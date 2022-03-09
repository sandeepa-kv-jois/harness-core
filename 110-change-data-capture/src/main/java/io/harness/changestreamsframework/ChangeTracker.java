/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreamsframework;

import io.harness.ChangeDataCaptureServiceConfig;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.persistence.PersistentEntity;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.mongodb.ClientSessionOptions;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeTracker {
  @Inject private ChangeDataCaptureServiceConfig mainConfiguration;
  @Inject private ChangeEventFactory changeEventFactory;
  @Inject private MongoConfig mongoConfig;
  private ExecutorService executorService;
  private Set<ChangeTrackingTask> changeTrackingTasks;
  private Set<Future<?>> changeTrackingTasksFuture;
  private MongoClient mongoClient;
  private ReadPreference readPreference;
  private ClientSession clientSession;

  public String getCollectionName(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotation(Entity.class).value();
  }

  public String getChangeDataCaptureDataStore(Class<? extends PersistentEntity> clazz) {
    return clazz.getAnnotationsByType(ChangeDataCapture.class)[0].dataStore();
  }

  private MongoClientURI mongoClientUri(String dataStore) {
    String mongoClientUrl;
    switch (dataStore) {
      case "events":
        mongoClientUrl = mainConfiguration.getEventsMongo().getUri();
        break;
      case "pms-harness":
        mongoClientUrl = mainConfiguration.getPmsMongo().getUri();
        break;
      case "ng-harness":
        mongoClientUrl = mainConfiguration.getNgMongo().getUri();
        break;
      default:
        mongoClientUrl = mainConfiguration.getHarnessMongo().getUri();
        break;
    }

    TagSet mongoTagSet = getMongoTagSet();
    if (mongoTagSet == null) {
      readPreference = ReadPreference.secondaryPreferred();
    } else {
      readPreference = ReadPreference.secondary(mongoTagSet);
    }
    return new MongoClientURI(mongoClientUrl,
        MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig))
            .serverSelectionTimeout(mainConfiguration.getHarnessMongo().getServerSelectionTimeout())
            .readPreference(readPreference));
  }

  private TagSet getMongoTagSet() {
    if (!mainConfiguration.getMongoTagsConfig().getTagKey().equals("none")) {
      return new TagSet(new Tag(
          mainConfiguration.getMongoTagsConfig().getTagKey(), mainConfiguration.getMongoTagsConfig().getTagValue()));
    }
    return null;
  }

  public MongoDatabase connectToMongoDatabase(String dataStore) {
    MongoClientURI uri = mongoClientUri(dataStore);
    mongoClient = new MongoClient(uri);
    if (readPreference.getClass() == ReadPreference.secondaryPreferred().getClass()) {
      clientSession = null;
    } else {
      clientSession = mongoClient.startSession(ClientSessionOptions.builder().build());
    }
    final String databaseName = uri.getDatabase();
    log.info("Database is {}", databaseName);
    return mongoClient.getDatabase(databaseName)
        .withReadConcern(ReadConcern.MAJORITY)
        .withReadPreference(readPreference);
  }

  private void createChangeStreamTasks(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    changeTrackingTasks = new HashSet<>();
    for (ChangeTrackingInfo<?> changeTrackingInfo : changeTrackingInfos) {
      MongoDatabase mongoDatabase =
          connectToMongoDatabase(getChangeDataCaptureDataStore(changeTrackingInfo.getMorphiaClass()));

      MongoCollection<DBObject> collection =
          mongoDatabase.getCollection(getCollectionName(changeTrackingInfo.getMorphiaClass()))
              .withDocumentClass(DBObject.class)
              .withReadPreference(readPreference);

      log.info("Connection details for mongo collection {}", collection.getReadPreference());

      ChangeStreamSubscriber changeStreamSubscriber = getChangeStreamSubscriber(changeTrackingInfo);
      ChangeTrackingTask changeTrackingTask = new ChangeTrackingTask(
          changeStreamSubscriber, collection, clientSession, changeTrackingInfo.getResumeToken());
      changeTrackingTasks.add(changeTrackingTask);
    }
  }

  private void openChangeStreams(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    executorService =
        Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setNameFormat("change-tracker-%d").build());
    createChangeStreamTasks(changeTrackingInfos);
    changeTrackingTasksFuture = new HashSet<>();

    if (!executorService.isShutdown()) {
      for (ChangeTrackingTask changeTrackingTask : changeTrackingTasks) {
        Future f = executorService.submit(changeTrackingTask);
        changeTrackingTasksFuture.add(f);
      }
    }
  }

  private boolean shouldProcessChange(ChangeStreamDocument<DBObject> changeStreamDocument) {
    return changeStreamDocument.getFullDocument() != null
        || changeStreamDocument.getOperationType() == OperationType.DELETE;
  }

  private <T extends PersistentEntity> ChangeStreamSubscriber getChangeStreamSubscriber(
      ChangeTrackingInfo<T> changeTrackingInfo) {
    return changeStreamDocument -> {
      if (shouldProcessChange(changeStreamDocument)) {
        ChangeEvent<T> changeEvent =
            changeEventFactory.fromChangeStreamDocument(changeStreamDocument, changeTrackingInfo.getMorphiaClass());
        changeTrackingInfo.getChangeSubscriber().onChange(changeEvent);
      }
    };
  }

  public void start(Set<ChangeTrackingInfo<?>> changeTrackingInfos) {
    openChangeStreams(changeTrackingInfos);
  }

  public boolean checkIfAnyChangeTrackerIsAlive() {
    for (Future<?> f : changeTrackingTasksFuture) {
      if (!f.isDone()) {
        return true;
      }
    }
    return false;
  }

  public void stop() {
    log.info("Trying to close changeTrackingTasks");
    for (Future<?> f : changeTrackingTasksFuture) {
      f.cancel(true);
    }
    if (executorService != null) {
      executorService.shutdownNow();
    }
    if (mongoClient != null) {
      mongoClient.close();
    }
  }
}

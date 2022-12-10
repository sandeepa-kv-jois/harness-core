/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.common;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;

import lombok.Getter;
import lombok.Setter;
import org.mongodb.morphia.annotations.Entity;

@StoreIn(DbAliases.HARNESS)
@Entity(value = "!!!testMongo", noClassnameStored = true)
public class MongoEntity extends Base {
  @Getter @Setter private String data;
}

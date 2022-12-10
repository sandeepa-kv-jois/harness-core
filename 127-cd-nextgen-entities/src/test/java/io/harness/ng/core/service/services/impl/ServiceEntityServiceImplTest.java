/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.impl.EntitySetupUsageServiceImpl;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.yaml.YamlNode;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@RunWith(Parameterized.class)
public class ServiceEntityServiceImplTest extends CDNGEntitiesTestBase {
  @Mock private OutboxService outboxService;
  @Mock private EntitySetupUsageServiceImpl entitySetupUsageService;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock private ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  @Inject @InjectMocks private ServiceEntityServiceImpl serviceEntityService;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String SERVICE_ID = "serviceId";

  private String pipelineInputYamlPath;
  private String actualEntityYamlPath;
  private String mergedInputYamlPath;
  private boolean isMergedYamlEmpty;

  public ServiceEntityServiceImplTest(String pipelineInputYamlPath, String actualEntityYamlPath,
      String mergedInputYamlPath, boolean isMergedYamlEmpty) {
    this.pipelineInputYamlPath = pipelineInputYamlPath;
    this.actualEntityYamlPath = actualEntityYamlPath;
    this.mergedInputYamlPath = mergedInputYamlPath;
    this.isMergedYamlEmpty = isMergedYamlEmpty;
  }

  @Before
  public void setup() {
    entitySetupUsageService = mock(EntitySetupUsageServiceImpl.class);
    Reflect.on(serviceEntityService).set("entitySetupUsageService", entitySetupUsageService);
    Reflect.on(serviceEntityService).set("outboxService", outboxService);
    Reflect.on(serviceEntityService).set("serviceOverrideService", serviceOverrideService);
    Reflect.on(serviceEntityService).set("entitySetupUsageHelper", entitySetupUsageHelper);
  }
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {"service/serviceInputs-with-few-values-fixed.yaml", "service/service-with-primaryArtifactRef-runtime.yaml",
            "service/serviceInputs-merged.yaml", false},
        {"service/serviceInputs-with-few-values-fixed.yaml", "service/service-with-no-runtime-input.yaml",
            "infrastructure/empty-file.yaml", true},
        {"infrastructure/empty-file.yaml", "service/service-with-primaryArtifactRef-fixed.yaml",
            "service/merged-service-input-fixed-prime-artifact.yaml", false}});
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> serviceEntityService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier("IDENTIFIER")
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("Service")
                                      .build();

    // Create operations
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    assertThat(createdService).isNotNull();
    assertThat(createdService.getAccountId()).isEqualTo(serviceEntity.getAccountId());
    assertThat(createdService.getOrgIdentifier()).isEqualTo(serviceEntity.getOrgIdentifier());
    assertThat(createdService.getProjectIdentifier()).isEqualTo(serviceEntity.getProjectIdentifier());
    assertThat(createdService.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(createdService.getName()).isEqualTo(serviceEntity.getName());
    assertThat(createdService.getVersion()).isEqualTo(0L);

    // Get operations
    Optional<ServiceEntity> getService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", false);
    assertThat(getService).isPresent();
    assertThat(getService.get()).isEqualTo(createdService);

    // Update operations
    ServiceEntity updateServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("IDENTIFIER")
                                             .orgIdentifier("ORG_ID")
                                             .projectIdentifier("PROJECT_ID")
                                             .name("UPDATED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .build();
    ServiceEntity updatedServiceResponse = serviceEntityService.update(updateServiceRequest);
    assertThat(updatedServiceResponse.getAccountId()).isEqualTo(updateServiceRequest.getAccountId());
    assertThat(updatedServiceResponse.getOrgIdentifier()).isEqualTo(updateServiceRequest.getOrgIdentifier());
    assertThat(updatedServiceResponse.getProjectIdentifier()).isEqualTo(updateServiceRequest.getProjectIdentifier());
    assertThat(updatedServiceResponse.getIdentifier()).isEqualTo(updateServiceRequest.getIdentifier());
    assertThat(updatedServiceResponse.getName()).isEqualTo(updateServiceRequest.getName());
    assertThat(updatedServiceResponse.getDescription()).isEqualTo(updateServiceRequest.getDescription());
    assertThat(updatedServiceResponse.getVersion()).isEqualTo(1L);

    updateServiceRequest.setAccountId("NEW_ACCOUNT");
    assertThatThrownBy(() -> serviceEntityService.update(updateServiceRequest))
        .isInstanceOf(InvalidRequestException.class);
    updatedServiceResponse.setAccountId("ACCOUNT_ID");

    // Upsert operations
    ServiceEntity upsertServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("NEW_IDENTIFIER")
                                             .orgIdentifier("ORG_ID")
                                             .projectIdentifier("NEW_PROJECT")
                                             .name("UPSERTED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .build();
    ServiceEntity upsertService = serviceEntityService.upsert(upsertServiceRequest, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isEqualTo(upsertServiceRequest.getProjectIdentifier());
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());

    // List services operations.
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    assertThat(ServiceElementMapper.writeDTO(list.getContent().get(0)))
        .isEqualTo(ServiceElementMapper.writeDTO(updatedServiceResponse));

    criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);

    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(0);

    // Upsert operations for org level
    ServiceEntity upsertServiceRequestOrgLevel = ServiceEntity.builder()
                                                     .accountId("ACCOUNT_ID")
                                                     .identifier("NEW_IDENTIFIER")
                                                     .orgIdentifier("ORG_ID")
                                                     .name("UPSERTED_SERVICE")
                                                     .description("NEW_DESCRIPTION")
                                                     .build();
    upsertService = serviceEntityService.upsert(upsertServiceRequestOrgLevel, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isNull();
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());

    criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);

    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    List<ServiceResponseDTO> dtoList =
        list.getContent().stream().map(ServiceElementMapper::writeDTO).collect(Collectors.toList());
    assertThat(dtoList).containsOnly(ServiceElementMapper.writeDTO(upsertService));

    // Delete operations
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    boolean delete = serviceEntityService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", 1L);
    assertThat(delete).isTrue();
    verify(serviceOverrideService).deleteAllInProjectForAService("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER");

    Optional<ServiceEntity> deletedService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "UPDATED_SERVICE", false);
    assertThat(deletedService.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testBulkCreate() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);
    for (int i = 0; i < 5; i++) {
      String serviceIdentifier = "identifier " + i;
      Optional<ServiceEntity> serviceEntitySaved =
          serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, serviceIdentifier, false);
      assertThat(serviceEntitySaved.isPresent()).isTrue();
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetAllServices() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int pageSize = 1000;
    int numOfServices = pageSize * 2 + 100; // creating adhoc num of services, not in multiples of page size
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, pageSize, true, new ArrayList<>());
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetAllNonDeletedServices() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int numOfServices = 20;
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setDeleted(i % 2 == 0); // Every alternate service is deleted
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllNonDeletedServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, new ArrayList<>());
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices / 2);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetAllNonDeletedServicesWithSort() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int numOfServices = 20;
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier" + i;
      String serviceName = String.valueOf((char) ('A' + i));
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setDeleted(i % 2 == 0); // Every alternate service is deleted
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllNonDeletedServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList("name,DESC"));
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices / 2);
    assertThat(serviceEntityList.get(0).getName())
        .isGreaterThan(serviceEntityList.get(serviceEntityList.size() - 1).getName());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetActiveServiceCount() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setCreatedAt((long) i);
      if (i % 5 == 0) {
        serviceEntity.setDeleted(true);
        serviceEntity.setDeletedAt((long) (i + 5));
      }
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);
    Integer activeServiceCount =
        serviceEntityService.findActiveServicesCountAtGivenTimestamp(ACCOUNT_ID, ORG_ID, PROJECT_ID, 16);
    assertThat(activeServiceCount).isEqualTo(16 - 2);
  }

  private ServiceEntity createServiceEntity(String identifier, String name) {
    return ServiceEntity.builder()
        .accountId(ACCOUNT_ID)
        .identifier(identifier)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .name(name)
        .deleted(false)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGetDuplicateServiceExistsErrorMessage() {
    String errorMessage = "Bulk write operation error on server localhost:27017. "
        + "Write errors: [BulkWriteError{index=0, code=11000, "
        + "message='E11000 duplicate key error collection: ng-harness.servicesNG "
        + "index: unique_accountId_organizationIdentifier_projectIdentifier_serviceIdentifier "
        + "dup key: { accountId: \"kmpySmUISimoRrJL6NL73w\", orgIdentifier: \"default\", "
        + "projectIdentifier: \"Nofar\", identifier: \"service_5\" }'";
    String errorMessageToBeShownToUser =
        serviceEntityService.getDuplicateServiceExistsErrorMessage("kmpySmUISimoRrJL6NL73w", errorMessage);
    assertThat(errorMessageToBeShownToUser)
        .isEqualTo(
            "Service [service_5] under Project[Nofar], Organization [default] in Account [kmpySmUISimoRrJL6NL73w] already exists");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testErrorMessageWhenServiceIsReferenced() {
    List<EntitySetupUsageDTO> referencedByEntities = Arrays.asList(getEntitySetupUsageDTO());
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(referencedByEntities));
    assertThatThrownBy(() -> serviceEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "SERVICE", 0L))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage(
            "The service SERVICE cannot be deleted because it is being referenced in 1 entity. To delete your service, please remove the reference service from these entities.");

    referencedByEntities = Arrays.asList(getEntitySetupUsageDTO(), getEntitySetupUsageDTO());
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(referencedByEntities));
    assertThatThrownBy(() -> serviceEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "SERVICE", 0L))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage(
            "The service SERVICE cannot be deleted because it is being referenced in 2 entities. To delete your service, please remove the reference service from these entities.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testDeleteAllServicesInProject() {
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_2")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    // Create operations
    serviceEntityService.create(serviceEntity1);
    serviceEntityService.create(serviceEntity2);

    boolean delete = serviceEntityService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    assertThat(delete).isTrue();

    // List services operations.
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testHardDeleteService() {
    final String id = UUIDGenerator.generateUuid();
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(id)
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("Service")
                                      .build();

    serviceEntityService.create(serviceEntity);
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    boolean delete = serviceEntityService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L);
    assertThat(delete).isTrue();

    // list both deleted true/false services
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithNoRuntimeInputs() {
    String filename = "service/service-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNullOrEmpty();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithRuntimeInputs() {
    String filename = "service/service-with-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/service-with-runtime-inputs-res.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefFixed() {
    String filename = "service/service-with-primaryArtifactRef-fixed.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-fixed.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefRuntime() {
    String filename = "service/service-with-primaryArtifactRef-runtime.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-runtime.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefExpression() {
    String filename = "service/service-with-primaryArtifactRef-expression.yaml";
    String yaml = readFile(filename);
    assertThatThrownBy(() -> serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Primary artifact ref cannot be an expression inside the service %s", SERVICE_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV2() {
    String filename = "service/service-with-primaryArtifactRef-runtime.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(responseDTO.getSourceIdentifiers()).hasSameElementsAs(Arrays.asList("i1", "i2"));
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).isNotNull().isNotEmpty().hasSize(2);
    String runForm1 = "identifier: \"i1\"\n"
        + "type: \"DockerRegistry\"\n"
        + "spec:\n"
        + "  tag: \"<+input>\"\n";
    String runForm2 = "identifier: \"i2\"\n"
        + "type: \"DockerRegistry\"\n"
        + "spec:\n"
        + "  tag: \"<+input>\"\n";
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i1", runForm1);
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i2", runForm2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV1() {
    String filename = "service/serviceWith3ConnectorReferences.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNull();
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV2AndSourcesHasNoRuntimeInput() {
    String filename = "service/service-with-no-runtime-input-in-sources.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(responseDTO.getSourceIdentifiers()).hasSameElementsAs(Arrays.asList("i1", "i2"));
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i1", null);
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i2", null);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpsertWithoutOutbox() {
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(UUIDGenerator.generateUuid())
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .build();

    ServiceEntity created = serviceEntityService.create(createRequest);

    ServiceEntity upsertRequest = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(created.getIdentifier())
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("UPSERTED_ENV")
                                      .description("NEW_DESCRIPTION")
                                      .build();

    ServiceEntity upserted = serviceEntityService.upsert(upsertRequest, UpsertOptions.DEFAULT.withNoOutbox());

    assertThat(upserted).isNotNull();

    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetYamlNodeForFqn() {
    String yaml = readFile("ArtifactResourceUtils/serviceWithPrimaryAndSidecars.yaml");
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("testGetYamlNodeForFqn")
                                      .identifier("testGetYamlNodeForFqn")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    YamlNode primaryNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag");

    YamlNode sidecarNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sc1.spec.tag");

    assertThat(primaryNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(primaryNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist\",\"tag\":\"<+input>\"}");

    assertThat(sidecarNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(sidecarNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist-sample\",\"region\":\"us-east-1\",\"tag\":\"<+input>\"}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlNodeForFqnWithPrimarySources() {
    String yaml = readFile("ArtifactResourceUtils/serviceWithPrimarySourcesAndSidecars.yaml");
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("testGetYamlNodeForFqn")
                                      .identifier("testGetYamlNodeForFqn")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    YamlNode primaryNode = serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        "testGetYamlNodeForFqn",
        "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.sources.i1.spec.tag");

    YamlNode sidecarNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sc1.spec.tag");

    assertThat(primaryNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(primaryNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage1\",\"imagePath\":\"harness/todolist\",\"tag\":\"<+input>\"}");

    assertThat(sidecarNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(sidecarNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist-sample\",\"region\":\"us-east-1\",\"tag\":\"<+input>\"}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testMergeServiceInputs() {
    String yaml = readFile(actualEntityYamlPath);
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("serviceWithPrimaryArtifactRefRuntime")
                                      .identifier("serviceWithPrimaryArtifactRefRuntime")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    String oldTemplateInputYaml = readFile(pipelineInputYamlPath);
    String mergedTemplateInputsYaml = readFile(mergedInputYamlPath);
    ServiceInputsMergedResponseDto responseDto = serviceEntityService.mergeServiceInputs(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "serviceWithPrimaryArtifactRefRuntime", oldTemplateInputYaml);
    String mergedYaml = responseDto.getMergedServiceInputsYaml();
    if (isMergedYamlEmpty) {
      assertThat(mergedYaml).isNull();
    } else {
      assertThat(mergedYaml).isNotNull().isNotEmpty();
      assertThat(mergedYaml).isEqualTo(mergedTemplateInputsYaml);
    }
    assertThat(responseDto.getServiceYaml()).isNotNull().isNotEmpty().isEqualTo(yaml);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private EntitySetupUsageDTO getEntitySetupUsageDTO() {
    return EntitySetupUsageDTO.builder().referredByEntity(EntityDetail.builder().build()).build();
  }
}

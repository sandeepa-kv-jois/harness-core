package io.harness.resourcegroup.framework.v2.remote.mapper;

import static io.harness.rule.OwnerRule.REETIKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.v1.model.ResourceGroup;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class ResourceGroupMapperTest extends ResourceGroupTestBase {
  private ObjectMapper objectMapper;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  public static String readFileAsString(String file) {
    try {
      return new String(Files.readAllBytes(Paths.get(file)));
    } catch (Exception ex) {
      Assert.fail("Failed reading the json from " + file + " with error " + ex.getMessage());
      return "";
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testManagedRGV1ToV2() {
    String accountLevelResourceGroup = readFileAsString(
        "830-resource-group/src/test/resources/resourcegroups/v1/AllResourcesIncludingChildScopes.json");
    ResourceGroup resourceGroupV1 = null;
    try {
      ResourceGroupDTO resourceGroupV1DTO = objectMapper.readValue(accountLevelResourceGroup, ResourceGroupDTO.class);
      resourceGroupV1 =
          io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
      resourceGroupV1.setHarnessManaged(true);

    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      ResourceGroupMapper.fromV1(resourceGroupV1);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testCustomRGV1ToV2() {
    String customResourceGroupV1 =
        readFileAsString("830-resource-group/src/main/resources/io/harness/resourcegroup/v1/customResourceGroup.json");
    String customResourceGroupV2 =
        readFileAsString("830-resource-group/src/main/resources/io/harness/resourcegroup/v2/customResourceGroup.json");
    ResourceGroup resourceGroupV1 = null;
    io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2DTO = null;
    try {
      ResourceGroupDTO resourceGroupV1DTO = objectMapper.readValue(customResourceGroupV1, ResourceGroupDTO.class);
      resourceGroupV2DTO =
          objectMapper.readValue(customResourceGroupV2, io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO.class);
      resourceGroupV1 =
          io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO convertedResourceGroupDTO =
          ResourceGroupMapper.toDTO(ResourceGroupMapper.fromV1(resourceGroupV1));
      assertThat(convertedResourceGroupDTO).isEqualTo(resourceGroupV2DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }
}

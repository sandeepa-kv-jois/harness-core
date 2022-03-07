package io.harness.jackson.v2;

import static io.harness.rule.OwnerRule.REETIKA;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ResourceGroupDTODeserializerTest extends CategoryTest {
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
  public void testDeserializationOfAccountResourceGroup() {
    String accountLevelResourceGroup =
        readFileAsString("940-resource-group-beans/src/test/resources/resourcegroups/accountResourceGroupV2.json");

    try {
      objectMapper.readValue(accountLevelResourceGroup, ResourceGroupDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
  }
}

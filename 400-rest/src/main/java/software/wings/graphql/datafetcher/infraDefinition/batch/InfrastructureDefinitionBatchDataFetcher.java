package software.wings.graphql.datafetcher.infraDefinition.batch;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import software.wings.graphql.datafetcher.AbstractBatchDataFetcher;
import software.wings.graphql.schema.query.QLInfrastructureDefinitionQueryParameters;
import software.wings.graphql.schema.type.QLInfrastructureDefinition;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import java.util.concurrent.CompletionStage;
import javax.validation.constraints.NotNull;
import org.dataloader.DataLoader;

public class InfrastructureDefinitionBatchDataFetcher
    extends AbstractBatchDataFetcher<QLInfrastructureDefinition, QLInfrastructureDefinitionQueryParameters, String> {
  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  public CompletionStage<QLInfrastructureDefinition> load(QLInfrastructureDefinitionQueryParameters qlQuery,
      @NotNull DataLoader<String, QLInfrastructureDefinition> dataLoader) {
    return dataLoader.load(emptyIfNull(qlQuery.getInfrastructureId()));
  }
}

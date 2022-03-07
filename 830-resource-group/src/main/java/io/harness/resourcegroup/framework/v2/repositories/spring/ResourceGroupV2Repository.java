package io.harness.resourcegroup.framework.v2.repositories.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.framework.v2.repositories.custom.ResourceGroupV2RepositoryCustom;
import io.harness.resourcegroup.v2.model.ResourceGroup;

import java.util.List;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PL)
public interface ResourceGroupV2Repository
    extends PagingAndSortingRepository<ResourceGroup, String>, ResourceGroupV2RepositoryCustom {
  List<ResourceGroup> deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);
}

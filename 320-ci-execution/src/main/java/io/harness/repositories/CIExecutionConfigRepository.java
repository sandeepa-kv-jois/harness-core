package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.CIExecutionConfig;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface CIExecutionConfigRepository extends CrudRepository<CIExecutionConfig, String> {
  Optional<CIExecutionConfig> findFirstByAccountIdentifier(String accountIdentifier);
}
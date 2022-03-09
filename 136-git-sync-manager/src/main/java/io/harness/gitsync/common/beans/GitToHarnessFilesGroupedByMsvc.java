package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.ChangeSet;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PL)
public class GitToHarnessFilesGroupedByMsvc {
  Microservice microservice;
  List<ChangeSet> changeSetList;
}

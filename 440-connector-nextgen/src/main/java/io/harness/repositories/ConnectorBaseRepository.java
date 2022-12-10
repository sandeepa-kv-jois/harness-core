/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.entities.Connector;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@HarnessRepo
@GitSyncableHarnessRepo
@NoRepositoryBean
@OwnedBy(DX)
public interface ConnectorBaseRepository extends Repository<Connector, ConnectorDTO> {}

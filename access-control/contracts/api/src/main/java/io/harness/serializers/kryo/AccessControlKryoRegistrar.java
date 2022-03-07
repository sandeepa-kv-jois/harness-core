/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializers.kryo;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.api.AccessCheckRequestDTO;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.aggregator.api.AggregatorSecondarySyncStateDTO;
import io.harness.accesscontrol.commons.ValidationResultDTO;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.accesscontrol.permissions.PermissionStatus;
import io.harness.accesscontrol.permissions.api.PermissionDTO;
import io.harness.accesscontrol.permissions.api.PermissionResponseDTO;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentRequest;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentValidationRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentValidationResponseDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PL)
public class AccessControlKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(AccessDeniedErrorDTO.class, 70001);
    kryo.register(ValidationResultDTO.class, 70005);

    kryo.register(AccessCheckRequestDTO.class, 70006);
    kryo.register(AccessCheckResponseDTO.class, 70007);
    kryo.register(AccessControlDTO.class, 70008);
    kryo.register(PermissionCheckDTO.class, 70002);
    kryo.register(Principal.class, 70009);
    kryo.register(ResourceScope.class, 70003);
    kryo.register(Resource.class, 70010);

    kryo.register(AggregatorSecondarySyncStateDTO.class, 70011);

    kryo.register(PermissionDTO.class, 70012);
    kryo.register(PermissionResponseDTO.class, 70013);
    kryo.register(PermissionStatus.class, 70014);

    kryo.register(PrincipalDTO.class, 70015);
    kryo.register(PrincipalType.class, 70016);

    kryo.register(ResourceGroupDTO.class, 70017);
    kryo.register(RoleAssignmentAggregateResponseDTO.class, 70018);
    kryo.register(RoleAssignmentCreateRequestDTO.class, 70019);
    kryo.register(RoleAssignmentDTO.class, 70020);
    kryo.register(RoleAssignmentFilterDTO.class, 70021);
    kryo.register(RoleAssignmentRequest.class, 70022);
    kryo.register(RoleAssignmentResponseDTO.class, 70023);
    kryo.register(RoleAssignmentValidationRequestDTO.class, 70024);
    kryo.register(RoleAssignmentValidationResponseDTO.class, 70025);

    kryo.register(RoleDTO.class, 70026);
    kryo.register(RoleResponseDTO.class, 70027);

    kryo.register(ScopeDTO.class, 70028);
    kryo.register(HarnessScopeParams.class, 70029);
  }
}

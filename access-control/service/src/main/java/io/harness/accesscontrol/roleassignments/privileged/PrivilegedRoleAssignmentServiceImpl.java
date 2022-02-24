/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.privileged;

import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.accesscontrol.acl.PermissionCheckResult;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDao;
import io.harness.accesscontrol.roles.PrivilegedRole;
import io.harness.accesscontrol.roles.PrivilegedRolesConfig;
import io.harness.accesscontrol.support.SupportPreference;
import io.harness.accesscontrol.support.SupportService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class PrivilegedRoleAssignmentServiceImpl implements PrivilegedRoleAssignmentService {
  private final PrivilegedRoleAssignmentDao dao;
  private final Set<PrivilegedRole> privilegedRoles;
  private final SupportService supportService;
  private final String PRIVILEGED_ROLES_CONFIG_PATH = "io/harness/accesscontrol/roles/privileged-roles.yml";

  @Inject
  public PrivilegedRoleAssignmentServiceImpl(PrivilegedRoleAssignmentDao dao, SupportService supportService) {
    this.dao = dao;
    this.supportService = supportService;
    this.privilegedRoles = getPrivilegedRoles();
  }

  private Set<PrivilegedRole> getPrivilegedRoles() {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(PRIVILEGED_ROLES_CONFIG_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      return om.readValue(bytes, PrivilegedRolesConfig.class).getRoles();
    } catch (IOException e) {
      throw new InvalidRequestException("Super Roles file path or format is invalid");
    }
  }

  @Override
  public PrivilegedAccessResult checkAccess(PrivilegedAccessCheck privilegedAccessCheck) {
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    SupportPreference supportPreference =
        supportService.fetchSupportPreference(privilegedAccessCheck.getAccountIdentifier());

    Set<String> allAllowedPermissions = new HashSet<>();
    if (supportPreference.isSupportEnabled()) {
      allAllowedPermissions = getAllAllowedPermissions(privilegedAccessCheck.getPrincipal());
    }

    Set<String> finalAllAllowedPermissions = allAllowedPermissions;
    return PrivilegedAccessResult.builder()
        .accountIdentifier(privilegedAccessCheck.getAccountIdentifier())
        .principal(privilegedAccessCheck.getPrincipal())
        .permissionCheckResults(permissionChecks.stream()
                                    .map(permissionCheck -> checkAccess(permissionCheck, finalAllAllowedPermissions))
                                    .collect(Collectors.toList()))
        .build();
  }

  private PermissionCheckResult checkAccess(PermissionCheck permissionCheck, Set<String> allAllowedPermissions) {
    return PermissionCheckResult.builder()
        .resourceScope(permissionCheck.getResourceScope())
        .resourceType(permissionCheck.getResourceType())
        .resourceIdentifier(permissionCheck.getResourceIdentifier())
        .permission(permissionCheck.getPermission())
        .permitted(allAllowedPermissions.contains(permissionCheck.getPermission()))
        .build();
  }

  private Set<String> getAllAllowedPermissions(Principal principal) {
    List<PrivilegedRoleAssignment> privilegedRoleAssignments = dao.getByPrincipal(principal);
    Set<String> privilegedRoleIdentifiers =
        privilegedRoleAssignments.stream().map(PrivilegedRoleAssignment::getRoleIdentifier).collect(Collectors.toSet());
    return privilegedRoles.stream()
        .filter(privilegedRole -> privilegedRoleIdentifiers.contains(privilegedRole.getIdentifier()))
        .map(PrivilegedRole::getPermissions)
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  @Override
  public void syncRoleAssignments(Set<Principal> updatedPrincipals, String roleIdentifier) {
    List<PrivilegedRoleAssignment> privilegedRoleAssignments = dao.getByRole(roleIdentifier);
    Set<Principal> savedPrincipals = privilegedRoleAssignments.stream()
                                         .map(r
                                             -> Principal.builder()
                                                    .principalType(r.getPrincipalType())
                                                    .principalIdentifier(r.getPrincipalIdentifier())
                                                    .build())
                                         .collect(Collectors.toSet());

    Set<Principal> removedPrincipals = Sets.difference(savedPrincipals, updatedPrincipals);
    Set<Principal> addedPrincipals = Sets.difference(updatedPrincipals, savedPrincipals);

    if (!removedPrincipals.isEmpty()) {
      dao.removeByPrincipalsAndRole(removedPrincipals, roleIdentifier);
    }

    if (!addedPrincipals.isEmpty()) {
      List<PrivilegedRoleAssignment> newRoleAssignments =
          addedPrincipals.stream()
              .map(principal
                  -> PrivilegedRoleAssignment.builder()
                         .principalIdentifier(principal.getPrincipalIdentifier())
                         .principalType(principal.getPrincipalType())
                         .roleIdentifier(roleIdentifier)
                         .build())
              .collect(Collectors.toList());
      dao.insertAllIgnoringDuplicates(newRoleAssignments);
    }
  }

  @Override
  public void deleteByRoleAssignment(String id) {
    dao.deleteByRoleAssignment(id);
  }
}

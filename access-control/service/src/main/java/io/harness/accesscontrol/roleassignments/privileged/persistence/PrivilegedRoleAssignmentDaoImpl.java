/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.privileged.persistence;

import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignment;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDBO.PrivilegedRoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.privileged.persistence.repositories.PrivilegedRoleAssignmentRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class PrivilegedRoleAssignmentDaoImpl implements PrivilegedRoleAssignmentDao {
  private final PrivilegedRoleAssignmentRepository repository;

  @Inject
  public PrivilegedRoleAssignmentDaoImpl(PrivilegedRoleAssignmentRepository repository) {
    this.repository = repository;
  }

  @Override
  public long insertAllIgnoringDuplicates(List<PrivilegedRoleAssignment> privilegedRoleAssignments) {
    List<PrivilegedRoleAssignmentDBO> assignments =
        privilegedRoleAssignments.stream().map(PrivilegedRoleAssignmentDBOMapper::toDBO).collect(Collectors.toList());
    return repository.insertAllIgnoringDuplicates(assignments);
  }

  @Override
  public List<PrivilegedRoleAssignment> getByPrincipal(Principal principal) {
    Criteria userIdentifierCriteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.principalType)
                                          .is(principal.getPrincipalType())
                                          .and(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                                          .is(principal.getPrincipalIdentifier());
    // changed criteria to match query index created for this query(Type followed by Identifier (as in unique index))

    List<PrivilegedRoleAssignmentDBO> assignments = repository.get(userIdentifierCriteria);
    return assignments.stream().map(PrivilegedRoleAssignmentDBOMapper::fromDBO).collect(Collectors.toList());
  }

  @Override
  public List<PrivilegedRoleAssignment> getByRole(String roleIdentifier) {
    Criteria roleIdentifierCriteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.roleIdentifier).is(roleIdentifier);
    List<PrivilegedRoleAssignmentDBO> assignments = repository.get(roleIdentifierCriteria);
    return assignments.stream().map(PrivilegedRoleAssignmentDBOMapper::fromDBO).collect(Collectors.toList());
  }

  @Override
  public long removeByPrincipalsAndRole(Set<Principal> principals, String roleIdentifier) {
    Criteria criteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.roleIdentifier).is(roleIdentifier);

    criteria.orOperator(principals.stream()
                            .map(principal
                                -> Criteria.where(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                                       .is(principal.getPrincipalIdentifier())
                                       .and(PrivilegedRoleAssignmentDBOKeys.principalType)
                                       .is(principal.getPrincipalType()))
                            .toArray(Criteria[] ::new));
    return repository.remove(criteria);
  }

  @Override
  public long deleteByRoleAssignment(String id) {
    Criteria criteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.linkedRoleAssignment).is(id);
    return repository.remove(criteria);
  }
}

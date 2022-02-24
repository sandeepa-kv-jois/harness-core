/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.accesscontrol.roleassignments.privileged.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NAMANG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignment;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDBO.PrivilegedRoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.privileged.persistence.repositories.PrivilegedRoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class PrivilegedRoleAssignmentDaoImplTest extends AccessControlTestBase {
  private PrivilegedRoleAssignmentRepository repository;
  private PrivilegedRoleAssignmentDaoImpl privilegedRoleAssignmentDao;
  private Principal demoPrincipal;
  private static final String DEMO_ROLE_IDENTIFIER = randomAlphabetic(10);
  @Before
  public void setup() {
    repository = mock(PrivilegedRoleAssignmentRepository.class);
    privilegedRoleAssignmentDao = spy(new PrivilegedRoleAssignmentDaoImpl(repository));
    demoPrincipal =
        Principal.builder().principalType(PrincipalType.USER).principalIdentifier(randomAlphabetic(10)).build();
  }

  private List<PrivilegedRoleAssignment> buildPrivilegedRoleAssignments(int noOfPrivilegedRoleAssignments) {
    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = new ArrayList<>();
    for (int i = 0; i < noOfPrivilegedRoleAssignments; i++) {
      builtPrivilegedRoleAssignments.add(PrivilegedRoleAssignment.builder()
                                             .principalType(demoPrincipal.getPrincipalType())
                                             .principalIdentifier(demoPrincipal.getPrincipalIdentifier())
                                             .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                                             .build());
    }
    return builtPrivilegedRoleAssignments;
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testInsertAllIgnoringDuplicates() {
    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(5);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());
    when(repository.insertAllIgnoringDuplicates(assignments)).thenReturn((long) assignments.size());

    long returnVal = privilegedRoleAssignmentDao.insertAllIgnoringDuplicates(builtPrivilegedRoleAssignments);

    verify(privilegedRoleAssignmentDao, times(1)).insertAllIgnoringDuplicates(builtPrivilegedRoleAssignments);
    verify(repository, times(1)).insertAllIgnoringDuplicates(assignments);
    assertEquals(returnVal, assignments.size());
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testInsertAllIgnoringDuplicatesWhenInputEmpty() {
    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(0);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());
    when(repository.insertAllIgnoringDuplicates(assignments)).thenReturn((long) 0);

    long returnVal = privilegedRoleAssignmentDao.insertAllIgnoringDuplicates(builtPrivilegedRoleAssignments);

    verify(privilegedRoleAssignmentDao, times(1)).insertAllIgnoringDuplicates(builtPrivilegedRoleAssignments);
    verify(repository, times(1)).insertAllIgnoringDuplicates(assignments);
    assertEquals(returnVal, 0);
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetByPrincipal() {
    Criteria userIdentifierCriteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.principalType)
                                          .is(demoPrincipal.getPrincipalType())
                                          .and(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                                          .is(demoPrincipal.getPrincipalIdentifier());

    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(5);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());

    when(repository.get(userIdentifierCriteria)).thenReturn(assignments);

    List<PrivilegedRoleAssignment> returnVal = privilegedRoleAssignmentDao.getByPrincipal(demoPrincipal);

    verify(privilegedRoleAssignmentDao, times(1)).getByPrincipal(demoPrincipal);
    verify(repository, times(1)).get(userIdentifierCriteria);
    assertEquals(returnVal, builtPrivilegedRoleAssignments);
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetByPrincipalWhenNotFound() {
    Criteria userIdentifierCriteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.principalType)
                                          .is(demoPrincipal.getPrincipalType())
                                          .and(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                                          .is(demoPrincipal.getPrincipalIdentifier());

    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(0);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());

    when(repository.get(userIdentifierCriteria)).thenReturn(assignments);

    List<PrivilegedRoleAssignment> returnVal = privilegedRoleAssignmentDao.getByPrincipal(demoPrincipal);

    verify(privilegedRoleAssignmentDao, times(1)).getByPrincipal(demoPrincipal);
    verify(repository, times(1)).get(userIdentifierCriteria);
    assertEquals(returnVal, builtPrivilegedRoleAssignments);
    assertTrue(returnVal.isEmpty());
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetByRole() {
    Criteria roleIdentifierCriteria =
        Criteria.where(PrivilegedRoleAssignmentDBOKeys.roleIdentifier).is(DEMO_ROLE_IDENTIFIER);

    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(5);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());

    when(repository.get(roleIdentifierCriteria)).thenReturn(assignments);

    List<PrivilegedRoleAssignment> returnVal = privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(repository, times(1)).get(roleIdentifierCriteria);
    assertEquals(returnVal, builtPrivilegedRoleAssignments);
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetByRoleNotFound() {
    Criteria roleIdentifierCriteria =
        Criteria.where(PrivilegedRoleAssignmentDBOKeys.roleIdentifier).is(DEMO_ROLE_IDENTIFIER);

    List<PrivilegedRoleAssignment> builtPrivilegedRoleAssignments = buildPrivilegedRoleAssignments(0);
    List<PrivilegedRoleAssignmentDBO> assignments = builtPrivilegedRoleAssignments.stream()
                                                        .map(PrivilegedRoleAssignmentDBOMapper::toDBO)
                                                        .collect(Collectors.toList());

    when(repository.get(roleIdentifierCriteria)).thenReturn(assignments);

    List<PrivilegedRoleAssignment> returnVal = privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(repository, times(1)).get(roleIdentifierCriteria);
    assertEquals(returnVal, builtPrivilegedRoleAssignments);
    assertTrue(returnVal.isEmpty());
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testRemoveByPrincipalsAndRole() {
    Set<Principal> testPrincipalList = new HashSet<>();
    for (int i = 0; i < 6; i++) {
      testPrincipalList.add(Principal.builder().principalType(PrincipalType.USER).principalIdentifier("P" + i).build());
    }
    Criteria criteria = Criteria.where(PrivilegedRoleAssignmentDBOKeys.roleIdentifier).is(DEMO_ROLE_IDENTIFIER);

    criteria.orOperator(testPrincipalList.stream()
                            .map(principal
                                -> Criteria.where(PrivilegedRoleAssignmentDBOKeys.principalIdentifier)
                                       .is(principal.getPrincipalIdentifier())
                                       .and(PrivilegedRoleAssignmentDBOKeys.principalType)
                                       .is(principal.getPrincipalType()))
                            .toArray(Criteria[] ::new));
    long expectedReturnVal = ThreadLocalRandom.current().nextLong();
    when(repository.remove(criteria)).thenReturn(expectedReturnVal);

    long returnVal = privilegedRoleAssignmentDao.removeByPrincipalsAndRole(testPrincipalList, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).removeByPrincipalsAndRole(testPrincipalList, DEMO_ROLE_IDENTIFIER);
    verify(repository, times(1)).remove(criteria);
    assertEquals(expectedReturnVal, returnVal);
    verifyNoMoreInteractions(privilegedRoleAssignmentDao);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import lombok.Getter;

@OwnedBy(PL)
@Getter
public class LastAdminCheckFilter {
  private final String userIdentifier;
  private final String userGroupIdentifier;
  private final LastAdminCheckFilterType type;

  public LastAdminCheckFilter(String userIdentifier, String userGroupIdentifier) {
    this.userIdentifier = userIdentifier;
    this.userGroupIdentifier = userGroupIdentifier;
    if (isNotEmpty(userIdentifier)) {
      if (isNotEmpty(userGroupIdentifier)) {
        this.type = LastAdminCheckFilterType.USER_FROM_USER_GROUP_REMOVAL;
      } else {
        this.type = LastAdminCheckFilterType.USER_DELETION;
      }
    } else {
      if (isNotEmpty(userGroupIdentifier)) {
        this.type = LastAdminCheckFilterType.USER_GROUP_DELETION;
      } else {
        throw new InvalidArgumentsException(
            "At least one of userGroupIdentifier or userIdentifier should be prevent in the filter");
      }
    }
  }

  public enum LastAdminCheckFilterType { USER_DELETION, USER_GROUP_DELETION, USER_FROM_USER_GROUP_REMOVAL }
}

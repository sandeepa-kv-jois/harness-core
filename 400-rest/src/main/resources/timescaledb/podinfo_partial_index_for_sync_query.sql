-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

CREATE INDEX IF NOT EXISTS podinfo_acc_clus_srtime_sptime_pndex ON pod_info(accountid text_ops,clusterid text_ops,starttime timestamptz_ops DESC, stoptime timestamptz_ops DESC NULLS FIRST) where stoptime is null;

COMMIT;
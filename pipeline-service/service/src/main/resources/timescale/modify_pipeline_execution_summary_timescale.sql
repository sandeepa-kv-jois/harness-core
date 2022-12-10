-- Copyright 2021 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;

ALTER TABLE pipeline_execution_summary_cd ADD COLUMN IF NOT EXISTS planExecutionId TEXT;

COMMIT;

BEGIN;

ALTER TABLE service_infra_info ADD COLUMN IF NOT EXISTS artifact_image TEXT;

COMMIT;

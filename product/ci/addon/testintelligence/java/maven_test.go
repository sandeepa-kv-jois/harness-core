// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"testing"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestMaven_GetCmd(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)

	runner := NewMavenRunner(log.Sugar(), fs, cmdFactory)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}
	tz := "-Duser.timezone=US/Mountain"
	enUS := "-Duser.locale=en/US"
	agent := "-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini"

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		ignoreInstr          bool
		want                 string
		expectedErr          bool
		tests                []types.RunnableTest
	}{
		// PR run
		{
			name:                 "RunAllTests_TwoTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -am -DharnessArgLine=\"%s %s %s\" -DargLine=\"%s %s %s\" clean test", tz, enUS, agent, tz, enUS, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunAllTests_ZeroTests_NoUserParams_AgentAttached",
			args:                 "clean test",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -am -DharnessArgLine=%s -DargLine=%s clean test", agent, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -Dtest=pkg1.cls1,pkg2.cls2 -am -DharnessArgLine=\"%s %s %s\" -DargLine=\"%s %s %s\" clean test", tz, enUS, agent, tz, enUS, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_Duplicate_UserParams_AgentAttached",
			args:                 "clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -Dtest=pkg1.cls1,pkg2.cls2 -am -DharnessArgLine=\"%s %s %s\" -DargLine=\"%s %s %s\" clean test -B -2C", tz, enUS, agent, tz, enUS, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_OrCondition_AgentAttached",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -Dtest=pkg2.cls2 -am -DharnessArgLine=\"%s %s %s\" -DargLine=\"%s %s %s\" clean test -B -2C   || true", tz, enUS, agent, tz, enUS, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
		{
			name:                 "RunAllTests_TwoTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("mvn -am -DharnessArgLine=\"%s %s %s\" -DargLine=\"%s %s %s\" clean test", tz, enUS, agent, tz, enUS, agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		// Ignore instrumentation true: Manual run or RunOnlySelectedTests task input is false
		{
			name:                 "RunAllTests_ZeroTests_NoUserParams_AgentNotAttached",
			args:                 "clean test",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "mvn clean test",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentNotAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 fmt.Sprintf("mvn -Dtest=pkg1.cls1,pkg2.cls2 clean test %s %s", tz, enUS),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentNotAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_Duplicate_UserParams_AgentNotAttached",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 fmt.Sprintf("mvn -Dtest=pkg1.cls1,pkg2.cls2 clean test -B -2C %s %s", tz, enUS),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_OrCondition_AgentNotAttached",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 fmt.Sprintf("mvn -Dtest=pkg2.cls2 clean test -B -2C %s %s || true", tz, enUS),
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := runner.GetCmd(ctx, tc.tests, tc.args, "/test/tmp/config.ini", tc.ignoreInstr, !tc.runOnlySelectedTests)
			if tc.expectedErr == (err == nil) {
				t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
			}
			assert.Equal(t, tc.want, got)
		})
	}
}

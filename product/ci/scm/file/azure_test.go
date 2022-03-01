// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package file

import (
	"context"
	"os"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

var azureToken = os.Getenv("AZURE_TOKEN")

const (
	organization = "tphoney"
	project      = "test_project"
	repoID       = "fde2d21f-13b9-4864-a995-83329045289a"
)

func TestFindFileAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetFileRequest{
		Slug: repoID,
		Path: "README.md",
		Type: &pb.GetFileRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.BlobId, "0ca446aab9d09eac8625b53e3df8da661976c458", "there is a blob_id")
	assert.NotEqual(t, got.CommitId, "", "there is a commit_id")
}

func TestCreateUpdateDeleteFileAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	// get the latest commit first
	getLatestCommit := &pb.FindFilesInBranchRequest{
		Slug: repoID,
		Type: &pb.FindFilesInBranchRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	branch, err0 := FindFilesInBranch(context.Background(), getLatestCommit, log.Sugar())
	assert.Nil(t, err0, "no errors")
	// create
	in := &pb.FileModifyRequest{
		Slug:     repoID,
		Path:     "CRUD",
		Content:  "hello",
		CommitId: branch.GetFile()[0].CommitId,
		Branch:   "main",
		Message:  "create CRUD",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	create, err1 := CreateFile(context.Background(), in, log.Sugar())
	assert.Nil(t, err1, "no errors")
	assert.Equal(t, int32(201), create.Status, "status matches")
	assert.NotEqual(t, create.CommitId, "", "there is a commit_id")
	//	modify
	in2 := &pb.FileModifyRequest{
		Slug:     repoID,
		Path:     "CRUD",
		Content:  "hello2",
		CommitId: create.CommitId,
		Branch:   "main",
		Message:  "update CRUD",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	update, err2 := UpdateFile(context.Background(), in2, log.Sugar())
	assert.Nil(t, err2, "no errors")
	assert.Equal(t, int32(201), update.Status, "status matches")
	assert.NotEqual(t, update.CommitId, "", "there is a commit_id")
	// delete
	in3 := &pb.DeleteFileRequest{
		Slug:     repoID,
		Path:     "CRUD",
		Branch:   "main",
		CommitId: update.CommitId,
		Message:  "delete CRUD",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	del, err3 := DeleteFile(context.Background(), in3, log.Sugar())
	assert.Nil(t, err3, "no errors")
	assert.Equal(t, int32(201), del.Status, "status matches")
	assert.NotEqual(t, "", del.CommitId, "commit id is not ''")
}

func TestFindFilesInCommitAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.FindFilesInCommitRequest{
		Slug: repoID,
		Ref:  "b3de5618fe072ad1be4f7b830c67a5ca2d50d8f6",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindFilesInCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.File), 1, "more than 1 file")
}

func TestFindFilesInBranchAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.FindFilesInBranchRequest{
		Slug: repoID,
		Type: &pb.FindFilesInBranchRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindFilesInBranch(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.LessOrEqual(t, 2, len(got.File), "More than 2 files in branch")
}

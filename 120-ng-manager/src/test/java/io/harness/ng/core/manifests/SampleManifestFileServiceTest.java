/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class SampleManifestFileServiceTest extends CategoryTest {
  @Mock private FileStoreService fileStoreService;
  @InjectMocks private SampleManifestFileService sampleManifestFileService;
  private AutoCloseable mocks;
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    doAnswer(im -> im.getArgument(0)).when(fileStoreService).create(any(FileDTO.class), any(InputStream.class));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testCreateSampleFiles() {
    ArgumentCaptor<FileDTO> captor = ArgumentCaptor.forClass(FileDTO.class);
    ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);

    sampleManifestFileService.createDefaultFilesInFileStore("account_identifier");

    verify(fileStoreService, times(6)).create(captor.capture(), inputStreamCaptor.capture());

    List<FileDTO> fileDTOList = captor.getAllValues();

    assertThat(fileDTOList.stream().map(FileDTO::getIdentifier).collect(Collectors.toList()))
        .containsExactly(
            "sample_k8s_manifests", "templates", "values_yaml", "deployment_yaml", "namespace_yaml", "service_yaml");
  }
}

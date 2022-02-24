package io.harness.delegate.task.shell;

import io.harness.delegate.beans.FileBucket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigFileMetaData {
  private String fileId;
  private Long length;
  private String filename;
  private String destinationDirectoryPath;
  private FileBucket fileBucket;
  private boolean encrypted;
  private String activityId;
}

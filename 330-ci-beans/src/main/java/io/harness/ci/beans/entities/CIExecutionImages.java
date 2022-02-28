package io.harness.ci.beans.entities;

import com.google.inject.Singleton;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@Singleton
public class CIExecutionImages {
  @NotBlank String addonTag;
  @NotBlank String liteEngineTag;
  @NotBlank String gitCloneTag;
  @NotBlank String buildAndPushDockerRegistryTag;
  @NotBlank String buildAndPushECRTag;
  @NotBlank String buildAndPushGCRTag;
  @NotBlank String gcsUploadTag;
  @NotBlank String s3UploadTag;
  @NotBlank String artifactoryUploadTag;
  @NotBlank String cacheGCSTag;
  @NotBlank String cacheS3Tag;
  @NotBlank String securityTag;
}

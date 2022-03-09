/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filesystem;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.filesystem.FileIo.acquireLock;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.filesystem.FileIo.getHomeDir;
import static io.harness.filesystem.FileIo.releaseLock;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.filesystem.FileIo.writeFile;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.nio.file.Files.lines;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FileIoTest extends CategoryTest {
  private static String tempDirectory = System.getProperty("java.io.tmpdir");

  private static String getRandomTempDirectory() {
    return tempDirectory + "/" + UUID.randomUUID().toString();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void createDirectoryTest() throws IOException {
    final String directoryPath = getRandomTempDirectory();
    try {
      createDirectoryIfDoesNotExist(directoryPath);
      File testFile = new File(directoryPath);
      assertThat(testFile.exists()).isTrue();
      long lastModifiedTime = testFile.lastModified();

      createDirectoryIfDoesNotExist(directoryPath);
      File testFile1 = new File(directoryPath);
      assertThat(testFile1.lastModified()).isEqualTo(lastModifiedTime);
    } finally {
      deleteDirectoryAndItsContentIfExists(directoryPath);
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void waitForDirectoryToBeAccessibleOutOfProcessPositiveTest() throws IOException {
    final String directoryPath = getRandomTempDirectory();
    try {
      createDirectoryIfDoesNotExist(directoryPath);
      assertThat(waitForDirectoryToBeAccessibleOutOfProcess(directoryPath, 3)).isTrue();

    } finally {
      deleteDirectoryAndItsContentIfExists(directoryPath);
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void waitForDirectoryToBeAccessibleOutOfProcessNegativeTest() {
    assertThat(waitForDirectoryToBeAccessibleOutOfProcess(getRandomTempDirectory(), 3)).isFalse();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void deleteFileIfExistsTest() throws IOException {
    final String fileName = tempDirectory + "/testfile.txt";
    deleteFileIfExists(fileName);
    File testFile = new File(fileName);
    assertThat(testFile.exists()).isFalse();
    try (FileOutputStream outputStream = new FileOutputStream(testFile)) {
      outputStream.write("RandomTextContent".getBytes(StandardCharsets.UTF_8));
    }
    assertThat(testFile.exists()).isTrue();
    deleteFileIfExists(fileName);
    assertThat(testFile.exists()).isFalse();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void deleteDirectoryAndItsContentTest() throws IOException {
    final String directoryName = getRandomTempDirectory();
    final String fileName = "/testfile.txt";
    createDirectoryIfDoesNotExist(directoryName);
    File directory = new File(directoryName);
    File testFile = new File(directory, fileName);
    try (FileOutputStream outputStream = new FileOutputStream(testFile)) {
      outputStream.write("RandomTextContent".getBytes(StandardCharsets.UTF_8));
    }
    assertThat(testFile.exists()).isTrue();
    deleteDirectoryAndItsContentIfExists(directoryName);
    assertThat(testFile.exists()).isFalse();
    assertThat(directory.exists()).isFalse();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void writeFileTest() throws IOException {
    final String directoryName = getRandomTempDirectory();
    final String fileName = "/testfile.txt";
    final String text = "randomText";
    createDirectoryIfDoesNotExist(directoryName);
    File directory = new File(directoryName);
    File testFile = new File(directory, fileName);
    writeFile(testFile.getAbsolutePath(), text.getBytes(StandardCharsets.UTF_8));
    assertThat(testFile.exists()).isTrue();

    try (Stream<String> stream = lines(Paths.get(testFile.getAbsolutePath()))) {
      String readOutput = stream.findFirst().get();
      assertThat(readOutput).isEqualTo(text);
    }

    deleteDirectoryAndItsContentIfExists(directoryName);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetHomeDir() {
    String homeDir = getHomeDir();
    assertThat(isEmpty(homeDir)).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void fileLockConcurrently() {
    File file = new File(getRandomTempDirectory() + "file");

    ReentrantLock re = new ReentrantLock();

    AtomicBoolean failed = new AtomicBoolean(false);

    Concurrent.test(100, n -> {
      if (acquireLock(file, ofMinutes(1))) {
        if (re.tryLock()) {
          re.unlock();
        } else {
          failed.set(true);
        }
        releaseLock(file);
      } else {
        failed.set(true);
      }
    });
    assertThat(failed.get()).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFilesUnderPathMatchesFirstLine() throws IOException {
    String randomPath = Files.createTempDirectory("testGetFilesUnderPathMatchesFirstLine").toString();
    try {
      String file1 = "\n\ntest-file-1";
      String file2 = "\nsecretcode\ntest-file-2";
      String file3 = "secretcode\ntest-file-2";
      File directory = new File(randomPath);
      writeFile(new File(directory, "file1").getAbsolutePath(), file1.getBytes(StandardCharsets.UTF_8));
      writeFile(new File(directory, "file2").getAbsolutePath(), file2.getBytes(StandardCharsets.UTF_8));
      writeFile(new File(directory, "file3").getAbsolutePath(), file3.getBytes(StandardCharsets.UTF_8));

      assertThat(FileIo.getFilesUnderPathMatchesFirstLine(randomPath, line -> line.contains("secretcode")).stream())
          .containsExactlyInAnyOrder(Paths.get("file2"), Paths.get("file3"));
    } finally {
      deleteDirectoryAndItsContentIfExists(randomPath);
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.executor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.executor.support.LocalRepositoryExecutorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalRepositoryExecutorToolTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesDefaultArtifactAndMetadataPaths() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("project");
        ExecutorRequest.Builder request = ExecutorRequest.mavenBuilder()
                .userHomeDirectory(home)
                .cwd(cwd);
        LocalRepositoryExecutorTool tool = new LocalRepositoryExecutorTool(noopExecutor());

        assertEquals(
                home.resolve(".m2/repository/org/example/library/1.2/library-1.2-tests.jar").toString(),
                tool.artifactPath(request, "org.example:library:1.2:jar:tests", null));
        assertEquals(
                home.resolve(".m2/repository/org/example/library/maven-metadata-central.xml").toString(),
                tool.metadataPath(request, "org.example:library", "central"));
    }

    @Test
    void honoursConfiguredRepositoryAndSettings() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd);
        Path settings = cwd.resolve("settings.xml");
        Files.write(settings, Collections.singletonList(
                "<settings><localRepository>${user.home}/custom-repository</localRepository></settings>"));
        ExecutorRequest.Builder request = ExecutorRequest.mavenBuilder()
                .userHomeDirectory(home)
                .cwd(cwd)
                .argument("--settings")
                .argument("settings.xml");
        LocalRepositoryExecutorTool tool = new LocalRepositoryExecutorTool(noopExecutor());

        assertEquals(home.resolve("custom-repository").toString(), tool.localRepository(request));
    }

    @Test
    void explicitRepositoryPropertyTakesPrecedence() throws Exception {
        Path repository = tempDir.resolve("repository");
        ExecutorRequest.Builder request = ExecutorRequest.mavenBuilder()
                .userHomeDirectory(tempDir.resolve("home"))
                .cwd(tempDir)
                .argument("-Dmaven.repo.local=" + repository);

        assertEquals(repository.toString(), new LocalRepositoryExecutorTool(noopExecutor()).localRepository(request));
    }

    private static Executor noopExecutor() {
        return new Executor() {
            @Override
            public ExecutorResult execute(ExecutorRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String mavenVersion() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {}
        };
    }
}

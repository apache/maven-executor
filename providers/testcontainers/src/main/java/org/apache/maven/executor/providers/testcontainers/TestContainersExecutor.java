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
package org.apache.maven.executor.providers.testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.github.dockerjava.api.DockerClient;
import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;

import static java.util.Objects.requireNonNull;

/**
 * Executor that uses {@link org.testcontainers.Testcontainers} to run <a href="https://hub.docker.com/_/maven">Maven Docker image</a>.
 */
public class TestContainersExecutor implements Executor {
    protected final String imageName;
    protected final String imageTag;
    protected final ConcurrentHashMap<String, String> cache;

    /**
     * Method to create executor that will use <a href="https://hub.docker.com/_/maven">Maven Docker image</a> with
     * given tag (Maven version).
     *
     * @param mavenVersion required param, the Maven version, not {@code null}.
     */
    public static TestContainersExecutor withMavenImageVersion(String mavenVersion) {
        return new TestContainersExecutor("maven", requireNonNull(mavenVersion));
    }

    /**
     * Method to create executor that will use given Docker image with optionally provided tag.
     *
     * @param imageName required param, the image name to use, not {@code null}.
     * @param imageTag optional param, the image tag, may be {@code null}.
     */
    public static TestContainersExecutor withImage(String imageName, String imageTag) {
        return new TestContainersExecutor(imageName, imageTag);
    }

    private TestContainersExecutor(String imageName, String imageTag) {
        this.imageName = requireNonNull(imageName);
        this.imageTag = imageTag;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Note: Testcontainers uses Lombok {@code @Sneakythrows}.
     */
    @Override
    public int execute(ExecutorRequest request) throws ExecutorException {
        requireNonNull(request);

        HashMap<String, String> env = new HashMap<>();
        request.environmentVariables().ifPresent(env::putAll);
        env.put("MAVEN_CONFIG", "/var/maven-home/.m2");

        ArrayList<String> command = new ArrayList<>();
        command.add(request.command());
        command.add("-Duser.home=/var/maven-home");
        command.addAll(request.arguments());

        MemoizingOneShotStartupCheckStrategy startupCheckStrategy = new MemoizingOneShotStartupCheckStrategy();
        try (GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse(imageName + (imageTag != null ? ":" + imageTag : "")))) {
            container
                    .withFileSystemBind(request.userHomeDirectory().toString(), "/var/maven-home/")
                    .withFileSystemBind(request.cwd().toString(), "/var/maven-project")
                    .withWorkingDirectory("/var/maven-project")
                    .withStartupCheckStrategy(startupCheckStrategy)
                    .withCommand(command.toArray(new String[0]))
                    .withCreateContainerCmdModifier(
                            cmd -> cmd.withUser(Integer.toString(detectUid(request.userHomeDirectory()))))
                    .withEnv(env)
                    .start();

            try {
                new ByteArrayInputStream(
                                container.getLogs(OutputFrame.OutputType.STDOUT).getBytes(StandardCharsets.UTF_8))
                        .transferTo(request.stdOut().orElse(OutputStream.nullOutputStream()));
                new ByteArrayInputStream(
                                container.getLogs(OutputFrame.OutputType.STDERR).getBytes(StandardCharsets.UTF_8))
                        .transferTo(request.stdErr().orElse(OutputStream.nullOutputStream()));
                return startupCheckStrategy.lastStatus.get() == StartupCheckStrategy.StartupStatus.SUCCESSFUL ? 0 : 1;
            } catch (IOException e) {
                throw new ExecutorException(e);
            }
        }
    }

    @Override
    public String mavenVersion(ExecutorRequest executorRequest) throws ExecutorException {
        return cache.computeIfAbsent("maven.version", k -> {
            ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
            ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
            int exitCode = execute(executorRequest.toBuilder()
                    .command(ExecutorRequest.MVN)
                    .arguments("-q", "-v")
                    .stdOut(stdOut)
                    .stdErr(stdErr)
                    .build());
            if (exitCode == 0) {
                return stdOut.toString().trim();
            } else {
                throw new ExecutorException(
                        "Unexpected exit code: " + exitCode + "; stdout = " + stdOut + "; stderr = " + stdErr);
            }
        });
    }

    private static int detectUid(Path userHome) {
        try {
            return (Integer) Files.getAttribute(userHome, "unix:uid");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {}

    private static class MemoizingOneShotStartupCheckStrategy extends OneShotStartupCheckStrategy {
        private final AtomicReference<StartupStatus> lastStatus = new AtomicReference<>(null);

        public StartupStatus checkStartupState(DockerClient dockerClient, String containerId) {
            StartupStatus startupStatus = super.checkStartupState(dockerClient, containerId);
            lastStatus.set(startupStatus);
            return startupStatus;
        }
    }
}

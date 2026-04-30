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
package org.apache.maven.executor.providers.dockerexe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.support.ProcessBuilderExecutorSupport;

import static java.util.Objects.requireNonNull;

/**
 * Executor that spawns a process with {@code docker} CLI to run <a href="https://hub.docker.com/_/maven">Maven Docker image</a>.
 */
public class DockerExeExecutor extends ProcessBuilderExecutorSupport implements Executor {
    protected final String imageName;
    protected final String imageTag;
    protected final ConcurrentHashMap<String, String> cache;

    /**
     * Method to create executor that will use <a href="https://hub.docker.com/_/maven">Maven Docker image</a> with
     * given tag (Maven version).
     *
     * @param mavenVersion required param, the Maven version, not {@code null}.
     */
    public static DockerExeExecutor withMavenImageVersion(String mavenVersion) {
        return new DockerExeExecutor("maven", requireNonNull(mavenVersion));
    }

    /**
     * Method to create executor that will use given Docker image with optionally provided tag.
     *
     * @param imageName required param, the image name to use, not {@code null}.
     * @param imageTag optional param, the image tag, may be {@code null}.
     */
    public static DockerExeExecutor withImage(String imageName, String imageTag) {
        return new DockerExeExecutor(imageName, imageTag);
    }

    private DockerExeExecutor(String imageName, String imageTag) {
        this.imageName = requireNonNull(imageName);
        this.imageTag = imageTag;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public int execute(ExecutorRequest request) throws ExecutorException {
        requireNonNull(request);

        try {
            HashMap<String, String> env = new HashMap<>();
            request.environmentVariables().ifPresent(env::putAll);
            env.put("MAVEN_CONFIG", "/var/maven-home/.m2");

            ArrayList<String> command = new ArrayList<>();
            command.add("docker");
            command.add("run");
            command.add("--rm");
            command.add("-q");
            command.add("-u");
            command.add(Integer.toString(detectUid(request.userHomeDirectory())));

            for (Map.Entry<String, String> entry : env.entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + entry.getValue());
            }

            command.add("-v");
            command.add(request.userHomeDirectory() + ":/var/maven-home/");
            command.add("-v");
            command.add(request.cwd() + ":/var/maven-project");
            command.add("-w");
            command.add("/var/maven-project");
            command.add(imageName + (imageTag != null ? ":" + imageTag : ""));
            command.add(request.command());
            command.add("-Duser.home=/var/maven-home");
            command.addAll(request.arguments());

            return doExecuteProcess(
                    request,
                    new ProcessBuilder().directory(request.cwd().toFile()).command(command));
        } catch (IOException e) {
            throw new ExecutorException(e);
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

    private static int detectUid(Path userHome) throws IOException {
        return (Integer) Files.getAttribute(userHome, "unix:uid");
    }

    @Override
    public void close() {}
}

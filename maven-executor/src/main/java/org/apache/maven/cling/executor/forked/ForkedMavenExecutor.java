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
package org.apache.maven.cling.executor.forked;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.maven.cling.executor.Executor;
import org.apache.maven.cling.executor.ExecutorException;
import org.apache.maven.cling.executor.ExecutorRequest;

import static java.util.Objects.requireNonNull;
import static org.apache.maven.cling.executor.ExecutorRequest.getCanonicalPath;

/**
 * Forked executor implementation, that spawns a subprocess with Maven from the installation directory. Very costly
 * but provides the best isolation.
 */
public class ForkedMavenExecutor extends ProcessBuilderExecutorSupport implements Executor {
    protected final Path installationDirectory;
    protected final boolean useMavenArgsEnv;

    public ForkedMavenExecutor(Path installationDirectory) {
        this(installationDirectory, false);
    }

    public ForkedMavenExecutor(Path installationDirectory, boolean useMavenArgsEnv) {
        this.installationDirectory = requireNonNull(installationDirectory);
        this.useMavenArgsEnv = useMavenArgsEnv;
    }

    @Override
    public int execute(ExecutorRequest executorRequest) throws ExecutorException {
        requireNonNull(executorRequest);
        if (closed.get()) {
            throw new ExecutorException("Executor is closed");
        }
        validate(executorRequest);

        return doExecute(executorRequest);
    }

    @Override
    public String mavenVersion(ExecutorRequest executorRequest) throws ExecutorException {
        requireNonNull(executorRequest);
        if (closed.get()) {
            throw new ExecutorException("Executor is closed");
        }
        validate(executorRequest);
        try {
            Path cwd = Files.createTempDirectory("forked-executor-maven-version");
            try {
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                int exitCode = execute(executorRequest.toBuilder()
                        .cwd(cwd)
                        .arguments(List.of("--version", "--quiet"))
                        .stdOut(stdout)
                        .build());
                if (exitCode == 0) {
                    if (stdout.size() > 0) {
                        return stdout.toString()
                                .replace("\n", "")
                                .replace("\r", "")
                                .trim();
                    }
                    return UNKNOWN_VERSION;
                } else {
                    throw new ExecutorException(
                            "Maven version query unexpected exitCode=" + exitCode + "\nLog: " + stdout);
                }
            } finally {
                Files.deleteIfExists(cwd);
            }
        } catch (IOException e) {
            throw new ExecutorException("Failed to determine maven version", e);
        }
    }

    protected void validate(ExecutorRequest executorRequest) throws ExecutorException {}

    protected int doExecute(ExecutorRequest executorRequest) throws ExecutorException {
        ArrayList<String> cmdAndArguments = new ArrayList<>();
        cmdAndArguments.add(installationDirectory
                .resolve("bin")
                .resolve(IS_WINDOWS ? executorRequest.command() + ".cmd" : executorRequest.command())
                .toString());

        String mavenArgsEnv = System.getenv("MAVEN_ARGS");
        if (useMavenArgsEnv && mavenArgsEnv != null && !mavenArgsEnv.isEmpty()) {
            Arrays.stream(mavenArgsEnv.split(" "))
                    .filter(s -> !s.trim().isEmpty())
                    .forEach(cmdAndArguments::add);
        }

        cmdAndArguments.addAll(executorRequest.arguments());

        ArrayList<String> jvmArgs = new ArrayList<>();
        if (!executorRequest.userHomeDirectory().equals(getCanonicalPath(Paths.get(System.getProperty("user.home"))))) {
            jvmArgs.add("-Duser.home=" + executorRequest.userHomeDirectory().toString());
        }
        if (executorRequest.jvmArguments().isPresent()) {
            jvmArgs.addAll(executorRequest.jvmArguments().get());
        }
        if (executorRequest.jvmSystemProperties().isPresent()) {
            jvmArgs.addAll(executorRequest.jvmSystemProperties().get().entrySet().stream()
                    .map(e -> "-D" + e.getKey() + "=" + e.getValue())
                    .toList());
        }

        HashMap<String, String> env = new HashMap<>();
        if (executorRequest.environmentVariables().isPresent()) {
            env.putAll(executorRequest.environmentVariables().get());
        }
        if (!jvmArgs.isEmpty()) {
            String mavenOpts = env.getOrDefault("MAVEN_OPTS", "");
            if (!mavenOpts.isEmpty()) {
                mavenOpts += " ";
            }
            mavenOpts += String.join(" ", jvmArgs);
            env.put("MAVEN_OPTS", mavenOpts);
        }
        env.remove("MAVEN_ARGS"); // we already used it if configured to do so

        if (executorRequest.skipMavenRc()) {
            env.put("MAVEN_SKIP_RC", "true");
        }

        ProcessBuilder pb =
                new ProcessBuilder().directory(executorRequest.cwd().toFile()).command(cmdAndArguments);
        if (!env.isEmpty()) {
            pb.environment().putAll(env);
        }
        return doExecuteProcess(executorRequest, pb);
    }
}

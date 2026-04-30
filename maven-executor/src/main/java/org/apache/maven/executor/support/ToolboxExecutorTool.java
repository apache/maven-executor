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
package org.apache.maven.executor.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorTool;

import static java.util.Objects.requireNonNull;

/**
 * {@link ExecutorTool} implementation based on Maveniverse Toolbox. It uses Toolbox mojos to implement all the
 * required operations.
 *
 * @see <a href="https://github.com/maveniverse/toolbox">Maveniverse Toolbox</a>
 */
public class ToolboxExecutorTool implements ExecutorTool {
    private static final String TOOLBOX_PREFIX = "eu.maveniverse.maven.plugins:toolbox:";

    private final Executor executor;
    private final String toolboxVersion;

    /**
     * @deprecated Better specify required version yourself. This one is "cemented" to 0.15.8
     */
    @Deprecated
    public ToolboxExecutorTool(Executor executor) {
        this(executor, "0.15.8");
    }

    public ToolboxExecutorTool(Executor executor, String toolboxVersion) {
        this.executor = requireNonNull(executor);
        this.toolboxVersion = requireNonNull(toolboxVersion);
    }

    @Override
    public Map<String, String> dump(ExecutorRequest.Builder executorRequest) throws ExecutorException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecutorRequest.Builder builder = mojo(executorRequest, "gav-dump")
                .argument("-DasProperties")
                .stdOut(stdout)
                .stdErr(stderr);
        doExecute(builder);
        try {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(
                    validateOutput(false, stdout, stderr).getBytes()));
            return properties.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> String.valueOf(e.getValue()),
                            (prev, next) -> next,
                            HashMap::new));
        } catch (IOException e) {
            throw new ExecutorException("Unable to parse properties", e);
        }
    }

    @Override
    public String localRepository(ExecutorRequest.Builder executorRequest) throws ExecutorException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecutorRequest.Builder builder = mojo(executorRequest, "gav-local-repository-path")
                .stdOut(stdout)
                .stdErr(stderr);
        doExecute(builder);
        return validateOutput(true, stdout, stderr);
    }

    @Override
    public String artifactPath(ExecutorRequest.Builder executorRequest, String gav, String repositoryId)
            throws ExecutorException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecutorRequest.Builder builder = mojo(executorRequest, "gav-artifact-path")
                .argument("-Dgav=" + gav)
                .stdOut(stdout)
                .stdErr(stderr);
        if (repositoryId != null) {
            builder.argument("-Drepository=" + repositoryId + "::unimportant");
        }
        doExecute(builder);
        return validateOutput(true, stdout, stderr);
    }

    @Override
    public String metadataPath(ExecutorRequest.Builder executorRequest, String gav, String repositoryId)
            throws ExecutorException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecutorRequest.Builder builder = mojo(executorRequest, "gav-metadata-path")
                .argument("-Dgav=" + gav)
                .stdOut(stdout)
                .stdErr(stderr);
        if (repositoryId != null) {
            builder.argument("-Drepository=" + repositoryId + "::unimportant");
        }
        doExecute(builder);
        return validateOutput(true, stdout, stderr);
    }

    private ExecutorRequest.Builder mojo(ExecutorRequest.Builder builder, String mojo) {
        if (executor.mavenVersion().startsWith("4.")) {
            builder.argument("--raw-streams");
        }
        return builder.argument(TOOLBOX_PREFIX + toolboxVersion + ":" + mojo)
                .argument("--quiet")
                .argument("-DforceStdout");
    }

    private void doExecute(ExecutorRequest.Builder builder) {
        ExecutorRequest request = builder.build();
        int ec = executor.execute(request);
        if (ec != 0) {
            throw new ExecutorException("Unexpected exit code=" + ec + "; stdout="
                    + request.stdOut().orElse(null) + "; stderr="
                    + request.stdErr().orElse(null));
        }
    }

    /**
     * Performs "sanity check" for output, making sure no insane values like empty strings are returned.
     */
    private String validateOutput(boolean shave, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        String result = stdout.toString();
        if (shave) {
            result = result.replace("\n", "").replace("\r", "");
        }
        // sanity checks: stderr has any OR result is empty string (no method should emit empty string)
        if (result.trim().isEmpty()) {
            // see bug https://github.com/apache/maven/pull/11303 Fail in this case
            // tl;dr We NEVER expect empty string as output from this tool; so fail here instead to chase ghosts
            throw new IllegalStateException("Empty output from Toolbox; stdout[" + stdout.size() + "]=" + stdout
                    + "; stderr[" + stderr.size() + "]=" + stderr);
        }
        return result;
    }
}

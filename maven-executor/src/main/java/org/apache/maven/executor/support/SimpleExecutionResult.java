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

import java.util.Optional;

import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;

import static java.util.Objects.requireNonNull;

/**
 * Simple execution result.
 */
public class SimpleExecutionResult implements ExecutorResult {
    private final ExecutorRequest request;
    private final boolean success;
    private final Integer exitCode;
    private final String stdOut;
    private final String stdErr;

    /**
     * Constructor.
     *
     * @param request the request, must not be {@code null}
     * @param success the logical outcome
     * @param exitCode the exit code, if available, or {@code null}
     * @param stdOut the STDOUT as string, if available, or {@code null}
     * @param stdErr the STDERR as string, if available, or {@code null}
     */
    public SimpleExecutionResult(
            ExecutorRequest request, boolean success, Integer exitCode, String stdOut, String stdErr) {
        this.request = requireNonNull(request);
        this.success = success;
        // below all are nullable
        this.exitCode = exitCode;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

    @Override
    public ExecutorRequest getRequest() {
        return request;
    }

    @Override
    public boolean success() {
        return success;
    }

    @Override
    public Optional<Integer> exitCode() {
        return Optional.ofNullable(exitCode);
    }

    @Override
    public Optional<String> stdOutString() {
        return Optional.ofNullable(stdOut);
    }

    @Override
    public Optional<String> stdErrString() {
        return Optional.ofNullable(stdErr);
    }
}

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

import java.util.Optional;

/**
 * Thrown when an execution does not finish within {@link ExecutorRequest#executionTimeout()}. It carries the output
 * the execution produced before it was killed, so a caller can see where the build stalled.
 */
public class ExecutorTimeoutException extends ExecutorException {
    private final String stdOutString;

    private final String stdErrString;

    /**
     * Constructs a new {@code ExecutorTimeoutException}.
     *
     * @param message the detail message
     * @param stdOutString the STDOUT grabbed until the timeout, or {@code null} if output was not grabbed
     * @param stdErrString the STDERR grabbed until the timeout, or {@code null} if output was not grabbed
     */
    public ExecutorTimeoutException(String message, String stdOutString, String stdErrString) {
        super(message);
        this.stdOutString = stdOutString;
        this.stdErrString = stdErrString;
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the STDOUT the execution produced before
     * it timed out. Otherwise, empty: caller-supplied {@link ExecutorRequest#stdOut()} already received it.
     */
    public Optional<String> stdOutString() {
        return Optional.ofNullable(stdOutString);
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the STDERR the execution produced before
     * it timed out. Otherwise, empty: caller-supplied {@link ExecutorRequest#stdErr()} already received it.
     */
    public Optional<String> stdErrString() {
        return Optional.ofNullable(stdErrString);
    }
}

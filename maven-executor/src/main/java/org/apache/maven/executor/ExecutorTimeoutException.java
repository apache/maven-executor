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
 * Thrown when an execution does not finish within {@link ExecutorRequest#executionTimeout()}. It carries the tail of
 * the output the execution produced before it was killed, so a caller can see where the build stalled. Only the tail:
 * a hung build may have logged far more than should be kept alive with an exception.
 */
public class ExecutorTimeoutException extends ExecutorException {
    /**
     * The most output, in bytes, kept from each of STDOUT and STDERR.
     */
    public static final int MAX_TAIL_BYTES = 64 * 1024;

    private final String stdOutTail;

    private final String stdErrTail;

    /**
     * Constructs a new {@code ExecutorTimeoutException}.
     *
     * @param message the detail message
     * @param stdOutTail the end of the STDOUT grabbed until the timeout, or {@code null} if output was not grabbed
     * @param stdErrTail the end of the STDERR grabbed until the timeout, or {@code null} if output was not grabbed
     */
    public ExecutorTimeoutException(String message, String stdOutTail, String stdErrTail) {
        super(message);
        this.stdOutTail = stdOutTail;
        this.stdErrTail = stdErrTail;
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the end of the STDOUT the execution
     * produced before it timed out: at most {@link #MAX_TAIL_BYTES}. When cut, it starts at a line boundary if the
     * kept window contains one, and may otherwise start mid-line.
     * Otherwise, empty: caller-supplied {@link ExecutorRequest#stdOut()} already received all of it.
     */
    public Optional<String> stdOutTail() {
        return Optional.ofNullable(stdOutTail);
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the end of the STDERR the execution
     * produced before it timed out: at most {@link #MAX_TAIL_BYTES}. When cut, it starts at a line boundary if the
     * kept window contains one, and may otherwise start mid-line.
     * Otherwise, empty: caller-supplied {@link ExecutorRequest#stdErr()} already received all of it.
     */
    public Optional<String> stdErrTail() {
        return Optional.ofNullable(stdErrTail);
    }
}

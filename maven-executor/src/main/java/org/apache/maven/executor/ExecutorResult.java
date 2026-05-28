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
 * Represents an execution result.
 */
public interface ExecutorResult {
    /**
     * The {@link ExecutorRequest} this result is for.
     */
    ExecutorRequest getRequest();

    /**
     * The outcome of execution.
     */
    boolean success();

    /**
     * The exit code, if available (ie running the tool happened in a way it did produce exit code).
     */
    default Optional<Integer> exitCode() {
        return Optional.empty();
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the {@link String} containing
     * STDOUT of tool. Never {@code null}, but maybe empty string. Otherwise, empty.
     */
    default Optional<String> stdOutString() {
        return Optional.empty();
    }

    /**
     * If {@link ExecutorRequest#grabOutputAsString()} was {@code true}, then the {@link String} containing
     * STDERR of tool. Never {@code null}, but maybe empty string. Otherwise, empty.
     */
    default Optional<String> stdErrString() {
        return Optional.empty();
    }
}

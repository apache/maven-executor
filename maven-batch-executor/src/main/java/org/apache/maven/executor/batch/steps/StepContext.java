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
package org.apache.maven.executor.batch.steps;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.executor.ExecutorException;
import org.slf4j.Logger;

/**
 * The context of {@link Step}.
 */
public interface StepContext {
    /**
     * Chain specific logger.
     */
    Logger log();

    /**
     * The current working directory.
     */
    Path cwd();

    /**
     * The user home directory.
     */
    Path userHome();

    /**
     * Immutable map of environment variables.
     */
    Map<String, String> environmentVariables();

    /**
     * Returns the {@link Tool}.
     */
    Tool tool();

    /**
     * Performs an execution.
     */
    Execution.Result execute(Execution.Request execution) throws ExecutorException;

    /**
     * Returns all, so far happened executions, where last (with the largest index) is most recent one.
     */
    List<Execution.Result> executions();

    /**
     * Returns the last {@link Execution.Result}, if there was any, empty otherwise.
     */
    default Optional<Execution.Result> last() {
        if (executions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(executions().get(executions().size() - 1));
    }

    /**
     * Shared mutable map.
     */
    Map<String, Object> shared();

    /**
     * Makes this step chain skipped.
     */
    void skip(String message);

    /**
     * Makes this step chain failed.
     * <p>
     * Note: if any step throws {@link AssertionError}, this method is also invoked.
     */
    void fail(Throwable throwable);
}

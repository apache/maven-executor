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

import static java.util.Objects.requireNonNull;

/**
 * Execute step: executes one {@link Execution.Request} and enforces outcome.
 */
public class ExecuteStep implements Step {
    private final Execution.Request execution;
    private final boolean shouldFail;

    public ExecuteStep(Execution.Request execution, boolean shouldFail) {
        this.execution = requireNonNull(execution);
        this.shouldFail = shouldFail;
    }

    @Override
    public void execute(StepContext context) {
        Execution.Result result = context.execute(execution);
        if (shouldFail) {
            if (result.exitCode() == 0) {
                context.fail(new AssertionError("Execution should have been failed"));
            }
        } else {
            if (result.exitCode() != 0) {
                context.fail(new AssertionError("Execution should have succeeded"));
            }
        }
    }
}

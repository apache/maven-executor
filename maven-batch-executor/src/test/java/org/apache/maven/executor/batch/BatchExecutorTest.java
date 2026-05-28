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
package org.apache.maven.executor.batch;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.batch.steps.ContextStep;
import org.apache.maven.executor.batch.steps.Environment;
import org.apache.maven.executor.batch.steps.ExecuteStep;
import org.apache.maven.executor.batch.steps.Execution;
import org.apache.maven.executor.batch.steps.Step;
import org.apache.maven.executor.support.SimpleExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchExecutorTest {
    @Test
    void smoke() {
        Executor executor = new Executor() {
            @Override
            public ExecutorResult execute(ExecutorRequest executorRequest) throws ExecutorException {
                return new SimpleExecutionResult(executorRequest, true, 0, "Executed", null);
            }

            @Override
            public String mavenVersion() throws ExecutorException {
                return "3.9.15";
            }

            @Override
            public void close() throws ExecutorException {}
        };
        BatchExecutor batch = BatchExecutor.singleThreaded(executor);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");

        Path userHome = Path.of("target/user-home");
        Path cwd = Path.of("target/cwd");
        BatchExecutorRequest request = new BatchExecutorRequest() {
            @Override
            public Map<Environment, List<Step>> environments() {
                return Map.of(
                        new Environment(ExecutorRequest.mavenBuilder()
                                .userHomeDirectory(userHome)
                                .cwd(cwd)
                                .build()),
                        List.of(
                                new ContextStep(
                                        ContextStep.ofScriptEngine(engine, "assert binding.variables['last'] == null")),
                                new ContextStep(c -> assertTrue(c.shared().isEmpty())),
                                new ContextStep(c -> c.shared().put("a", "b")),
                                new ContextStep(c -> assertFalse(c.shared().isEmpty())),
                                new ExecuteStep(
                                        new Execution.Request() {
                                            @Override
                                            public String command() {
                                                return "mvn";
                                            }

                                            @Override
                                            public List<String> arguments() {
                                                return List.of();
                                            }

                                            @Override
                                            public Optional<Map<String, String>> environmentVariables() {
                                                return Optional.empty();
                                            }

                                            @Override
                                            public Optional<Map<String, String>> jvmSystemProperties() {
                                                return Optional.empty();
                                            }

                                            @Override
                                            public Optional<List<String>> jvmArguments() {
                                                return Optional.empty();
                                            }
                                        },
                                        false),
                                new ContextStep(
                                        ContextStep.ofScriptEngine(engine, "assert binding.variables['last'] != null")),
                                new ContextStep(ContextStep.ofScriptEngine(engine, "context.log().info('hello')")),
                                new ContextStep(ContextStep.ofScriptEngine(engine, "log.info('hello again')")),
                                new ContextStep(
                                        c -> assertEquals(1, c.executions().size())),
                                new ContextStep(
                                        c -> assertEquals("b", c.shared().get("a"))),
                                new ContextStep(c -> assertEquals(
                                        "Executed!", c.last().orElseThrow().stdOut()))));
            }
        };
        BatchExecutorResult result = batch.execute(request);
        assertEquals(1, result.results().size());
    }
}

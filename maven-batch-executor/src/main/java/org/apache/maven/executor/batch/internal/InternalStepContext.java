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
package org.apache.maven.executor.batch.internal;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorTool;
import org.apache.maven.executor.batch.steps.Environment;
import org.apache.maven.executor.batch.steps.Execution;
import org.apache.maven.executor.batch.steps.StepContext;
import org.apache.maven.executor.batch.steps.Tool;
import org.apache.maven.executor.support.ToolboxExecutorTool;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Defines one step of batch execution.
 */
public class InternalStepContext implements StepContext {
    private final Logger logger;
    private final Environment environment;
    private final Executor executor;
    private final List<Execution.Result> resultList;

    private final ExecutorTool executorTool;
    private final ConcurrentHashMap<String, Object> shared;
    private final AtomicReference<String> skipped;
    private final AtomicReference<String> failed;

    public InternalStepContext(
            Logger logger, Environment environment, Executor executor, List<Execution.Result> resultList) {
        this.logger = requireNonNull(logger);
        this.environment = requireNonNull(environment);
        this.executor = requireNonNull(executor);
        this.resultList = requireNonNull(resultList);

        this.executorTool = new ToolboxExecutorTool(executor, "0.15.8");
        this.shared = new ConcurrentHashMap<>();
        this.skipped = new AtomicReference<>(null);
        this.failed = new AtomicReference<>(null);
    }

    @Override
    public Logger log() {
        return logger;
    }

    @Override
    public Path cwd() {
        return environment.template().cwd();
    }

    @Override
    public Path userHome() {
        return environment.template().userHomeDirectory();
    }

    @Override
    public Map<String, String> environmentVariables() {
        return environment.template().environmentVariables().orElse(Map.of());
    }

    @Override
    public Tool tool() {
        return null;
    }

    @Override
    public Execution.Result execute(Execution.Request execution) throws ExecutorException {
        ExecutorRequest.Builder builder =
                environment.template().toBuilder().command(execution.command()).arguments(execution.arguments());
        execution.environmentVariables().ifPresent(ev -> ev.forEach(builder::environmentVariable));
        execution.jvmSystemProperties().ifPresent(sp -> sp.forEach(builder::jvmSystemProperty));
        execution.jvmArguments().ifPresent(ja -> ja.forEach(builder::jvmArgument));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = executor.execute(builder.stdOut(out).stdErr(err).build());
        Execution.Result result = new Execution.Result() {
            @Override
            public int exitCode() {
                return exitCode;
            }

            @Override
            public String stdOut() {
                return out.toString();
            }

            @Override
            public String stdErr() {
                return err.toString();
            }
        };
        resultList.add(result);
        return result;
    }

    @Override
    public List<Execution.Result> executions() {
        return List.copyOf(resultList);
    }

    @Override
    public Map<String, Object> shared() {
        return shared;
    }

    @Override
    public void skip(String message) {
        requireNonNull(message);
        logger.info("SKIP: {}", message);
        skipped.set(message);
    }

    @Override
    public void fail(Throwable throwable) {
        requireNonNull(throwable);
        logger.info("FAIL: {}", throwable.getMessage());
        failed.set(throwable.getMessage());
    }

    // ==

    public boolean shouldContinue() {
        return failed.get() == null && skipped.get() == null;
    }
}

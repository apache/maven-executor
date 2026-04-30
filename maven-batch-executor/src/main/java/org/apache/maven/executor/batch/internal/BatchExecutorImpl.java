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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.batch.BatchExecutor;
import org.apache.maven.executor.batch.BatchExecutorRequest;
import org.apache.maven.executor.batch.BatchExecutorResult;
import org.apache.maven.executor.batch.steps.Environment;
import org.apache.maven.executor.batch.steps.Execution;
import org.apache.maven.executor.batch.steps.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple and naive, single threaded implementation of {@link BatchExecutor}.
 */
public class BatchExecutorImpl implements BatchExecutor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Executor executor;
    private final AtomicBoolean atomicBoolean;

    public BatchExecutorImpl(Executor executor) {
        this.executor = executor;
        this.atomicBoolean = new AtomicBoolean(false);
    }

    @Override
    public BatchExecutorResult execute(BatchExecutorRequest batch) throws ExecutorException {
        logger.info("Starting to execute batch execution");
        HashMap<Environment, List<Execution.Result>> results = new HashMap<>();
        for (Map.Entry<Environment, List<Step>> entry : batch.environments().entrySet()) {
            Environment environment = entry.getKey();
            String name = environment.template().cwd().getFileName().toString();
            ArrayList<Execution.Result> resultList = new ArrayList<>();
            results.put(environment, resultList);
            InternalStepContext context =
                    new InternalStepContext(LoggerFactory.getLogger(name), environment, executor, resultList);
            for (Step step : entry.getValue()) {
                try {
                    step.execute(context);
                    if (!context.shouldContinue()) {
                        break;
                    }
                } catch (AssertionError error) {
                    context.fail(error);
                    break;
                }
            }
        }
        logger.info("Finished batch execution");
        return new BatchExecutorResult() {
            @Override
            public BatchExecutorRequest request() {
                return batch;
            }

            @Override
            public Map<Environment, List<Execution.Result>> results() {
                return Map.copyOf(results);
            }
        };
    }

    @Override
    public void close() throws ExecutorException {
        if (atomicBoolean.compareAndSet(false, true)) {
            executor.close();
        }
    }
}

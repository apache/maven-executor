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
package org.apache.maven.cling.executor.support;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.cling.executor.Executor;
import org.apache.maven.cling.executor.ExecutorException;
import org.apache.maven.cling.executor.ExecutorHelper;
import org.apache.maven.cling.executor.ExecutorRequest;

import static java.util.Objects.requireNonNull;

/**
 * Simple router to executors, and delegate to executor tool.
 */
public class ExecutorHelperImpl implements ExecutorHelper {
    private final Mode defaultMode;
    private final HashMap<Mode, Executor> executors;

    private final ConcurrentHashMap<String, String> cache;

    public ExecutorHelperImpl(Mode defaultMode, Executor embedded, Executor forked) {
        this.defaultMode = requireNonNull(defaultMode);
        this.executors = new HashMap<>();

        this.executors.put(Mode.EMBEDDED, requireNonNull(embedded, "embedded"));
        this.executors.put(Mode.FORKED, requireNonNull(forked, "forked"));
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Mode getDefaultMode() {
        return defaultMode;
    }

    @Override
    public ExecutorRequest.Builder executorRequest() {
        return ExecutorRequest.mavenBuilder();
    }

    @Override
    public int execute(Mode mode, ExecutorRequest executorRequest) throws ExecutorException {
        return getExecutor(mode, executorRequest).execute(executorRequest);
    }

    @Override
    public String mavenVersion() {
        return cache.computeIfAbsent("maven.version", k -> {
            ExecutorRequest request = executorRequest().build();
            return getExecutor(Mode.AUTO, request).mavenVersion(request);
        });
    }

    protected Executor getExecutor(Mode mode, ExecutorRequest request) throws ExecutorException {
        return switch (mode) {
            case AUTO -> getExecutorByRequest(request);
            case EMBEDDED -> executors.get(Mode.EMBEDDED);
            case FORKED -> executors.get(Mode.FORKED);
        };
    }

    private Executor getExecutorByRequest(ExecutorRequest request) {
        if (request.environmentVariables().isEmpty() && request.jvmArguments().isEmpty()) {
            return getExecutor(Mode.EMBEDDED, request);
        } else {
            return getExecutor(Mode.FORKED, request);
        }
    }
}

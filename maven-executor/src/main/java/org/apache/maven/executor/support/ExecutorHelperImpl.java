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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.embedded.EmbeddedMavenExecutor;
import org.apache.maven.executor.forked.ForkedMavenExecutor;

import static java.util.Objects.requireNonNull;

/**
 * Simple router to executors, and delegate to executor tool.
 */
public class ExecutorHelperImpl implements ExecutorHelper {
    private final Mode defaultMode;
    private final HashMap<Mode, Executor> executors;
    private final boolean manageExecutors;

    private final ConcurrentHashMap<String, String> cache;
    private final AtomicBoolean closed;

    public ExecutorHelperImpl(
            Mode defaultMode, EmbeddedMavenExecutor embedded, ForkedMavenExecutor forked, boolean manageExecutors) {
        this.defaultMode = requireNonNull(defaultMode);
        this.executors = new HashMap<>();
        this.manageExecutors = manageExecutors;

        this.executors.put(Mode.EMBEDDED, requireNonNull(embedded, "embedded"));
        this.executors.put(Mode.FORKED, requireNonNull(forked, "forked"));
        this.cache = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public Mode getDefaultMode() {
        return defaultMode;
    }

    @Override
    public int execute(Mode mode, ExecutorRequest executorRequest) throws ExecutorException {
        if (closed.get()) {
            throw new ExecutorException("Executor is closed");
        }
        return getExecutor(mode, executorRequest).execute(executorRequest);
    }

    @Override
    public String mavenVersion() {
        if (closed.get()) {
            throw new ExecutorException("Executor is closed");
        }
        return cache.computeIfAbsent(
                "maven.version",
                k -> getExecutor(
                                Mode.AUTO,
                                ExecutorRequest.mavenBuilder()
                                        .userHomeDirectory(ExecutorRequest.discoverUserHomeDirectory())
                                        .build())
                        .mavenVersion());
    }

    @Override
    public void close() throws ExecutorException {
        if (closed.compareAndSet(false, true)) {
            if (manageExecutors) {
                ArrayList<ExecutorException> exceptions = new ArrayList<>();
                for (Executor executor : executors.values()) {
                    try {
                        executor.close();
                    } catch (ExecutorException e) {
                        exceptions.add(e);
                    }
                }
                if (!exceptions.isEmpty()) {
                    if (exceptions.size() == 1) {
                        throw exceptions.get(0);
                    } else {
                        ExecutorException ex = new ExecutorException("Could not close executors");
                        exceptions.forEach(ex::addSuppressed);
                        throw ex;
                    }
                }
            }
        }
    }

    protected Executor getExecutor(Mode mode, ExecutorRequest request) throws ExecutorException {
        return switch (mode) {
            case AUTO -> getExecutorByRequest(request);
            case EMBEDDED -> executors.get(Mode.EMBEDDED);
            case FORKED -> executors.get(Mode.FORKED);
        };
    }

    protected Executor getExecutorByRequest(ExecutorRequest request) {
        if (request.environmentVariables().isEmpty() && request.jvmArguments().isEmpty()) {
            return getExecutor(Mode.EMBEDDED, request);
        } else {
            return getExecutor(Mode.FORKED, request);
        }
    }
}

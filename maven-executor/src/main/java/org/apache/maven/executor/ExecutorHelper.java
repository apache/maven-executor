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

import java.nio.file.Path;

import org.apache.maven.executor.embedded.EmbeddedMavenExecutor;
import org.apache.maven.executor.forked.ForkedMavenExecutor;
import org.apache.maven.executor.support.ExecutorHelperImpl;

/**
 * Helper class for routing Maven execution based on preferences and/or issued execution requests.
 */
public interface ExecutorHelper extends Executor {
    /**
     * Creates {@link ExecutorHelper} along with needed executors using provided installation directory. Created instance
     * will assume that provided executors <em>are managed by itself</em>, and on close will close them as well.
     *
     * @param installationDirectory The Maven Home (installation) directory.
     * @param defaultMode The default mode of helper.
     * @return New instance of helper that will create executors, and close them when is closed.
     */
    static ExecutorHelper forMavenInstallation(Path installationDirectory, Mode defaultMode) {
        Path mavenHome = ExecutorRequest.getCanonicalPath(installationDirectory);
        EmbeddedMavenExecutor embedded = new EmbeddedMavenExecutor(mavenHome);
        ForkedMavenExecutor forked = new ForkedMavenExecutor(mavenHome);
        return new ExecutorHelperImpl(defaultMode, embedded, forked, true);
    }

    /**
     * Creates {@link ExecutorHelper} instance with provided default mode and provided executors. Created instance
     * will assume that provided executors are <em>managed by caller</em>, and on close will not close them.
     *
     * @param defaultMode The default mode of helper.
     * @param embedded The embedded executor to use.
     * @param forked The forked executor to use.
     * @return New instance of helper that will not close provided executors when is closed.
     */
    static ExecutorHelper forExecutors(Mode defaultMode, EmbeddedMavenExecutor embedded, ForkedMavenExecutor forked) {
        return new ExecutorHelperImpl(defaultMode, embedded, forked, false);
    }

    /**
     * The modes of execution.
     */
    enum Mode {
        /**
         * Automatically decide. For example, presence of {@link ExecutorRequest#environmentVariables()} or
         * {@link ExecutorRequest#jvmArguments()} will result in choosing {@link #FORKED} executor. Otherwise,
         * {@link #EMBEDDED} executor is preferred.
         */
        AUTO,
        /**
         * Forces embedded execution. May fail if {@link ExecutorRequest} contains input unsupported by executor.
         */
        EMBEDDED,
        /**
         * Forces forked execution. Always carried out, most isolated and "most correct", but is slow as it uses child process.
         */
        FORKED
    }

    /**
     * Returns the preferred mode of this helper.
     */
    Mode getDefaultMode();

    /**
     * Executes the request with preferred mode executor.
     */
    default ExecutorResult execute(ExecutorRequest executorRequest) throws ExecutorException {
        return execute(getDefaultMode(), executorRequest);
    }

    /**
     * Executes the request with passed in mode executor.
     */
    ExecutorResult execute(Mode mode, ExecutorRequest executorRequest) throws ExecutorException;
}

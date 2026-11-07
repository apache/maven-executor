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

import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.embedded.EmbeddedMavenExecutor;
import org.apache.maven.executor.forked.ForkedMavenExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ExecutorHelperImplTest {
    @TempDir
    private Path tempDir;

    @Test
    void autoModeRoutesByRequest() {
        EmbeddedMavenExecutor embedded = new EmbeddedMavenExecutor(tempDir);
        ForkedMavenExecutor forked = new ForkedMavenExecutor(tempDir);
        ExecutorHelperImpl helper = new ExecutorHelperImpl(ExecutorHelper.Mode.AUTO, embedded, forked, false);

        ExecutorRequest.Builder request =
                ExecutorRequest.mavenBuilder().cwd(tempDir).userHomeDirectory(tempDir);
        assertSame(embedded, helper.getExecutor(ExecutorHelper.Mode.AUTO, request.build()));
        assertSame(
                forked,
                helper.getExecutor(
                        ExecutorHelper.Mode.AUTO, request.jvmArgument("-Xmx64m").build()));
        assertSame(
                forked,
                helper.getExecutor(
                        ExecutorHelper.Mode.AUTO,
                        request.jvmArguments(null)
                                .executionTimeout(Duration.ofMinutes(1))
                                .build()));
    }
}

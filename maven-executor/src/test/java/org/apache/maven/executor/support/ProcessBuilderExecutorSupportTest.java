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

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(60)
class ProcessBuilderExecutorSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void timeoutDestroysTheProcessTree() throws Exception {
        assumeTrue(hasProcessHandle(), "descendants are reachable from Java 9 on");
        File heartbeat = tempDir.resolve("heartbeat").toFile();

        assertThrows(
                ExecutorException.class,
                () -> new TestExecutor().execute(request(Duration.ofSeconds(5)), HangingProcess.parent(heartbeat)));

        assertTrue(heartbeat.length() > 0, "the child never started, so the test proves nothing");
        long length = heartbeat.length();
        Thread.sleep(1000);
        assertEquals(length, heartbeat.length(), "the child process outlived the timeout");
    }

    private static boolean hasProcessHandle() {
        try {
            Class.forName("java.lang.ProcessHandle");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private ExecutorRequest request(Duration timeout) {
        return ExecutorRequest.mavenBuilder()
                .cwd(tempDir)
                .userHomeDirectory(tempDir)
                .executionTimeout(timeout)
                .grabOutputAsString(true)
                .build();
    }

    private static final class TestExecutor extends ProcessBuilderExecutorSupport {
        ExecutorResult execute(ExecutorRequest request, ProcessBuilder processBuilder) {
            return doExecuteProcess(request, processBuilder);
        }

        @Override
        public ExecutorResult execute(ExecutorRequest executorRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String mavenVersion() {
            throw new UnsupportedOperationException();
        }
    }
}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessBuilderExecutorSupportTest {

    @Test
    void doesNotCloseCallerOutputStreams() throws Exception {
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-version").start();
        TrackingOutputStream stdout = new TrackingOutputStream();
        TrackingOutputStream stderr = new TrackingOutputStream();

        new TestSupport()
                .pump(
                        process,
                        new InputStream() {
                            @Override
                            public int read() {
                                return -1;
                            }
                        },
                        stdout,
                        stderr)
                .await();
        process.waitFor();

        assertFalse(stdout.closed);
        assertFalse(stderr.closed);
        assertTrue(stderr.toString(StandardCharsets.UTF_8.name()).contains("version"));
    }

    private static class TestSupport extends ProcessBuilderExecutorSupport {
        @Override
        public ExecutorResult execute(ExecutorRequest executorRequest) {
            return null;
        }

        @Override
        public String mavenVersion() {
            return UNKNOWN_VERSION;
        }
    }

    private static class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}

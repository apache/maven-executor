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
package org.apache.maven.cling.executor.providers.testcontainers;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import org.apache.maven.cling.executor.ExecutorRequest;
import org.apache.maven.cling.executor.test.TestProjects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContainersExecutorTest {
    @Test
    void smoke() throws Exception {
        Path cwd = Path.of("target/test-classes/simple-project");
        TestProjects.createSimpleProject(cwd);
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ExecutorRequest request = ExecutorRequest.mavenBuilder()
                .cwd(cwd)
                .userHomeDirectory(cwd)
                .arguments("-V", "clean", "install")
                .stdOut(stdOut)
                .build();
        try (TestContainersExecutor executor = TestContainersExecutor.withMavenVersion("3.9.15")) {
            int exitCode = executor.execute(request);
            assertEquals(0, exitCode);
            assertTrue(stdOut.toString().contains("[INFO] BUILD SUCCESS"));
        }
    }
}

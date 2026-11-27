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
package org.apache.maven.executor.embedded;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.executor.Environment;
import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.MavenExecutorTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Embedded executor UT
 */
public class EmbeddedMavenExecutorTest extends MavenExecutorTestSupport {

    @Override
    protected Executor doSelectExecutor(Path installationDirectory) {
        return new EmbeddedMavenExecutor(installationDirectory);
    }

    @Test
    void mavenArgsEnvKeepsOrder() throws Exception {
        ExecutorRequest request = customizedRequest().argument("validate").build();
        Path pom = request.cwd().resolve("sub").resolve("pom.xml");
        Files.createDirectories(pom.getParent());
        Files.write(
                pom,
                ("<project><modelVersion>4.0.0</modelVersion><groupId>org.apache.maven.samples</groupId>"
                                + "<artifactId>sub</artifactId><version>1.0.0</version></project>")
                        .getBytes(StandardCharsets.UTF_8));
        try (Executor executor = new EmbeddedMavenExecutor(Paths.get(Environment.MAVEN3_HOME), true) {
            @Override
            protected String mavenArgsEnv() {
                return "-f sub/pom.xml";
            }
        }) {
            ExecutorResult result = executor.execute(request);
            assertEquals(
                    0,
                    result.exitCode().orElse(-1),
                    result.stdOutString().orElse("") + result.stdErrString().orElse(""));
        }
    }
}

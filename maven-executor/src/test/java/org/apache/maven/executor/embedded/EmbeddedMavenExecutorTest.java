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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.maven.executor.Environment;
import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.MavenExecutorTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Embedded executor UT
 */
public class EmbeddedMavenExecutorTest extends MavenExecutorTestSupport {

    @TempDir
    Path tempDir;

    @Override
    protected Executor doSelectExecutor(Path installationDirectory) {
        return new EmbeddedMavenExecutor(installationDirectory);
    }

    @Test
    void preservesSystemPropertiesSetBetweenExecutions() throws Exception {
        String propertyName = "maven.executor.test.property";
        String originalValue = System.getProperty(propertyName);
        EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(Paths.get(Environment.MAVEN4_HOME));
        try {
            Files.createDirectories(tempDir.resolve("home"));
            ExecutorRequest request = ExecutorRequest.mavenBuilder()
                    .cwd(tempDir)
                    .userHomeDirectory(tempDir.resolve("home"))
                    .argument("--version")
                    .build();

            System.clearProperty(propertyName);
            executor.execute(request);
            Properties properties = new Properties();
            properties.putAll(System.getProperties());
            properties.setProperty(propertyName, "set-between-executions");
            System.setProperties(properties);

            executor.execute(request);

            assertEquals("set-between-executions", System.getProperty(propertyName));
        } finally {
            executor.close();
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }
}

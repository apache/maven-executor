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
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.MavenExecutorTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void preservesCallerPropertiesWhenClosedAfterExecution() throws Exception {
        Files.createDirectories(tempDir.resolve("home"));
        String propertyName = "maven.executor.test.close.property";
        String originalValue = System.getProperty(propertyName);
        try {
            try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(Paths.get(Environment.MAVEN3_HOME))) {
                executor.execute(ExecutorRequest.mavenBuilder()
                        .cwd(tempDir)
                        .userHomeDirectory(tempDir.resolve("home"))
                        .argument("--version")
                        .build());
                System.setProperty(propertyName, "set-before-close");
            }
            assertEquals("set-before-close", System.getProperty(propertyName));
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }

    @Test
    void restoresCallerPropertiesWhenCloseFails() throws Exception {
        Files.createDirectories(tempDir.resolve("home"));
        String propertyName = "maven.executor.test.failing.close.property";
        String originalValue = System.getProperty(propertyName);
        try {
            try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(Paths.get(Environment.MAVEN3_HOME)) {
                @Override
                protected void doClose(Context context) throws ExecutorException {
                    super.doClose(context);
                    System.setProperty(propertyName, "changed-during-close");
                    throw new ExecutorException("expected close failure");
                }
            }) {
                executor.execute(ExecutorRequest.mavenBuilder()
                        .cwd(tempDir)
                        .userHomeDirectory(tempDir.resolve("home"))
                        .argument("--version")
                        .build());
                System.setProperty(propertyName, "set-before-close");
                assertThrows(ExecutorException.class, executor::close);
                assertEquals("set-before-close", System.getProperty(propertyName));
            }
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }

    @Test
    void preservesCallerPropertiesWhenDiscoveringVersion() throws Exception {
        String propertyName = "maven.executor.test.version.property";
        String originalValue = System.getProperty(propertyName);
        try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(Paths.get(Environment.MAVEN3_HOME))) {
            System.setProperty(propertyName, "set-before-version");
            assertEquals("3", executor.mavenVersion().substring(0, 1));
            assertEquals("set-before-version", System.getProperty(propertyName));
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }

    @Test
    void restoresCallerPropertiesWhenContextCreationFails() throws Exception {
        Path incompleteHome = tempDir.resolve("incomplete-maven");
        Files.createDirectories(incompleteHome.resolve("boot"));
        Files.createDirectories(incompleteHome.resolve("bin"));
        Files.createFile(incompleteHome.resolve("bin/m2.conf"));
        ClassLoader callerClassLoader = Thread.currentThread().getContextClassLoader();
        Properties callerProperties = new Properties();
        callerProperties.putAll(System.getProperties());
        String propertyName = "maven.executor.test.creation.property";
        String originalValue = System.getProperty(propertyName);
        try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(incompleteHome)) {
            ClassLoader changedClassLoader = new ClassLoader(callerClassLoader) {};
            Thread.currentThread().setContextClassLoader(changedClassLoader);
            System.setProperty(propertyName, "set-before-creation");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, executor::mavenVersion);
            assertEquals("Invalid Maven home directory; boot is empty", failure.getMessage());
            assertEquals("set-before-creation", System.getProperty(propertyName));
            assertSame(changedClassLoader, Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(callerClassLoader);
            System.setProperties(callerProperties);
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }
}

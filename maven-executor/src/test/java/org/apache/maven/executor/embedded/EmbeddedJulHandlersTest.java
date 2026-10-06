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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.apache.maven.executor.Environment;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maven 4 installs jul-to-slf4j's {@code SLF4JBridgeHandler} on the {@code java.util.logging} root logger of the JVM it
 * runs in. Embedded, that is the caller's JVM, and the handler's classes live in Maven's class realm: once the
 * executor closes that realm, every later {@code java.util.logging} record of the caller fails with
 * {@code NoClassDefFoundError: org/slf4j/spi/LocationAwareLogger}.
 */
@Timeout(120)
class EmbeddedJulHandlersTest {
    @TempDir
    Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"3", "4"})
    void rootLoggerHandlersAreRestored(String maven) throws Exception {
        Path home = Paths.get("3".equals(maven) ? Environment.MAVEN3_HOME : Environment.MAVEN4_HOME);
        Path cwd = Files.createDirectories(temp.resolve("cwd"));
        Logger root = Logger.getLogger("");
        List<Handler> before = Arrays.asList(root.getHandlers());

        try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(home)) {
            ExecutorResult result = executor.execute(ExecutorRequest.mavenBuilder()
                    .cwd(cwd)
                    .userHomeDirectory(temp)
                    .argument("-v")
                    .grabOutputAsString(true)
                    .build());
            assertTrue(
                    result.success(),
                    () -> result.stdOutString().orElse("")
                            + result.stdErrString().orElse(""));
            assertEquals(before, Arrays.asList(root.getHandlers()), "root handlers after an execution");
        }

        assertEquals(before, Arrays.asList(root.getHandlers()), "root handlers after close");
        // must not throw NoClassDefFoundError from a handler of the closed realm
        Logger.getLogger(EmbeddedJulHandlersTest.class.getName()).warning("logged after embedded Maven " + maven);
    }
}

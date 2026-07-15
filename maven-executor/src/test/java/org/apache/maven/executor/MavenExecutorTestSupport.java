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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Timeout(60)
public abstract class MavenExecutorTestSupport {
    @TempDir(cleanup = CleanupMode.NEVER)
    private static Path tempDir;

    private Path cwd;

    private Path userHome;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws Exception {
        cwd = tempDir.resolve(testInfo.getTestMethod()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .getName())
                .resolve("cwd");
        Files.createDirectories(cwd.resolve(".mvn"));
        userHome = tempDir.resolve(testInfo.getTestMethod()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .getName())
                .resolve("home");
        Files.createDirectories(userHome);

        System.out.println("=== "
                + testInfo.getTestMethod()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .getName());
    }

    private static final Map<Path, Executor> EXECUTORS = new ConcurrentHashMap<>();

    protected final Executor createAndMemoizeExecutor(Path installationDirectory) {
        return EXECUTORS.computeIfAbsent(installationDirectory, k -> doSelectExecutor(installationDirectory));
    }

    @AfterAll
    static void afterAll() {
        for (Executor executor : EXECUTORS.values()) {
            executor.close();
        }
        EXECUTORS.clear();
    }

    protected abstract Executor doSelectExecutor(Path installationDirectory);

    @Test
    void mvnenc4() throws Exception {
        String logfile = "m4.log";
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                cwd.resolve(logfile),
                Collections.singletonList(customizedRequest()
                        .command("mvnenc")
                        .cwd(cwd)
                        .userHomeDirectory(userHome)
                        .argument("diag")
                        .argument("-l")
                        .argument(logfile)
                        .build()));
        System.out.println(readString(cwd.resolve(logfile)));
    }

    @DisabledOnOs(
            value = WINDOWS,
            disabledReason = "JUnit on Windows fails to clean up as mvn3 does not close log file properly")
    @Test
    void dump3() throws Exception {
        String logfile = "m3.log";
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                cwd.resolve(logfile),
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .userHomeDirectory(userHome)
                        .argument("eu.maveniverse.maven.plugins:toolbox:" + Environment.TOOLBOX_VERSION + ":gav-dump")
                        .argument("-l")
                        .argument(logfile)
                        .build()));
        System.out.println(readString(cwd.resolve(logfile)));
    }

    @Test
    void dump4() throws Exception {
        String logfile = "m4.log";
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                cwd.resolve(logfile),
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .userHomeDirectory(userHome)
                        .argument("eu.maveniverse.maven.plugins:toolbox:" + Environment.TOOLBOX_VERSION + ":gav-dump")
                        .argument("-l")
                        .argument(logfile)
                        .build()));
        System.out.println(readString(cwd.resolve(logfile)));
    }

    @DisabledOnOs(
            value = WINDOWS,
            disabledReason = "JUnit on Windows fails to clean up as mvn3 does not close log file properly")
    @Test
    void defaultFs3() throws Exception {
        layDownFiles(cwd);
        String logfile = "m3.log";
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                cwd.resolve(logfile),
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("-l")
                        .argument(logfile)
                        .build()));
        System.out.println(readString(cwd.resolve(logfile)));
    }

    @Test
    void defaultFs4() throws Exception {
        layDownFiles(cwd);
        String logfile = "m4.log";
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                cwd.resolve(logfile),
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("-l")
                        .argument(logfile)
                        .build()));
        System.out.println(readString(cwd.resolve(logfile)));
    }

    @Test
    void version3() throws Exception {
        assertEquals(System.getProperty("maven3version"), mavenVersion(Paths.get(Environment.MAVEN3_HOME)));
    }

    @Test
    void version4() throws Exception {
        assertEquals(System.getProperty("maven4version"), mavenVersion(Paths.get(Environment.MAVEN4_HOME)));
    }

    @Test
    void defaultFs4CaptureOutput() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertFalse(stdout.toString().contains("[\u001B["), "By default no ANSI color codes");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void defaultFs4CaptureOutputWithForcedColor() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("--color=yes")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertTrue(stdout.toString().contains("[\u001B["), "No ANSI codes present");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void defaultFs4CaptureOutputWithForcedOffColor() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN4_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("--color=no")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertFalse(stdout.toString().contains("[\u001B["), "No ANSI codes present");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void defaultFs3CaptureOutput() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        // Note: we do not validate ANSI as Maven3 is weird in this respect (thinks is color but is not)
        // assertTrue(stdout.toString().contains("[\u001B["), "No ANSI codes present");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void defaultFs3CaptureOutputWithForcedColor() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("--color=yes")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertTrue(stdout.toString().contains("[\u001B["), "No ANSI codes present");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void defaultFs3CaptureOutputWithForcedOffColor() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("verify")
                        .argument("--color=no")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertFalse(stdout.toString().contains("[\u001B["), "No ANSI codes present");
        assertTrue(stdout.toString().contains("INFO"), "No INFO found");
    }

    @Test
    void fs3WithDollar() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("help:evaluate")
                        .argument("-Dexpression=foo")
                        .argument("-Dfoo=some-${bar}")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertTrue(stdout.toString().contains("some-null"));
    }

    @Disabled("mvn4 has a bug https://github.com/apache/maven/issues/10421 that prevents this test from passing")
    @Test
    void fs4WithDollar() throws Exception {
        layDownFiles(cwd);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        execute(
                Paths.get(Environment.MAVEN3_HOME),
                null,
                Collections.singletonList(customizedRequest()
                        .cwd(cwd)
                        .argument("-V")
                        .argument("help:evaluate")
                        .argument("-Dexpression=foo")
                        .argument("-Dfoo=${bar}")
                        .stdOut(stdout)
                        .build()));
        System.out.println(stdout);
        assertTrue(stdout.toString().contains("some-null"));
    }

    public static final String POM_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "                <project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "                         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/maven-v4_0_0.xsd\">\n"
            + "\n"
            + "                    <modelVersion>4.0.0</modelVersion>\n"
            + "\n"
            + "                    <groupId>org.apache.maven.samples</groupId>\n"
            + "                    <artifactId>sample</artifactId>\n"
            + "                    <version>1.0.0</version>\n"
            + "\n"
            + "                    <dependencyManagement>\n"
            + "                      <dependencies>\n"
            + "                        <dependency>\n"
            + "                          <groupId>org.junit</groupId>\n"
            + "                          <artifactId>junit-bom</artifactId>\n"
            + "                          <version>5.11.1</version>\n"
            + "                          <type>pom</type>\n"
            + "                          <scope>import</scope>\n"
            + "                        </dependency>\n"
            + "                      </dependencies>\n"
            + "                    </dependencyManagement>\n"
            + "\n"
            + "                    <dependencies>\n"
            + "                      <dependency>\n"
            + "                        <groupId>org.junit.jupiter</groupId>\n"
            + "                        <artifactId>junit-jupiter-api</artifactId>\n"
            + "                        <scope>test</scope>\n"
            + "                      </dependency>\n"
            + "                    </dependencies>\n"
            + "\n"
            + "                </project>";

    public static final String APP_JAVA_STRING = "            package org.apache.maven.samples.sample;\n" + "\n"
            + "            public class App {\n"
            + "                public static void main(String... args) {\n"
            + "                    System.out.println(\"Hello World!\");\n"
            + "                }\n"
            + "            }";

    protected void execute(Path installationDirectory, Path logFile, Collection<ExecutorRequest> requests)
            throws Exception {
        Executor invoker = createAndMemoizeExecutor(installationDirectory);
        for (ExecutorRequest request : requests) {
            ExecutorResult result = invoker.execute(request);
            int exitCode = result.exitCode().orElseThrow(() -> new NoSuchElementException("No such element"));
            if (exitCode != 0) {
                String stdout = result.stdOutString()
                        .map(s -> "=== STDOUT ===" + System.lineSeparator() + s)
                        .orElse("");
                stdout += result.stdErrString()
                        .map(s -> "=== STDERR ===" + System.lineSeparator() + s)
                        .orElse("");
                throw new FailedExecution(request, exitCode, readString(logFile) + stdout);
            }
        }
    }

    protected String mavenVersion(Path installationDirectory) throws Exception {
        return createAndMemoizeExecutor(installationDirectory).mavenVersion();
    }

    protected ExecutorRequest.Builder customizedRequest() {
        return customizedRequest(ExecutorRequest.mavenBuilder());
    }

    protected ExecutorRequest.Builder customizedRequest(ExecutorRequest.Builder builder) {
        builder = builder.cwd(cwd)
                .userHomeDirectory(userHome)
                .argument("-Daether.remoteRepositoryFilter.prefixes=false")
                .grabOutputAsString(true);
        if (System.getProperty("localRepository") != null) {
            builder.argument("-Dmaven.repo.local.tail=" + System.getProperty("localRepository"));
        }
        return builder;
    }

    protected void layDownFiles(Path cwd) throws IOException {
        Path pom = cwd.resolve("pom.xml").toAbsolutePath();
        writeString(pom, POM_STRING);
        Path appJava = cwd.resolve("src/main/java/org/apache/maven/samples/sample/App.java");
        Files.createDirectories(appJava.getParent());
        writeString(appJava, APP_JAVA_STRING);
    }

    protected static class FailedExecution extends Exception {
        private final ExecutorRequest request;
        private final int exitCode;
        private final String log;

        public FailedExecution(ExecutorRequest request, int exitCode, String log) {
            super(request.toString() + " => " + exitCode + "\n" + log);
            this.request = request;
            this.exitCode = exitCode;
            this.log = log;
        }

        public ExecutorRequest getRequest() {
            return request;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getLog() {
            return log;
        }
    }

    private static String readString(Path path) throws IOException {
        if (Files.exists(path)) {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } else {
            return "no such file: " + path;
        }
    }

    private static void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}

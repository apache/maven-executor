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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.executor.support.DefaultExecutorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultExecutorTool} without any Maven: the local repository lookup order and the default layout.
 */
class DefaultExecutorToolTest {
    @TempDir
    Path userHome;

    private Path cwd;

    private final DefaultExecutorTool tool = new DefaultExecutorTool();

    @BeforeEach
    void beforeEach() throws Exception {
        cwd = Files.createDirectories(userHome.resolve("cwd"));
    }

    private ExecutorRequest.Builder request() {
        return ExecutorRequest.mavenBuilder().userHomeDirectory(userHome).cwd(cwd);
    }

    private static String path(String path) {
        return path.replace('/', File.separatorChar);
    }

    @Test
    void localRepositoryDefaultsToUserHome() {
        assertEquals(userHome.resolve(".m2").resolve("repository").toString(), tool.localRepository(request()));
    }

    @Test
    void localRepositoryFromArgumentWinsAndLastOneCounts() throws Exception {
        writeUserSettings("<localRepository>/from/settings</localRepository>");
        Path first = userHome.resolve("first");
        Path second = userHome.resolve("second");
        assertEquals(
                second.toString(),
                tool.localRepository(request()
                        .argument("-Dmaven.repo.local=" + first)
                        .argument("-D")
                        .argument("maven.repo.local=" + second)));
    }

    @Test
    void relativeLocalRepositoryIsResolvedAgainstTheWorkingDirectory() {
        assertEquals(
                cwd.resolve("repo").toString(), tool.localRepository(request().argument("-Dmaven.repo.local=repo")));
    }

    @Test
    void localRepositoryFromMavenConfig() throws Exception {
        Path repo = userHome.resolve("from-maven-config");
        Files.createDirectories(cwd.resolve(".mvn"));
        Files.write(
                cwd.resolve(".mvn").resolve("maven.config"),
                Collections.singletonList("-B -Dmaven.repo.local=" + repo),
                StandardCharsets.UTF_8);
        assertEquals(repo.toString(), tool.localRepository(request()));
    }

    @Test
    void localRepositoryFromRequestSystemProperty() {
        Path repo = userHome.resolve("from-property");
        assertEquals(
                repo.toString(),
                tool.localRepository(request().jvmSystemProperty("maven.repo.local", repo.toString())));
    }

    @Test
    void localRepositoryFromUserSettingsWithUserHome() throws Exception {
        writeUserSettings("<localRepository>${user.home}/custom-repo</localRepository>");
        assertEquals(userHome.resolve("custom-repo").toString(), tool.localRepository(request()));
    }

    @Test
    void localRepositoryFromSettingsArgument() throws Exception {
        writeUserSettings("<localRepository>/from/default/settings</localRepository>");
        Path settings = cwd.resolve("it-settings.xml");
        Path repo = userHome.resolve("from-s");
        write(settings, "<localRepository>" + repo + "</localRepository>");
        assertEquals(
                repo.toString(), tool.localRepository(request().argument("-s").argument("it-settings.xml")));
    }

    @Test
    void localRepositoryFromGlobalSettingsOfTheInstallation() throws Exception {
        Path installation = userHome.resolve("maven");
        Path repo = userHome.resolve("from-global");
        write(installation.resolve("conf").resolve("settings.xml"), "<localRepository>" + repo + "</localRepository>");
        assertEquals(repo.toString(), new DefaultExecutorTool(installation).localRepository(request()));
        assertEquals(
                userHome.resolve(".m2").resolve("repository").toString(),
                tool.localRepository(request()),
                "no installation, no global settings");
    }

    @Test
    void artifactPath() {
        assertEquals(
                path("aopalliance/aopalliance/1.0/aopalliance-1.0.jar"),
                tool.artifactPath(request(), "aopalliance:aopalliance:1.0", null));
        assertEquals(
                path("org/apache/maven/maven-core/3.9.9/maven-core-3.9.9.pom"),
                tool.artifactPath(request(), "org.apache.maven:maven-core:pom:3.9.9", "central"));
        assertEquals(
                path("org/example/lib/1.0/lib-1.0-tests.jar"),
                tool.artifactPath(request(), "org.example:lib:jar:tests:1.0", null));
        assertEquals(
                path("org/example/lib/1.0/lib-1.0.jar"),
                tool.artifactPath(request(), "org.example:lib::1.0", null),
                "an empty extension is jar");
    }

    @Test
    void artifactPathOfATimestampedSnapshot() {
        assertEquals(
                path("org/example/lib/1.0-SNAPSHOT/lib-1.0-20260101.120000-3.jar"),
                tool.artifactPath(request(), "org.example:lib:1.0-20260101.120000-3", "central"));
        assertEquals(
                path("org/example/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT.jar"),
                tool.artifactPath(request(), "org.example:lib:1.0-20260101.120000-3", null),
                "an installed snapshot is named after the base version");
        assertEquals(
                path("org/example/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT.jar"),
                tool.artifactPath(request(), "org.example:lib:1.0-SNAPSHOT", null));
    }

    @Test
    void metadataPath() {
        assertEquals(path("maven-metadata-local.xml"), tool.metadataPath(request(), ":::", null));
        assertEquals(
                path("aopalliance/maven-metadata-someremote.xml"),
                tool.metadataPath(request(), "aopalliance", "someremote"));
        assertEquals(
                path("org/example/maven-metadata-local.xml"), tool.metadataPath(request(), "org.example:::", null));
        assertEquals(
                path("org/example/lib/maven-metadata-central.xml"),
                tool.metadataPath(request(), "org.example:lib::", "central"));
        assertEquals(
                path("org/example/lib/1.0-SNAPSHOT/maven-metadata-local.xml"),
                tool.metadataPath(request(), "org.example:lib:1.0-SNAPSHOT:", null));
        assertEquals(
                path("org/example/resolver-status-local.properties"),
                tool.metadataPath(request(), "org.example:::resolver-status.properties", null));
    }

    @Test
    void badCoordinates() {
        assertThrows(ExecutorException.class, () -> tool.artifactPath(request(), "org.example:lib", null));
        assertThrows(ExecutorException.class, () -> tool.artifactPath(request(), "org.example::1.0", null));
        assertThrows(ExecutorException.class, () -> tool.metadataPath(request(), "a:b:c:d:e", null));
        assertThrows(ExecutorException.class, () -> tool.dump(request()));
    }

    private void writeUserSettings(String content) throws Exception {
        write(userHome.resolve(".m2").resolve("settings.xml"), content);
    }

    private static void write(Path settings, String content) throws Exception {
        Files.createDirectories(settings.getParent());
        Files.write(
                settings,
                Collections.singletonList(
                        "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\">" + content + "</settings>"),
                StandardCharsets.UTF_8);
    }
}

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
package org.apache.maven.executor.verifier;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.executor.ExecutorHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks and paths of {@link Verifier}, without running Maven.
 */
class VerifierTest {
    @TempDir
    Path basedir;

    private Path repo;

    private ExecutorHelper helper;

    private Verifier verifier;

    @BeforeEach
    void beforeEach() {
        repo = basedir.resolve("repo");
        helper = ExecutorHelper.forMavenInstallation(
                Paths.get(System.getProperty("maven3home")), ExecutorHelper.Mode.FORKED);
        verifier = new Verifier(basedir.toString(), helper);
        verifier.setLocalRepo(repo.toString());
    }

    @AfterEach
    void afterEach() {
        helper.close();
    }

    @Test
    void addCliOptionSplitsAndAddCliArgumentDoesNot() {
        verifier.addCliOption(" -pl module-app  -am ");
        verifier.addCliArgument("-Dname=a value");
        assertEquals(Arrays.asList("-pl", "module-app", "-am", "-Dname=a value"), verifier.getCliArguments());
    }

    @Test
    void localRepositoryFromArgumentsAndSystemProperties() {
        Verifier fresh = new Verifier(basedir.toString(), helper);
        Path fromArgument = basedir.resolve("from-argument");
        fresh.addCliArgument("-Dmaven.repo.local=" + fromArgument);
        assertEquals(fromArgument.toString(), fresh.getLocalRepository());

        Verifier withProperty = new Verifier(basedir.toString(), helper);
        Path fromProperty = basedir.resolve("from-property");
        withProperty.setSystemProperty("maven.repo.local", fromProperty.toString());
        assertEquals(fromProperty.toString(), withProperty.getLocalRepository());

        assertEquals(repo.toString(), verifier.getLocalRepository(), "setLocalRepo wins");
    }

    @Test
    void artifactPaths() {
        assertEquals(
                path("org/example/lib/1.0/lib-1.0.jar"), verifier.getArtifactPath("org.example", "lib", "1.0", "jar"));
        assertEquals(
                path("org/example/plugin/1.0/plugin-1.0.jar"),
                verifier.getArtifactPath("org.example", "plugin", "1.0", "maven-plugin"));
        assertEquals(
                path("org/example/lib/1.0/lib-1.0-tests.jar"),
                verifier.getArtifactPath("org.example", "lib", "1.0", "test-jar"));
        assertEquals(
                path("org/example/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT-bin.zip"),
                verifier.getArtifactPath("org.example", "lib", "1.0-SNAPSHOT", "zip", "bin"));
        assertEquals(
                path("org/example/lib/maven-metadata-local.xml"),
                verifier.getArtifactMetadataPath("org.example", "lib"));
        assertEquals(
                path("org/example/maven-metadata-central.xml"),
                verifier.getArtifactMetadataPath("org.example", null, null, "maven-metadata-central.xml"));
    }

    @Test
    void suiteTypesThroughOverride() {
        Verifier coreIts = new Verifier(basedir.toString(), helper) {
            @Override
            protected String[] extensionAndClassifier(String type) {
                return "coreit-artifact".equals(type) ? new String[] {"jar", "it"} : super.extensionAndClassifier(type);
            }
        };
        coreIts.setLocalRepo(repo.toString());
        assertEquals(
                path("org/example/lib/1.0/lib-1.0-it.jar"),
                coreIts.getArtifactPath("org.example", "lib", "1.0", "coreit-artifact"));
    }

    @Test
    void artifactChecksAndDeletion() throws Exception {
        assertThrows(
                VerificationException.class, () -> verifier.verifyArtifactPresent("org.example", "lib", "1.0", "jar"));
        write(verifier.getArtifactPath("org.example", "lib", "1.0", "jar"), "content");
        write(verifier.getArtifactMetadataPath("org.example", "lib"), "<metadata/>");
        verifier.verifyArtifactPresent("org.example", "lib", "1.0", "jar");
        verifier.verifyArtifactContent("org.example", "lib", "1.0", "jar", "content");
        assertEquals(
                2,
                verifier.getArtifactFileNameList("org.example", "lib", "1.0", "jar")
                        .size());

        verifier.deleteArtifact("org.example", "lib", "1.0", "jar");
        verifier.verifyArtifactNotPresent("org.example", "lib", "1.0", "jar");
        assertFalse(Files.exists(Paths.get(verifier.getArtifactMetadataPath("org.example", "lib"))));

        write(verifier.getArtifactPath("org.example", "other", "2.0", "pom"), "");
        verifier.deleteArtifacts("org.example");
        assertFalse(Files.exists(repo.resolve("org").resolve("example")));
    }

    @Test
    void logChecks() throws Exception {
        write(
                basedir.resolve("log.txt").toString(),
                "[INFO] \u001B[1mBUILD SUCCESS\u001B[m\n"
                        + "[ERROR] VM_global_library.vm not found\n"
                        + "[ERROR] VM #displayTree: error : too few arguments to macro\n");
        verifier.verifyTextInLog("[INFO] BUILD SUCCESS");
        verifier.verifyTextNotInLog("BUILD FAILURE");
        assertThrows(VerificationException.class, () -> verifier.verifyTextNotInLog("BUILD SUCCESS"));
        assertThrows(VerificationException.class, () -> verifier.verifyTextInLog("BUILD FAILURE"));
        verifier.verifyErrorFreeLog();

        verifier.setLogFileName("other.txt");
        write(basedir.resolve("other.txt").toString(), "[ERROR] Failed to execute goal");
        assertThrows(VerificationException.class, verifier::verifyErrorFreeLog);
    }

    @Test
    void fileChecks() throws Exception {
        write(basedir.resolve("target/app-1.0.jar").toString(), "");
        verifier.verifyFilePresent("target/app-1.0.jar");
        verifier.verifyFilePresent("target/app-*.jar");
        verifier.verifyFileNotPresent("target/app-*.war");
        verifier.verifyFileNotPresent(basedir.resolve("missing.txt").toString());
        assertThrows(VerificationException.class, () -> verifier.verifyFilePresent("target/missing.jar"));

        try (OutputStream out = Files.newOutputStream(basedir.resolve("target/entries.jar"));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.closeEntry();
        }
        verifier.verifyFilePresent("target/entries.jar!/META-INF/MANIFEST.MF");
        verifier.verifyFileNotPresent("target/entries.jar!/missing.txt");
    }

    @Test
    void filterFileAndLoad() throws Exception {
        write(basedir.resolve("src.txt").toString(), "dir=@basedir@\nurl=@baseurl@\nkey=@key@\n");
        Map<String, String> filterMap = verifier.newDefaultFilterMap();
        filterMap.put("@key@", "value");
        File filtered = verifier.filterFile("src.txt", "out/filtered.properties", "UTF-8", filterMap);
        assertEquals(basedir.resolve("out").resolve("filtered.properties").toFile(), filtered);
        assertEquals("value", verifier.loadProperties("out/filtered.properties").getProperty("key"));
        assertEquals(
                basedir.toAbsolutePath().toString(),
                verifier.loadProperties("out/filtered.properties").getProperty("dir"));
        assertEquals(3, verifier.loadLines("src.txt", "UTF-8").size());
    }

    @Test
    void loadFileReplacesArtifactMarkers() throws Exception {
        write(verifier.getArtifactMetadataPath("org.example", "lib", "1.0"), "");
        write(
                basedir.resolve("expected.txt").toString(),
                "# comment\n\n  ${artifact:org.example:lib:1.0:jar}\nplain\n");
        assertTrue(verifier.loadFile(basedir.toString(), "missing.txt", false).isEmpty());
        List<String> lines = verifier.loadFile(basedir.toString(), "expected.txt", false);
        assertEquals(
                Arrays.asList(
                        verifier.getArtifactPath("org.example", "lib", "1.0", "jar"),
                        verifier.getArtifactMetadataPath("org.example", "lib", "1.0"),
                        "plain"),
                lines);
    }

    private String path(String relative) {
        return repo.resolve(relative.replace('/', File.separatorChar)).toString();
    }

    private static void write(String file, String content) throws Exception {
        Path path = Paths.get(file);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}

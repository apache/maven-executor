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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.maven.executor.ExecutorHelper;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Verifier} running Maven 3 and Maven 4 on the test projects. Embedded Maven 4 is left out: it hangs instead of
 * failing on a failed build (E1 in apache/maven-executor#49).
 */
@Timeout(300)
class VerifierExecuteTest {
    private static final String GROUP_ID = "org.apache.maven.executor.verifier.its";

    @TempDir
    Path temp;

    static Stream<Arguments> mavens() {
        return Stream.of(
                Arguments.of("3", ExecutorHelper.Mode.FORKED),
                Arguments.of("3", ExecutorHelper.Mode.EMBEDDED),
                Arguments.of("4", ExecutorHelper.Mode.FORKED));
    }

    private Verifier verifier(String maven, ExecutorHelper.Mode mode, ExecutorHelper helper, String project)
            throws Exception {
        File dir = ResourceExtractor.extractResourcePath(getClass(), "/projects/" + project, temp.toFile(), true);
        Verifier verifier = new Verifier(dir.getAbsolutePath(), helper);
        verifier.setLocalRepo(temp.resolve("repo").toString());
        verifier.addCliArgument("-Daether.remoteRepositoryFilter.prefixes=false");
        if (System.getProperty("localRepository") != null) {
            verifier.setSystemProperty("maven.repo.local.tail", System.getProperty("localRepository"));
        }
        return verifier;
    }

    private static ExecutorHelper helper(String maven, ExecutorHelper.Mode mode) {
        return ExecutorHelper.forMavenInstallation(
                Paths.get(System.getProperty("3".equals(maven) ? "maven3home" : "maven4home")), mode);
    }

    @ParameterizedTest
    @MethodSource("mavens")
    void installsAndChecksTheLocalRepository(String maven, ExecutorHelper.Mode mode) throws Exception {
        try (ExecutorHelper helper = helper(maven, mode)) {
            Verifier verifier = verifier(maven, mode, helper, "simple");
            assertTrue(verifier.getMavenVersion().startsWith(maven + "."), verifier.getMavenVersion());
            verifier.addCliArgument("install");
            verifier.execute();

            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("BUILD SUCCESS");
            verifier.verifyFilePresent("log.txt");
            verifier.verifyArtifactPresent(GROUP_ID, "simple", "1.0", "pom");
            verifier.verifyArtifactNotPresent(GROUP_ID, "simple", "1.0", "jar");

            verifier.deleteArtifacts(GROUP_ID);
            verifier.verifyArtifactNotPresent(GROUP_ID, "simple", "1.0", "pom");
        }
    }

    @ParameterizedTest
    @MethodSource("mavens")
    void passesAnOptionAsSeparateArguments(String maven, ExecutorHelper.Mode mode) throws Exception {
        try (ExecutorHelper helper = helper(maven, mode)) {
            Verifier verifier = verifier(maven, mode, helper, "reactor");
            verifier.setAutoclean(false);
            verifier.addCliOption("-pl b -am validate");
            verifier.execute();

            verifier.verifyErrorFreeLog();
            verifier.verifyTextInLog("Building a 1.0");
            verifier.verifyTextInLog("Building b 1.0");
            verifier.verifyTextNotInLog("Building c 1.0");
        }
    }

    @ParameterizedTest
    @MethodSource("mavens")
    void failedBuildThrowsAndKeepsTheLog(String maven, ExecutorHelper.Mode mode) throws Exception {
        try (ExecutorHelper helper = helper(maven, mode)) {
            Verifier verifier = verifier(maven, mode, helper, "failing");
            verifier.setAutoclean(false);
            verifier.setLogFileName("failing-log.txt");
            verifier.addCliArgument("validate");

            VerificationException failure = assertThrows(VerificationException.class, verifier::execute);
            assertTrue(failure.getMessage().contains("Exit code was non-zero"), failure.getMessage());

            verifier.verifyFilePresent("failing-log.txt");
            verifier.verifyTextInLog("no-such-packaging");
            assertThrows(VerificationException.class, verifier::verifyErrorFreeLog);
        }
    }
}

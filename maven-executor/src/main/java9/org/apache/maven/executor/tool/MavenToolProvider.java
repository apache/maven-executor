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
package org.apache.maven.executor.tool;

import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.spi.ToolProvider;

/**
 * A tool provider providing Maven. The "maven home" must be discoverable either via the "maven.home" system property
 * or the "MAVEN_HOME" environment variable.
 */
public class MavenToolProvider implements ToolProvider {
    @Override
    public String name() {
        return "maven-executor";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome == null) {
            mavenHome = System.getenv("MAVEN_HOME");
        }
        if (mavenHome == null) {
            throw new IllegalStateException("Cannot determine maven.home system property");
        }
        Path mavenHomePath = ExecutorRequest.getCanonicalPath(Paths.get(mavenHome));
        ExecutorHelper.Mode mode = ExecutorHelper.Mode.valueOf(
                System.getProperty("maven.executor.mode", ExecutorHelper.Mode.AUTO.name()));
        try (ExecutorHelper helper = ExecutorHelper.forMavenInstallation(mavenHomePath, mode)) {
            ExecutorResult result = helper.execute(ExecutorRequest.mavenBuilder().arguments(args).build());
            if (!result.stdOutString().orElse("").trim().isEmpty()) {
                out.println(result.stdOutString().orElse(""));
            }
            if (!result.stdErrString().orElse("").trim().isEmpty()) {
                err.println(result.stdErrString().orElse(""));
            }
            return result.exitCode().orElse(result.success() ? 0 : 1);
        }
    }
}

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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.spi.ToolProvider;

/**
 * A ToolProvider providing Maven via {@code maven-executor}. It needs to be pointed at existing Maven "installation directory"
 * and supports two modes of executions: embedded (in JVM, in isolated classloader) and forked (forks a subprocess to
 * run Maven). Former is faster, but latter provides full control (presence of JVM arguments or env variables implies
 * forked execution).
 * The Maven installation directory must be discoverable. The Maven home is discovered in order of precedence:
 * <ul>
 *   <li>Specified by {@code -MEX-mavenHome} MEX argument, if present</li>
 *   <li>If it does not exist, the {@code maven.executor.home} Java System Property</li>
 *   <li>If it does not exist, the {@code maven.home} Java System Property</li>
 *   <li>If it does not exist, the {@code MAVEN_HOME"} environment variable</li>
 *   <li>If it does not exist, error code {@code -100} is returned to signal no Maven home could be discovered</li>
 * </ul>
 * Other configurations available:
 * <ul>
 *   <li>The {@code maven.executor.mode} Java System Property; defines {@link org.apache.maven.executor.ExecutorHelper.Mode}, defaults to {@code AUTO}</li>
 * </ul>
 * Supported MEX expressions in arguments (they are removed from rest of arguments, they can be mixed with Maven options):
 * <ul>
 *     <li>{@code -MEX-mavenHome=/maven/installation} the Maven installation directory, to use</li>
 *     <li>{@code -MEX-cmd=mvnup} the command to execute, if it is not {@code mvn} (the default)</li>
 *     <li>{@code -MEX-cwd=/some/path} the CWD to run the execution (the default current CWD as reported by Java)</li>
 *     <li>{@code -MEX-jsp=key=value} to set Jave System Property (multiple arguments may be present)</li>
 *     <li>{@code -MEX-env=ENV_NAME=value} to set environment variable; implies forked execution (multiple arguments may be present)</li>
 *     <li>{@code -MEX-jvm=value} to set JVM arguments; implies forked execution (multiple arguments may be present)</li>
 * </ul>
 * If any other {@code -MEX-} prefixed expression is found in arguments, that is not in the table above, error code
 * {@code -200} is returned.
 */
public class MavenToolProvider implements ToolProvider {
    private static final String MEX_PREFIX = "-MEX-";

    @Override
    public String name() {
        return "maven-executor";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        ArrayList<String> allArguments = new ArrayList<>(Arrays.asList(args));

        String mavenHome = extractMTPSingleArgument(allArguments, "mavenHome").orElse(null);
        String cmd = extractMTPSingleArgument(allArguments, "cmd").orElse(null);
        String cwd = extractMTPSingleArgument(allArguments, "cwd").orElse(null);
        Map<String, String> environmentVariables = extractMTPMapArgument(allArguments, "env").orElse(null);
        Map<String, String> jvmSystemProperties = extractMTPMapArgument(allArguments, "jsp").orElse(null);
        Map<String, String> jvm = extractMTPMapArgument(allArguments, "jvm").orElse(Map.of());
        List<String> jvmArguments = jvm.isEmpty() ? null : new ArrayList<>(jvm.keySet());

        if (allArguments.stream().anyMatch(s -> s.startsWith(MEX_PREFIX))) {
            err.println("unknown " + MEX_PREFIX + " argument found");
            return -200;
        }

        if (mavenHome == null) {
            mavenHome = System.getProperty("maven.executor.home");
        }
        if (mavenHome == null) {
            mavenHome = System.getProperty("maven.home");
        }
        if (mavenHome == null) {
            mavenHome = System.getenv("MAVEN_HOME");
        }
        if (mavenHome == null) {
            err.println("maven.home not found");
            return -100;
        }

        ExecutorHelper.Mode mode = ExecutorHelper.Mode.valueOf(
                System.getProperty("maven.executor.mode", ExecutorHelper.Mode.AUTO.name()));
        try (ExecutorHelper helper = ExecutorHelper.forMavenInstallation(ExecutorRequest.getCanonicalPath(Paths.get(mavenHome)), mode)) {
            ExecutorRequest.Builder builder = ExecutorRequest.mavenBuilder();
            if (cmd != null) {
                builder = builder.command(cmd);
            }
            builder = builder.arguments(allArguments);
            if (cwd != null) {
                builder = builder.cwd(Paths.get(cwd));
            }
            if (environmentVariables != null) {
                builder = builder.environmentVariables(environmentVariables);
            }
            if (jvmSystemProperties != null) {
                builder = builder.jvmSystemProperties(jvmSystemProperties);
            }
            if (jvmArguments != null) {
                builder = builder.jvmArguments(jvmArguments);
            }
            ExecutorResult result = helper.execute(builder.build());
            if (!result.stdOutString().orElse("").trim().isEmpty()) {
                out.println(result.stdOutString().orElse(""));
            }
            if (!result.stdErrString().orElse("").trim().isEmpty()) {
                err.println(result.stdErrString().orElse(""));
            }
            return result.exitCode().orElse(result.success() ? 0 : 1);
        }
    }

    private Optional<String> extractMTPSingleArgument(List<String> allArguments, String name) {
        String key = "-MEX-" + name;
        Optional<String> mtpArgument = allArguments.stream().filter(a -> a.startsWith(key)).findFirst();
        if (mtpArgument.isPresent()) {
            String argument = mtpArgument.get();
            while (allArguments.remove(argument)) {
                // all occurrences removed; first wins
            }
            return Optional.of(argument.substring(key.length() + 1));
        }
        return Optional.empty();
    }

    private Optional<Map<String, String>> extractMTPMapArgument(List<String> allArguments, String name) {
        String key = "-MEX-" + name;
        Map<String, String> result = new HashMap<>();
        Iterator<String> li = allArguments.listIterator();
        while (li.hasNext()) {
            String argument = li.next();
            if (argument.startsWith(key)) {
                li.remove();
                String arg = argument.substring(key.length() + 1);
                if (arg.contains("=")) {
                    result.put(arg.substring(0, arg.indexOf("=")), arg.substring(arg.indexOf("=") + 1));
                } else {
                    result.put(arg, null);
                }
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }
}

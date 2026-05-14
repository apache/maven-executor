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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Represents a request to execute Maven with command-line arguments.
 * This interface encapsulates all the necessary information needed to execute
 * Maven command with arguments. The arguments are not parsed, they are just passed over
 * to executed tool.
 */
public interface ExecutorRequest {
    /**
     * The Maven command.
     */
    String MVN = "mvn";

    /**
     * The command to execute, ie "mvn".
     */
    String command();

    /**
     * The immutable list of arguments to pass to the command.
     */
    List<String> arguments();

    /**
     * Returns the current working directory for the Maven execution.
     * This is typically the directory from which Maven was invoked.
     *
     * @return the current working directory path
     */
    Path cwd();

    /**
     * Returns the user's home directory.
     * This is typically obtained from the "user.home" system property.
     *
     * @return the user's home directory path
     */
    Path userHomeDirectory();

    /**
     * Returns the map of Java System Properties to set before executing process.
     *
     * @return an Optional containing the map of Java System Properties, or empty if not specified
     */
    Optional<Map<String, String>> jvmSystemProperties();

    /**
     * Returns the map of environment variables to set before executing process.
     * This property is used ONLY by executors that spawn a new JVM.
     *
     * @return an Optional containing the map of environment variables, or empty if not specified
     */
    Optional<Map<String, String>> environmentVariables();

    /**
     * Returns the list of extra JVM arguments to be passed to the forked process.
     * These arguments allow for customization of the JVM environment in which tool will run.
     * This property is used ONLY by executors that spawn a new JVM.
     *
     * @return an Optional containing the list of extra JVM arguments, or empty if not specified
     */
    Optional<List<String>> jvmArguments();

    /**
     * Whether execution outputs (STDOUT and STDERR) should be captured as plain {@link String}.
     * By default, this is {@code true}, unless client manually sets any of {@link #stdOut()} or {@link #stdErr()}
     * streams, in which case this value is set to {@code false} and caller must handle these streams manually.
     */
    boolean grabOutputAsString();

    /**
     * Optional provider for STD in of the Maven. If given, this provider will be piped into std input of
     * Maven. The stream is closed once tool execution is finished.
     *
     * @return an Optional containing the stdin provider, or empty if not specified.
     */
    Optional<InputStream> stdIn();

    /**
     * Optional consumer for STD out of the Maven. If given, this consumer will get all output from the std out of
     * Maven. Note: whether consumer gets to consume anything depends on invocation arguments passed in
     * {@link #arguments()}, as if log file is set, not much will go to stdout.
     * The stream is closed once tool execution is finished.
     *
     * @return an Optional containing the stdout consumer, or empty if not specified.
     */
    Optional<OutputStream> stdOut();

    /**
     * Optional consumer for STD err of the Maven. If given, this consumer will get all output from the std err of
     * Maven. Note: whether consumer gets to consume anything depends on invocation arguments passed in
     * {@link #arguments()}, as if log file is set, not much will go to stderr.
     *  The stream is closed once tool execution is finished.
     *
     * @return an Optional containing the stderr consumer, or empty if not specified.
     */
    Optional<OutputStream> stdErr();

    /**
     * Indicate if {@code ~/.mavenrc} should be skipped during execution.
     * <p>
     * Affected only for forked executor by adding MAVEN_SKIP_RC environment variable
     */
    boolean skipMavenRc();

    /**
     * The optional execution time limit. If set, and execution does not finish within the given time, it is considered
     * failed and killed. If not set, no time limit is applied. Depending on implementation, the timeout detection may
     * be imprecise.
     */
    Optional<Duration> executionTimeout();

    /**
     * Returns {@link Builder} created from this instance.
     */
    default Builder toBuilder() {
        return new Builder(
                command(),
                arguments(),
                cwd(),
                userHomeDirectory(),
                jvmSystemProperties().orElse(null),
                environmentVariables().orElse(null),
                jvmArguments().orElse(null),
                grabOutputAsString(),
                stdIn().orElse(null),
                stdOut().orElse(null),
                stdErr().orElse(null),
                skipMavenRc(),
                executionTimeout().orElse(null));
    }

    /**
     * Returns new builder pre-set to run Maven. The discovery of maven home is attempted, user cwd and home are
     * also discovered by standard means.
     */
    static Builder mavenBuilder() {
        return new Builder(
                MVN,
                null,
                getCanonicalPath(Paths.get(System.getProperty("user.dir"))),
                getCanonicalPath(Paths.get(System.getProperty("user.home"))),
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                false,
                null);
    }

    class Builder {
        private String command;
        private List<String> arguments;
        private Path cwd;
        private Path userHomeDirectory;
        private Map<String, String> jvmSystemProperties;
        private Map<String, String> environmentVariables;
        private List<String> jvmArguments;
        private boolean grabOutputAsString;
        private InputStream stdIn;
        private OutputStream stdOut;
        private OutputStream stdErr;
        private boolean skipMavenRc;
        private Duration executionTimeout;

        private Builder() {}

        @SuppressWarnings("ParameterNumber")
        private Builder(
                String command,
                List<String> arguments,
                Path cwd,
                Path userHomeDirectory,
                Map<String, String> jvmSystemProperties,
                Map<String, String> environmentVariables,
                List<String> jvmArguments,
                boolean grabOutputAsString,
                InputStream stdIn,
                OutputStream stdOut,
                OutputStream stdErr,
                boolean skipMavenRc,
                Duration executionTimeout) {
            this.command = command;
            this.arguments = arguments;
            this.cwd = cwd;
            this.userHomeDirectory = userHomeDirectory;
            this.jvmSystemProperties = jvmSystemProperties;
            this.environmentVariables = environmentVariables;
            this.jvmArguments = jvmArguments;
            this.grabOutputAsString = grabOutputAsString;
            this.stdIn = stdIn;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
            this.skipMavenRc = skipMavenRc;
            this.executionTimeout = executionTimeout;
        }

        public Builder command(String command) {
            this.command = requireNonNull(command, "command");
            return this;
        }

        public Builder arguments(String... arguments) {
            return arguments(Arrays.asList(arguments));
        }

        public Builder arguments(List<String> arguments) {
            this.arguments = requireNonNull(arguments, "arguments");
            return this;
        }

        public Builder argument(String argument) {
            if (arguments == null) {
                arguments = new ArrayList<>();
            }
            this.arguments.add(requireNonNull(argument, "argument"));
            return this;
        }

        public Builder cwd(Path cwd) {
            this.cwd = getCanonicalPath(requireNonNull(cwd, "cwd"));
            return this;
        }

        public Builder userHomeDirectory(Path userHomeDirectory) {
            this.userHomeDirectory = getCanonicalPath(requireNonNull(userHomeDirectory, "userHomeDirectory"));
            return this;
        }

        public Builder jvmSystemProperties(Map<String, String> jvmSystemProperties) {
            this.jvmSystemProperties = jvmSystemProperties;
            return this;
        }

        public Builder jvmSystemProperty(String key, String value) {
            requireNonNull(key, "env key");
            requireNonNull(value, "env value");
            if (jvmSystemProperties == null) {
                this.jvmSystemProperties = new HashMap<>();
            }
            this.jvmSystemProperties.put(key, value);
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder environmentVariable(String key, String value) {
            requireNonNull(key, "env key");
            requireNonNull(value, "env value");
            if (environmentVariables == null) {
                this.environmentVariables = new HashMap<>();
            }
            this.environmentVariables.put(key, value);
            return this;
        }

        public Builder jvmArguments(List<String> jvmArguments) {
            this.jvmArguments = jvmArguments;
            return this;
        }

        public Builder jvmArgument(String jvmArgument) {
            if (jvmArguments == null) {
                jvmArguments = new ArrayList<>();
            }
            this.jvmArguments.add(requireNonNull(jvmArgument, "jvmArgument"));
            return this;
        }

        public Builder grabOutputAsString(boolean grabOutputAsString) {
            this.grabOutputAsString = grabOutputAsString;
            if (grabOutputAsString) {
                this.stdOut = null;
                this.stdErr = null;
            }
            return this;
        }

        public Builder stdIn(InputStream stdIn) {
            this.stdIn = stdIn;
            return this;
        }

        public Builder stdOut(OutputStream stdOut) {
            this.grabOutputAsString = false;
            this.stdOut = stdOut;
            return this;
        }

        public Builder stdErr(OutputStream stdErr) {
            this.grabOutputAsString = false;
            this.stdErr = stdErr;
            return this;
        }

        public Builder skipMavenRc(boolean skipMavenRc) {
            this.skipMavenRc = skipMavenRc;
            return this;
        }

        public Builder executionTimeout(Duration executionTimeout) {
            this.executionTimeout = executionTimeout;
            return this;
        }

        public ExecutorRequest build() {
            return new Impl(
                    command,
                    arguments,
                    cwd,
                    userHomeDirectory,
                    jvmSystemProperties,
                    environmentVariables,
                    jvmArguments,
                    grabOutputAsString,
                    stdIn,
                    stdOut,
                    stdErr,
                    skipMavenRc,
                    executionTimeout);
        }

        private static class Impl implements ExecutorRequest {
            private final String command;
            private final List<String> arguments;
            private final Path cwd;
            private final Path userHomeDirectory;
            private final Map<String, String> jvmSystemProperties;
            private final Map<String, String> environmentVariables;
            private final List<String> jvmArguments;
            private final boolean grabOutputAsString;
            private final InputStream stdIn;
            private final OutputStream stdOut;
            private final OutputStream stdErr;
            private final boolean skipMavenRc;
            private final Duration executionTimeout;

            @SuppressWarnings("ParameterNumber")
            private Impl(
                    String command,
                    List<String> arguments,
                    Path cwd,
                    Path userHomeDirectory,
                    Map<String, String> jvmSystemProperties,
                    Map<String, String> environmentVariables,
                    List<String> jvmArguments,
                    boolean grabOutputAsString,
                    InputStream stdIn,
                    OutputStream stdOut,
                    OutputStream stdErr,
                    boolean skipMavenRc,
                    Duration executionTimeout) {
                this.command = requireNonNull(command);
                this.arguments = arguments == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(arguments));
                this.cwd = getCanonicalPath(requireNonNull(cwd));
                this.userHomeDirectory = getCanonicalPath(requireNonNull(userHomeDirectory));
                this.jvmSystemProperties = jvmSystemProperties != null && !jvmSystemProperties.isEmpty()
                        ? Collections.unmodifiableMap(new HashMap<>(jvmSystemProperties))
                        : null;
                this.environmentVariables = environmentVariables != null && !environmentVariables.isEmpty()
                        ? Collections.unmodifiableMap(new HashMap<>(environmentVariables))
                        : null;
                this.jvmArguments = jvmArguments != null && !jvmArguments.isEmpty()
                        ? Collections.unmodifiableList(new ArrayList<>(jvmArguments))
                        : null;
                this.grabOutputAsString = grabOutputAsString;
                this.stdIn = stdIn;
                this.stdOut = stdOut;
                this.stdErr = stdErr;
                this.skipMavenRc = skipMavenRc;
                this.executionTimeout = executionTimeout;
            }

            @Override
            public String command() {
                return command;
            }

            @Override
            public List<String> arguments() {
                return arguments;
            }

            @Override
            public Path cwd() {
                return cwd;
            }

            @Override
            public Path userHomeDirectory() {
                return userHomeDirectory;
            }

            @Override
            public Optional<Map<String, String>> jvmSystemProperties() {
                return Optional.ofNullable(jvmSystemProperties);
            }

            @Override
            public Optional<Map<String, String>> environmentVariables() {
                return Optional.ofNullable(environmentVariables);
            }

            @Override
            public Optional<List<String>> jvmArguments() {
                return Optional.ofNullable(jvmArguments);
            }

            @Override
            public boolean grabOutputAsString() {
                return grabOutputAsString;
            }

            @Override
            public Optional<InputStream> stdIn() {
                return Optional.ofNullable(stdIn);
            }

            @Override
            public Optional<OutputStream> stdOut() {
                return Optional.ofNullable(stdOut);
            }

            @Override
            public Optional<OutputStream> stdErr() {
                return Optional.ofNullable(stdErr);
            }

            @Override
            public boolean skipMavenRc() {
                return skipMavenRc;
            }

            @Override
            public Optional<Duration> executionTimeout() {
                return Optional.ofNullable(executionTimeout);
            }

            @Override
            public String toString() {
                return "Impl{" + "command='"
                        + command + '\'' + ", arguments="
                        + arguments + ", cwd="
                        + cwd + ", userHomeDirectory="
                        + userHomeDirectory + ", jvmSystemProperties="
                        + jvmSystemProperties + ", environmentVariables="
                        + environmentVariables + ", jvmArguments="
                        + jvmArguments + ", grabOutputAsString="
                        + grabOutputAsString + ", stdIn="
                        + stdIn + ", stdOut="
                        + stdOut + ", stdErr="
                        + stdErr + ", skipMavenRc="
                        + skipMavenRc + ", executionTimeout="
                        + executionTimeout + '}';
            }
        }
    }

    static Path discoverInstallationDirectory() {
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome == null) {
            throw new ExecutorException("requires maven.home Java System Property set");
        }
        return getCanonicalPath(Paths.get(mavenHome));
    }

    static Path discoverUserHomeDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            throw new ExecutorException("requires user.home Java System Property set");
        }
        return getCanonicalPath(Paths.get(userHome));
    }

    static Path getCanonicalPath(Path path) {
        requireNonNull(path, "path");
        return path.toAbsolutePath().normalize();
    }
}

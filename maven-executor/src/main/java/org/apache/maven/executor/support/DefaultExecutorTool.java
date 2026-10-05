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
package org.apache.maven.executor.support;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorTool;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static java.util.Objects.requireNonNull;

/**
 * {@link ExecutorTool} implementation that computes the local repository and the paths in it without running Maven,
 * so it does not resolve any plugin into the local repository under test.
 * <p>
 * The local repository is, in order: a {@code -Dmaven.repo.local=} argument of the request, the same argument in
 * {@code .mvn/maven.config} of the request's working directory, the {@code maven.repo.local} JVM system property of
 * the request, the {@code maven.repo.local} system property of this JVM, {@code <localRepository>} of the user
 * settings ({@code -s} argument or {@code ~/.m2/settings.xml}), {@code <localRepository>} of the global settings
 * ({@code -gs} argument or {@code conf/settings.xml} of the installation, when one is given), and finally
 * {@code ~/.m2/repository} of the request's user home.
 * <p>
 * Paths follow the default repository layout, the way Resolver's enhanced local repository manager lays it out,
 * which is the default of Maven 3.9 and 4. This is best effort: it cannot see what the Maven under test sees, such as
 * a split local repository, a non-default layout or settings profiles. {@link ToolboxExecutorTool} asks Maven itself
 * and stays the authoritative choice; it also provides {@link #dump(ExecutorRequest.Builder)}, which this tool cannot.
 *
 * @since 1.1.0
 */
public class DefaultExecutorTool implements ExecutorTool {
    private static final String MAVEN_REPO_LOCAL = "maven.repo.local";

    private static final String DEFAULT_METADATA = "maven-metadata.xml";

    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile("^(.*-)?(\\d{8}\\.\\d{6}-\\d+)$");

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private final Path installationDirectory;

    /**
     * Creates a tool that does not read global settings.
     */
    public DefaultExecutorTool() {
        this(null);
    }

    /**
     * @param installationDirectory the Maven installation whose {@code conf/settings.xml} is the global settings, or
     *     {@code null} to not read global settings
     */
    public DefaultExecutorTool(Path installationDirectory) {
        this.installationDirectory = installationDirectory;
    }

    /**
     * Not supported: a dump needs the Maven under test.
     *
     * @throws ExecutorException always
     */
    @Override
    public Map<String, String> dump(ExecutorRequest.Builder request) throws ExecutorException {
        throw new ExecutorException("dump() needs the Maven under test; use ToolboxExecutorTool");
    }

    @Override
    public String localRepository(ExecutorRequest.Builder request) throws ExecutorException {
        ExecutorRequest executorRequest = requireNonNull(request, "request").build();
        String localRepository = localRepository(executorRequest);
        return executorRequest
                .cwd()
                .resolve(localRepository)
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    /**
     * Returns the path of the artifact relative to the local repository. Only a timestamped snapshot depends on
     * {@code repositoryId}: downloaded ({@code repositoryId} given), its file keeps the timestamp; installed
     * ({@code repositoryId} {@code null}), it is named after the base version, as Resolver does.
     *
     * @param gav {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
     */
    @Override
    public String artifactPath(ExecutorRequest.Builder request, String gav, String repositoryId)
            throws ExecutorException {
        requireNonNull(request, "request");
        String[] parts = requireNonNull(gav, "gav").split(":", -1);
        if (parts.length < 3 || parts.length > 5) {
            throw new ExecutorException("Bad artifact coordinates " + gav
                    + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
        }
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[parts.length - 1];
        String extension = parts.length > 3 && !parts[2].isEmpty() ? parts[2] : "jar";
        String classifier = parts.length > 4 ? parts[3] : "";
        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
            throw new ExecutorException(
                    "Bad artifact coordinates " + gav + ", groupId, artifactId and version" + " must not be empty");
        }

        StringBuilder path = new StringBuilder(128);
        path.append(groupId.replace('.', '/')).append('/');
        path.append(artifactId).append('/');
        path.append(baseVersion(version)).append('/');
        path.append(artifactId).append('-').append(repositoryId != null ? version : baseVersion(version));
        if (!classifier.isEmpty()) {
            path.append('-').append(classifier);
        }
        path.append('.').append(extension);
        return path.toString().replace('/', File.separatorChar);
    }

    /**
     * Returns the path of the metadata relative to the local repository: {@code maven-metadata.xml} becomes
     * {@code maven-metadata-local.xml}, or {@code maven-metadata-<repositoryId>.xml} for the copy of a remote one.
     */
    @Override
    public String metadataPath(ExecutorRequest.Builder request, String gav, String repositoryId)
            throws ExecutorException {
        requireNonNull(request, "request");
        String[] parts = requireNonNull(gav, "gav").split(":", -1);
        if (parts.length > 4) {
            throw new ExecutorException("Bad metadata coordinates " + gav + ", expected format is [G]:[A]:[V]:[type]");
        }
        String groupId = parts[0];
        String artifactId = parts.length > 1 ? parts[1] : "";
        String version = parts.length > 2 ? parts[2] : "";
        String type = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : DEFAULT_METADATA;

        StringBuilder path = new StringBuilder(128);
        if (!groupId.isEmpty()) {
            path.append(groupId.replace('.', '/')).append('/');
            if (!artifactId.isEmpty()) {
                path.append(artifactId).append('/');
                if (!version.isEmpty()) {
                    path.append(version).append('/');
                }
            }
        }
        path.append(insertRepositoryKey(type, repositoryId != null ? repositoryId : "local"));
        return path.toString().replace('/', File.separatorChar);
    }

    private String localRepository(ExecutorRequest request) {
        String fromArguments = mavenRepoLocal(request.arguments());
        if (fromArguments != null) {
            return fromArguments;
        }
        String fromMavenConfig = mavenRepoLocal(mavenConfig(request.cwd()));
        if (fromMavenConfig != null) {
            return fromMavenConfig;
        }
        String fromRequestProperties = request.jvmSystemProperties()
                .map(properties -> properties.get(MAVEN_REPO_LOCAL))
                .orElse(null);
        if (notEmpty(fromRequestProperties)) {
            return fromRequestProperties;
        }
        String fromThisJvm = System.getProperty(MAVEN_REPO_LOCAL);
        if (notEmpty(fromThisJvm)) {
            return fromThisJvm;
        }
        Path userHome = request.userHomeDirectory();
        Path userSettings = settingsArgument(request, "-s", "--settings");
        String fromUserSettings = settingsLocalRepository(
                userSettings != null ? userSettings : userHome.resolve(".m2").resolve("settings.xml"), userHome);
        if (fromUserSettings != null) {
            return fromUserSettings;
        }
        Path globalSettings = settingsArgument(request, "-gs", "--global-settings");
        if (globalSettings == null && installationDirectory != null) {
            globalSettings = installationDirectory.resolve("conf").resolve("settings.xml");
        }
        if (globalSettings != null) {
            String fromGlobalSettings = settingsLocalRepository(globalSettings, userHome);
            if (fromGlobalSettings != null) {
                return fromGlobalSettings;
            }
        }
        return userHome.resolve(".m2").resolve("repository").toString();
    }

    /**
     * The value of the last {@code -Dmaven.repo.local=} in the arguments, given as one argument or as {@code -D}
     * followed by the property, as Maven itself lets a later definition win.
     */
    private static String mavenRepoLocal(List<String> arguments) {
        String result = null;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            String property = null;
            if (argument.startsWith("-D") && argument.length() > 2) {
                property = argument.substring(2);
            } else if (("-D".equals(argument) || "--define".equals(argument)) && i + 1 < arguments.size()) {
                property = arguments.get(++i);
            }
            if (property != null && property.startsWith(MAVEN_REPO_LOCAL + "=")) {
                String value = property.substring(MAVEN_REPO_LOCAL.length() + 1);
                if (!value.isEmpty()) {
                    result = value;
                }
            }
        }
        return result;
    }

    private static List<String> mavenConfig(Path cwd) {
        Path mavenConfig = cwd.resolve(".mvn").resolve("maven.config");
        if (!Files.isRegularFile(mavenConfig)) {
            return Collections.emptyList();
        }
        try {
            List<String> arguments = new ArrayList<>();
            for (String line : Files.readAllLines(mavenConfig, StandardCharsets.UTF_8)) {
                for (String argument : line.trim().split("\\s+")) {
                    if (!argument.isEmpty()) {
                        arguments.add(argument);
                    }
                }
            }
            return arguments;
        } catch (IOException e) {
            throw new ExecutorException("Unable to read " + mavenConfig, e);
        }
    }

    private static Path settingsArgument(ExecutorRequest request, String shortOption, String longOption) {
        List<String> arguments = request.arguments();
        Path result = null;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ((shortOption.equals(argument) || longOption.equals(argument)) && i + 1 < arguments.size()) {
                result = request.cwd().resolve(arguments.get(++i));
            } else if (argument.startsWith(longOption + "=")) {
                result = request.cwd().resolve(argument.substring(longOption.length() + 1));
            }
        }
        return result;
    }

    private static String settingsLocalRepository(Path settings, Path userHome) {
        if (!Files.isRegularFile(settings)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(settings)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if ("localRepository".equals(children.item(i).getLocalName())) {
                    String value = interpolate(children.item(i).getTextContent().trim(), userHome);
                    return value.isEmpty() ? null : value;
                }
            }
            return null;
        } catch (Exception e) {
            throw new ExecutorException("Unable to read the local repository from " + settings, e);
        }
    }

    /**
     * Replaces {@code ${user.home}} with the request's user home, {@code ${env.NAME}} with the environment variable
     * and any other {@code ${name}} with the system property of this JVM; an unknown expression stays as it is.
     */
    private static String interpolate(String value, Path userHome) {
        Matcher matcher = PROPERTY.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            String replacement;
            if ("user.home".equals(expression)) {
                replacement = userHome.toString();
            } else if (expression.startsWith("env.")) {
                replacement = System.getenv(expression.substring(4));
            } else {
                replacement = System.getProperty(expression);
            }
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(replacement != null ? replacement : "${" + expression + "}"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * A timestamped snapshot version {@code 1.0-20260101.120000-1} lives in the {@code 1.0-SNAPSHOT} directory.
     */
    private static String baseVersion(String version) {
        Matcher matcher = TIMESTAMPED_SNAPSHOT.matcher(version);
        if (matcher.matches()) {
            return (matcher.group(1) != null ? matcher.group(1) : "") + "SNAPSHOT";
        }
        return version;
    }

    /**
     * As Resolver does: {@code maven-metadata.xml} and the key {@code local} give {@code maven-metadata-local.xml}.
     */
    private static String insertRepositoryKey(String fileName, String repositoryKey) {
        int dot = fileName.indexOf('.');
        if (dot < 0) {
            return fileName + '-' + repositoryKey;
        }
        return fileName.substring(0, dot) + '-' + repositoryKey + fileName.substring(dot);
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}

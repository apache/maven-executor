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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorTool;
import org.w3c.dom.Document;

/**
 * An {@link ExecutorTool} which calculates local-repository paths without invoking a Maven plugin.
 * Diagnostic dumps are delegated to {@link ToolboxExecutorTool}, because they require Maven itself.
 */
public final class LocalRepositoryExecutorTool implements ExecutorTool {
    private final ToolboxExecutorTool toolbox;

    public LocalRepositoryExecutorTool(Executor executor) {
        toolbox = new ToolboxExecutorTool(Objects.requireNonNull(executor));
    }

    @Override
    public Map<String, String> dump(ExecutorRequest.Builder request) throws ExecutorException {
        return toolbox.dump(request);
    }

    @Override
    public String localRepository(ExecutorRequest.Builder request) throws ExecutorException {
        ExecutorRequest built = request.build();
        String configured = property(built, "maven.repo.local");
        if (configured != null) {
            return configured;
        }
        Path settings = settingsFile(built);
        if (settings != null && Files.isRegularFile(settings)) {
            String localRepository = localRepositoryFromSettings(settings);
            if (localRepository != null) {
                return expand(localRepository, built).toString();
            }
        }
        return built.userHomeDirectory().resolve(".m2").resolve("repository").toString();
    }

    @Override
    public String artifactPath(ExecutorRequest.Builder request, String gav, String repositoryId)
            throws ExecutorException {
        String[] parts = coordinates(gav, 3, 5);
        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        String type = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : "jar";
        String classifier = parts.length > 4 ? parts[4] : "";
        String fileName = artifact + "-" + version
                + (classifier.isEmpty() ? "" : "-" + classifier) + "." + type;
        return Paths.get(localRepository(request)).resolve(group.replace('.', '/'))
                .resolve(artifact).resolve(version).resolve(fileName).toString();
    }

    @Override
    public String metadataPath(ExecutorRequest.Builder request, String gav, String repositoryId)
            throws ExecutorException {
        String[] parts = coordinates(gav, 1, 4);
        String fileName = parts.length == 4 && !parts[3].isEmpty() ? parts[3] : "maven-metadata.xml";
        Path path = Paths.get(localRepository(request));
        if (!parts[0].isEmpty()) {
            path = path.resolve(parts[0].replace('.', '/'));
        }
        if (parts.length > 1 && !parts[1].isEmpty()) {
            path = path.resolve(parts[1]);
        }
        if (parts.length > 2 && !parts[2].isEmpty()) {
            path = path.resolve(parts[2]);
        }
        if (repositoryId != null && !repositoryId.isEmpty() && "maven-metadata.xml".equals(fileName)) {
            fileName = "maven-metadata-" + repositoryId + ".xml";
        }
        return path.resolve(fileName).toString();
    }

    private static String[] coordinates(String value, int min, int max) {
        Objects.requireNonNull(value, "coordinates");
        String[] parts = value.split(":", -1);
        if (parts.length < min || parts.length > max) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + value);
        }
        return parts;
    }

    private static String property(ExecutorRequest request, String name) {
        Optional<Map<String, String>> properties = request.jvmSystemProperties();
        if (properties.isPresent() && properties.get().containsKey(name)) {
            return properties.get().get(name);
        }
        for (String argument : request.arguments()) {
            String prefix = "-D" + name + "=";
            if (argument.startsWith(prefix)) {
                return argument.substring(prefix.length());
            }
        }
        return null;
    }

    private static Path settingsFile(ExecutorRequest request) {
        String[] arguments = request.arguments().toArray(new String[0]);
        for (int i = 0; i < arguments.length; i++) {
            if (("-s".equals(arguments[i]) || "--settings".equals(arguments[i])) && i + 1 < arguments.length) {
                return request.cwd().resolve(arguments[i + 1]).normalize();
            }
        }
        return request.userHomeDirectory().resolve(".m2/settings.xml");
    }

    private static String localRepositoryFromSettings(Path settings) throws ExecutorException {
        try (InputStream input = Files.newInputStream(settings)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(input);
            if (document.getElementsByTagName("localRepository").getLength() == 0) {
                return null;
            }
            String value = document.getElementsByTagName("localRepository").item(0).getTextContent().trim();
            return value.isEmpty() ? null : value;
        } catch (Exception e) {
            throw new ExecutorException("Unable to read Maven settings: " + settings, e);
        }
    }

    private static Path expand(String value, ExecutorRequest request) {
        return Paths.get(value.replace("${user.home}", request.userHomeDirectory().toString())
                .replace("${maven.multiModuleProjectDirectory}", request.cwd().toString()));
    }
}

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
package org.apache.maven.executor.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class TestProjects {
    private TestProjects() {}

    public static void createSimpleProject(Path cwd) throws IOException {
        requireNonNull(cwd);

        Files.createDirectories(cwd);
        Path pom = cwd.resolve("pom.xml");
        Path submodulePom = cwd.resolve("submodule").resolve("pom.xml");

        Files.write(
                pom,
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<project xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/maven-v4_0_0.xsd\">\n"
                                + "\n"
                                + "  <modelVersion>4.0.0</modelVersion>\n"
                                + "\n"
                                + "  <groupId>org.apache.maven.samples</groupId>\n"
                                + "  <artifactId>simple-project</artifactId>\n"
                                + "  <version>1.0.0-SNAPSHOT</version>\n"
                                + "\n"
                                + "  <packaging>pom</packaging>\n"
                                + "\n"
                                + "  <modules>\n"
                                + "    <module>submodule</module>\n"
                                + "  </modules>\n"
                                + "</project>\n")
                        .getBytes(StandardCharsets.UTF_8));

        Files.createDirectories(submodulePom.getParent());
        Files.write(
                submodulePom,
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<project xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/maven-v4_0_0.xsd\">\n"
                                + "\n"
                                + "  <modelVersion>4.0.0</modelVersion>\n"
                                + "\n"
                                + "  <parent>\n"
                                + "    <groupId>org.apache.maven.samples</groupId>\n"
                                + "    <artifactId>simple-project</artifactId>\n"
                                + "    <version>1.0.0-SNAPSHOT</version>\n"
                                + "  </parent>\n"
                                + "\n"
                                + "  <artifactId>submodule</artifactId>\n"
                                + "\n"
                                + "  <dependencies>\n"
                                + "    <dependency>\n"
                                + "      <groupId>junit</groupId>\n"
                                + "      <artifactId>junit</artifactId>\n"
                                + "      <version>4.13.2</version>\n"
                                + "    </dependency>\n"
                                + "  </dependencies>\n"
                                + "</project>\n")
                        .getBytes(StandardCharsets.UTF_8));
    }
}

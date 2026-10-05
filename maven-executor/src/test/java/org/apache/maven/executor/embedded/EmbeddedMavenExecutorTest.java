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
package org.apache.maven.executor.embedded;

import java.nio.file.Path;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.MavenExecutorTestSupport;

/**
 * Embedded executor UT
 */
public class EmbeddedMavenExecutorTest extends MavenExecutorTestSupport {

    @Override
    protected Executor doSelectExecutor(Path installationDirectory) {
        return new EmbeddedMavenExecutor(installationDirectory);
    }

    /**
     * Maven 3.10 colors only if its JLine terminal is installed, and that happens in {@code MavenCli.main} but not in
     * the {@code MavenCli.doMain} entry point used by embedded execution, so {@code --color=yes} has no effect there.
     * Maven 3.9 kept the flag in a static and honored it.
     */
    @Override
    protected boolean maven3HonorsForcedColor() {
        String[] version = System.getProperty("maven3version", "3.9").split("\\.");
        return Integer.parseInt(version[0]) == 3 && Integer.parseInt(version[1]) < 10;
    }
}

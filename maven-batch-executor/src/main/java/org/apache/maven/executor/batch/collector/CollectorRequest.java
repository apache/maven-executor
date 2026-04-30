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
package org.apache.maven.executor.batch.collector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.executor.batch.BatchExecutorRequest;

/**
 * Collector collects potential test projects and creates {@link BatchExecutorRequest} based on them. It also
 * performs a copy and interpolation as well.
 */
public interface CollectorRequest {
    /**
     * This directory (that must exists) will be looked upon (to the first level), and found directories will be
     * recursively copied over to {@link #targetDirectory()}.
     */
    Path sourceDirectory();

    /**
     * The direction where discovered {@link #sourceDirectory()} contents will be copied to.
     */
    Path targetDirectory();

    /**
     * The user home directory, as configured.
     */
    Path userHomeDirectory();

    /**
     * If present, during copy, applicable files will be filtered as well with these properties.
     */
    Optional<Map<String, String>> filterProperties();

    /**
     * The default command, as configured.
     */
    String defaultCommand();

    /**
     * The default arguments, as configured.
     */
    List<String> defaultArguments();
}

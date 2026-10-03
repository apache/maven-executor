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

import java.lang.reflect.Method;
import java.util.stream.Stream;

/**
 * Destroys a started process together with every process it started.
 * <p>
 * {@link Process#destroyForcibly()} reaches only the process itself: the JVMs a Maven build forks (Surefire,
 * Failsafe, {@code exec:exec}) survive it, and on Windows so does the Maven JVM, which is a child of the
 * {@code cmd.exe} running {@code mvn.cmd}. {@code ProcessHandle} (Java 9+) is reached by reflection rather than from
 * a multi-release class, so the same code runs from an exploded {@code target/classes} directory, where versioned
 * classes are never loaded. On Java 8 only the process itself is destroyed.
 */
final class ProcessTrees {
    private static final Method TO_HANDLE;
    private static final Method DESCENDANTS;
    private static final Method DESTROY_FORCIBLY;

    static {
        Method toHandle = null;
        Method descendants = null;
        Method destroyForcibly = null;
        try {
            Class<?> processHandle = Class.forName("java.lang.ProcessHandle");
            toHandle = Process.class.getMethod("toHandle");
            descendants = processHandle.getMethod("descendants");
            destroyForcibly = processHandle.getMethod("destroyForcibly");
        } catch (ReflectiveOperationException e) {
            // Java 8: no ProcessHandle
        }
        TO_HANDLE = toHandle;
        DESCENDANTS = descendants;
        DESTROY_FORCIBLY = destroyForcibly;
    }

    private ProcessTrees() {}

    /**
     * Forcibly destroys the descendants of the process, then the process. The descendants go first: once the parent
     * is gone they are reparented and can no longer be found from it.
     */
    static void destroyForcibly(Process process) {
        if (TO_HANDLE != null) {
            try {
                Object handle = TO_HANDLE.invoke(process);
                Stream<?> descendants = (Stream<?>) DESCENDANTS.invoke(handle);
                descendants.forEach(ProcessTrees::destroyHandleForcibly);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // best effort: the process itself is still destroyed below
            }
        }
        process.destroyForcibly();
    }

    private static void destroyHandleForcibly(Object handle) {
        try {
            DESTROY_FORCIBLY.invoke(handle);
        } catch (ReflectiveOperationException e) {
            // the descendant may already be gone
        }
    }
}

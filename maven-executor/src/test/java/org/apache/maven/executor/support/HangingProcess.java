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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * Stands in for a Maven build that hangs: {@code parent <heartbeat>} prints a line to STDOUT and one to STDERR, starts
 * {@code child <heartbeat>} as its own child process, and sleeps; the child appends to the heartbeat file every 50ms,
 * like a forked test JVM that never ends. {@code noisy <heartbeat>} prints {@link #NOISY_LINES} lines to STDOUT,
 * then {@link #LAST_LINE}, and sleeps. Both give up after {@link #LIFETIME_MILLIS}, so a failing test leaves no
 * process behind for long.
 */
public final class HangingProcess {
    static final String STDOUT_LINE = "parent started";
    static final String STDERR_LINE = "parent warning";
    static final long LIFETIME_MILLIS = 30_000;
    static final int NOISY_LINES = 200_000;
    static final String LAST_LINE = "last line before the hang";

    private HangingProcess() {}

    static ProcessBuilder parent(File heartbeat) {
        return java("parent", heartbeat);
    }

    static ProcessBuilder noisy(File heartbeat) {
        return java("noisy", heartbeat);
    }

    private static ProcessBuilder java(String mode, File heartbeat) {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classPath;
        try {
            // toURI() decodes %20 and turns /D:/ into D:\ on Windows
            classPath = Paths.get(HangingProcess.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return new ProcessBuilder(
                java, "-cp", classPath, HangingProcess.class.getName(), mode, heartbeat.getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        long deadline = System.currentTimeMillis() + LIFETIME_MILLIS;
        File heartbeat = new File(args[1]);
        if ("parent".equals(args[0])) {
            java("child", heartbeat)
                    .redirectOutput(Redirect.appendTo(new File(heartbeat.getPath() + ".out")))
                    .redirectErrorStream(true)
                    .start();
            System.out.println(STDOUT_LINE);
            System.out.flush();
            System.err.println(STDERR_LINE);
            System.err.flush();
        } else if ("noisy".equals(args[0])) {
            for (int i = 0; i < NOISY_LINES; i++) {
                System.out.println("line " + i + " of the output a hung build keeps writing");
            }
            System.out.println(LAST_LINE);
            System.out.flush();
        } else {
            try (OutputStream out = new FileOutputStream(heartbeat, true)) {
                while (System.currentTimeMillis() < deadline) {
                    out.write('.');
                    out.flush();
                    Thread.sleep(50);
                }
            }
        }
        Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
    }
}

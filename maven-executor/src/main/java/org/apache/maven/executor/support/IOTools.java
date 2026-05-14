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
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * Support class IO.
 */
public final class IOTools {
    private IOTools() {}

    private static final int DEFAULT_BUFFER_SIZE = 16384;

    /**
     * Copy from Java 9+
     */
    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        requireNonNull(in);
        requireNonNull(out);
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            if (transferred < Long.MAX_VALUE) {
                try {
                    transferred = Math.addExact(transferred, read);
                } catch (ArithmeticException ignore) {
                    transferred = Long.MAX_VALUE;
                }
            }
        }
        return transferred;
    }

    /**
     * Copy from Java 9+
     */
    public static InputStream nullInputStream() {
        return new InputStream() {
            private volatile boolean closed;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public int available() throws IOException {
                ensureOpen();
                return 0;
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                ensureOpen();
                return -1;
            }

            @Override
            public long skip(long n) throws IOException {
                ensureOpen();
                return 0L;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }

    /**
     * Copy from Java 9+
     */
    public static OutputStream nullOutputStream() {
        return new OutputStream() {
            private volatile boolean closed;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public void write(int b) throws IOException {
                ensureOpen();
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ensureOpen();
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }
}

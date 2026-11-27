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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.ExecutorTimeoutException;

import static java.util.Objects.requireNonNull;

/**
 * Support class for executor implementations using {@link ProcessBuilder}.
 */
public abstract class ProcessBuilderExecutorSupport implements Executor {
    private static final long DRAIN_MILLIS = 5000;

    protected final AtomicBoolean closed;

    protected ProcessBuilderExecutorSupport() {
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public void close() throws ExecutorException {
        if (closed.compareAndSet(false, true)) {
            doClose();
        }
    }

    protected void doClose() throws ExecutorException {}

    protected ExecutorResult doExecuteProcess(ExecutorRequest execution, ProcessBuilder processBuilder) {
        requireNonNull(execution);
        if (execution.executionTimeout().isPresent()
                && execution.executionTimeout().get().isNegative()) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }

        Process process = null;
        try {
            process = processBuilder.start();
            InputStream stdIn = execution.stdIn().orElse(IOTools.nullInputStream());
            OutputStream stdOut;
            OutputStream stdErr;
            if (execution.grabOutputAsString()) {
                stdOut = new GrabbedOutput();
                stdErr = new GrabbedOutput();
            } else {
                stdOut = execution.stdOut().orElse(IOTools.nullOutputStream());
                stdErr = execution.stdErr().orElse(IOTools.nullOutputStream());
            }
            if (execution.executionTimeout().isPresent()) {
                long timeoutMillis = execution
                        .executionTimeout()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .toMillis();
                CountDownLatch pumps = pump(process, stdIn, stdOut, stdErr);
                if (pumps.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    int exitCode = process.waitFor();
                    String stdOutString = null;
                    String stdErrString = null;
                    if (execution.grabOutputAsString()) {
                        // they are ByteArrayOutputStreams
                        stdOutString = stdOut.toString();
                        stdErrString = stdErr.toString();
                    }
                    return new SimpleExecutionResult(execution, exitCode == 0, exitCode, stdOutString, stdErrString);
                } else {
                    ProcessTrees.destroyForcibly(process);
                    // the pumps finish once the destroyed processes have closed their pipes; wait for them, but
                    // not for long, since a descendant that survived (Java 8) keeps the pipes open
                    // an interrupt here takes the InterruptedException branch below, without the tail
                    pumps.await(DRAIN_MILLIS, TimeUnit.MILLISECONDS);
                    String stdOutTail = null;
                    String stdErrTail = null;
                    if (execution.grabOutputAsString()) {
                        // only the tail: a hung build may have logged far more than fits in a second copy
                        stdOutTail = ((GrabbedOutput) stdOut).tail(ExecutorTimeoutException.MAX_TAIL_BYTES);
                        stdErrTail = ((GrabbedOutput) stdErr).tail(ExecutorTimeoutException.MAX_TAIL_BYTES);
                    }
                    throw new ExecutorTimeoutException("Process timeout: " + execution, stdOutTail, stdErrTail);
                }
            } else {
                pump(process, stdIn, stdOut, stdErr).await();
                int exitCode = process.waitFor();
                String stdOutString = null;
                String stdErrString = null;
                if (execution.grabOutputAsString()) {
                    // they are ByteArrayOutputStreams
                    stdOutString = stdOut.toString();
                    stdErrString = stdErr.toString();
                }
                return new SimpleExecutionResult(execution, exitCode == 0, exitCode, stdOutString, stdErrString);
            }
        } catch (IOException e) {
            if (process != null) {
                ProcessTrees.destroyForcibly(process);
            }
            throw new ExecutorException("IO problem while executing command: " + execution, e);
        } catch (InterruptedException e) {
            ProcessTrees.destroyForcibly(process);
            throw new ExecutorException("Interrupted while executing command: " + execution, e);
        }
    }

    /**
     * Grabbed output that can also hand out its tail without copying the whole buffer.
     */
    static final class GrabbedOutput extends ByteArrayOutputStream {
        /**
         * The last {@code maxBytes} bytes at most. When cut, the tail starts after the first line break in that
         * window; a window without one starts mid-line, and a leading partial multibyte sequence decodes as U+FFFD.
         */
        synchronized String tail(int maxBytes) {
            int from = Math.max(0, count - maxBytes);
            if (from > 0) {
                // a break as the last byte would leave nothing
                for (int i = from; i < count - 1; i++) {
                    if (buf[i] == '\n') {
                        from = i + 1;
                        break;
                    }
                }
            }
            return new String(buf, from, count - from, Charset.defaultCharset());
        }
    }

    protected CountDownLatch pump(Process p, InputStream stdIn, OutputStream stdOut, OutputStream stdErr) {
        CountDownLatch latch = new CountDownLatch(3);
        String suffix = "-pump-" + ThreadLocalRandom.current().nextInt();
        Thread stdoutPump = new Thread(() -> {
            try (OutputStream stdout = stdOut) {
                IOTools.transferTo(p.getInputStream(), stdout);
                stdout.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdoutPump.setName("stdout" + suffix);
        stdoutPump.setDaemon(true);
        stdoutPump.start();
        Thread stderrPump = new Thread(() -> {
            try (OutputStream stderr = stdErr) {
                IOTools.transferTo(p.getErrorStream(), stderr);
                stderr.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stderrPump.setName("stderr" + suffix);
        stderrPump.setDaemon(true);
        stderrPump.start();
        Thread stdinPump = new Thread(() -> {
            try (OutputStream in = p.getOutputStream()) {
                IOTools.transferTo(stdIn, in);
                in.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdinPump.setName("stdin" + suffix);
        stdinPump.setDaemon(true);
        stdinPump.start();
        return latch;
    }
}

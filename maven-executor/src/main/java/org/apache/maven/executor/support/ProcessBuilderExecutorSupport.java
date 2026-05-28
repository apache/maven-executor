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
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;

import static java.util.Objects.requireNonNull;

/**
 * Support class for executor implementations using {@link ProcessBuilder}.
 */
public abstract class ProcessBuilderExecutorSupport implements Executor {
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
                stdOut = new ByteArrayOutputStream();
                stdErr = new ByteArrayOutputStream();
            } else {
                stdOut = execution.stdOut().orElse(IOTools.nullOutputStream());
                stdErr = execution.stdErr().orElse(IOTools.nullOutputStream());
            }
            if (execution.executionTimeout().isPresent()) {
                long timeoutMillis = execution
                        .executionTimeout()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .toMillis();
                if (pump(process, stdIn, stdOut, stdErr).await(timeoutMillis, TimeUnit.MILLISECONDS)) {
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
                    process.destroyForcibly();
                    throw new ExecutorException("Process timeout: " + execution);
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
                process.destroyForcibly();
            }
            throw new ExecutorException("IO problem while executing command: " + execution, e);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw new ExecutorException("Interrupted while executing command: " + execution, e);
        }
    }

    protected String mayQuoteAndEscape(String command) {
        if (command.contains(" ")) {
            if (command.contains("\"")) {
                return "\"" + command.replace("\"", "\\\"") + "\"";
            } else {
                return "\"" + command + "\"";
            }
        }
        return command;
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

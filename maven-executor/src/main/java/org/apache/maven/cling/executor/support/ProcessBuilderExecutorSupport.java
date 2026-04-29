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
package org.apache.maven.cling.executor.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.cling.executor.Executor;
import org.apache.maven.cling.executor.ExecutorException;
import org.apache.maven.cling.executor.ExecutorRequest;

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
        if (closed.compareAndExchange(false, true)) {
            doClose();
        }
    }

    protected void doClose() throws ExecutorException {}

    protected int doExecuteProcess(ExecutorRequest executorRequest, ProcessBuilder processBuilder) {
        try {
            Process process = processBuilder.start();
            pump(process, executorRequest).await();
            return process.waitFor();
        } catch (IOException e) {
            throw new ExecutorException("IO problem while executing command: " + executorRequest, e);
        } catch (InterruptedException e) {
            throw new ExecutorException("Interrupted while executing command: " + executorRequest, e);
        }
    }

    protected CountDownLatch pump(Process p, ExecutorRequest executorRequest) {
        CountDownLatch latch = new CountDownLatch(3);
        String suffix = "-pump-" + p.pid();
        Thread stdoutPump = new Thread(() -> {
            try {
                OutputStream stdout = executorRequest.stdOut().orElse(OutputStream.nullOutputStream());
                p.getInputStream().transferTo(stdout);
                stdout.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdoutPump.setName("stdout" + suffix);
        stdoutPump.start();
        Thread stderrPump = new Thread(() -> {
            try {
                OutputStream stderr = executorRequest.stdErr().orElse(OutputStream.nullOutputStream());
                p.getErrorStream().transferTo(stderr);
                stderr.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stderrPump.setName("stderr" + suffix);
        stderrPump.start();
        Thread stdinPump = new Thread(() -> {
            try {
                OutputStream in = p.getOutputStream();
                executorRequest.stdIn().orElse(InputStream.nullInputStream()).transferTo(in);
                in.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdinPump.setName("stdin" + suffix);
        stdinPump.start();
        return latch;
    }
}

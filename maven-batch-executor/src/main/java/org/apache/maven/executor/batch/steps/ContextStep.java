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
package org.apache.maven.executor.batch.steps;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import static java.util.Objects.requireNonNull;

/**
 * Context step: context consuming step. This step can prepare things, can skip context.
 */
public class ContextStep implements Step {
    public interface Consumer {
        void accept(StepContext context);
    }

    private final Consumer consumer;

    public ContextStep(Consumer consumer) {
        this.consumer = requireNonNull(consumer);
    }

    @Override
    public void execute(StepContext context) {
        consumer.accept(context);
    }

    public static Consumer ofScriptEngine(ScriptEngine engine, String script) {
        return new Consumer() {
            @Override
            public void accept(StepContext context) {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("log", context.log());
                bindings.put("cwd", context.cwd());
                bindings.put("userHome", context.userHome());
                bindings.put("tool", context.tool());
                bindings.put("executions", context.executions());
                context.last().ifPresent(l -> bindings.put("last", l));
                bindings.put("shared", context.shared());
                bindings.put("context", context);
                try {
                    engine.eval(script, bindings);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}

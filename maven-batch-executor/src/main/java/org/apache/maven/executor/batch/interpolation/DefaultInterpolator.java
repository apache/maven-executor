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
package org.apache.maven.executor.batch.interpolation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class DefaultInterpolator implements Interpolator {
    /**
     * Creates "standard" Maven interpolator that uses {@code "$"} as expression marker.
     * For testing purposes.
     */
    static DefaultInterpolator defaultMavenInterpolator() {
        return new DefaultInterpolator('\\', "$");
    }

    protected final char escapeChar;
    protected final String expressionMarker;
    protected final String delimStart;
    protected final String delimStop;
    protected final String marker;

    protected DefaultInterpolator(char escapeChar, String expressionMarker) {
        this.escapeChar = escapeChar;
        this.expressionMarker = requireNonNull(expressionMarker);
        this.delimStart = expressionMarker + "{";
        this.delimStop = "}";
        this.marker = expressionMarker + "__";
    }

    @Override
    public void interpolate(
            Map<String, String> map,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmpty) {
        Map<String, String> org = new HashMap<>(map);
        for (String name : map.keySet()) {
            map.compute(
                    name,
                    (k, value) -> interpolate(
                            value,
                            name,
                            null,
                            v -> {
                                String r = org.get(v);
                                if (r == null && callback != null) {
                                    r = callback.apply(v);
                                }
                                return r;
                            },
                            postprocessor,
                            defaultsToEmpty));
        }
    }

    @Override
    public String interpolate(
            String val, UnaryOperator<String> callback, BinaryOperator<String> postprocessor, boolean defaultsToEmpty) {
        return interpolate(val, null, null, callback, postprocessor, defaultsToEmpty);
    }

    public String interpolate(
            String val,
            String currentKey,
            Set<String> cycleMap,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmpty) {
        return substVars(val, currentKey, cycleMap, null, callback, postprocessor, defaultsToEmpty);
    }

    /**
     * Perform substitution on a property set
     *
     * @param properties the property set to perform substitution on
     * @param callback Callback for substitution
     */
    public void performSubstitution(Map<String, String> properties, UnaryOperator<String> callback) {
        performSubstitution(properties, callback, true);
    }

    /**
     * Perform substitution on a property set
     *
     * @param properties the property set to perform substitution on
     * @param callback the callback to obtain substitution values
     * @param defaultsToEmptyString sets an empty string if a replacement value is not found, leaves intact otherwise
     */
    public void performSubstitution(
            Map<String, String> properties, UnaryOperator<String> callback, boolean defaultsToEmptyString) {
        Map<String, String> org = new HashMap<>(properties);
        for (String name : properties.keySet()) {
            properties.compute(
                    name, (k, value) -> substVars(value, name, null, org, callback, null, defaultsToEmptyString));
        }
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * {@code ${&lt;prop-name&gt;}}, where {@code &lt;prop-name&gt;}
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from innermost to outermost. Configuration
     * properties override system properties.
     * </p>
     *
     * @param val The string on which to perform property substitution.
     * @param currentKey The key of the property being evaluated used to
     *        detect cycles.
     * @param cycleMap Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *         property placeholder syntax or a recursive variable reference.
     **/
    public String substVars(String val, String currentKey, Set<String> cycleMap, Map<String, String> configProps) {
        return substVars(val, currentKey, cycleMap, configProps, null);
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * {@code ${&lt;prop-name&gt;}}, where {@code &lt;prop-name&gt;}
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from innermost to outermost. Configuration
     * properties override system properties.
     * </p>
     *
     * @param val The string on which to perform property substitution.
     * @param currentKey The key of the property being evaluated used to
     *        detect cycles.
     * @param cycleMap Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @param callback the callback to obtain substitution values
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *         property placeholder syntax or a recursive variable reference.
     **/
    public String substVars(
            String val,
            String currentKey,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback) {
        return substVars(val, currentKey, cycleMap, configProps, callback, null, false);
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * {@code ${&lt;prop-name&gt;}}, where {@code &lt;prop-name&gt;}
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from innermost to outermost. Configuration
     * properties override system properties.
     * </p>
     *
     * @param val The string on which to perform property substitution.
     * @param currentKey The key of the property being evaluated used to
     *        detect cycles.
     * @param cycleMap Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @param callback the callback to obtain substitution values
     * @param defaultsToEmptyString sets an empty string if a replacement value is not found, leaves intact otherwise
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *         property placeholder syntax or a recursive variable reference.
     **/
    protected String substVars(
            String val,
            String currentKey,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmptyString) {
        return unescape(
                doSubstVars(val, currentKey, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString));
    }

    protected String doSubstVars(
            String val,
            String currentKey,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmptyString) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        if (cycleMap == null) {
            cycleMap = new HashSet<>();
        }

        // Put the current key in the cycle map.
        if (currentKey != null) {
            cycleMap.add(currentKey);
        }

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int startDelim;
        int stopDelim = -1;
        do {
            stopDelim = val.indexOf(delimStop, stopDelim + 1);
            while (stopDelim > 0 && val.charAt(stopDelim - 1) == escapeChar) {
                stopDelim = val.indexOf(delimStop, stopDelim + 1);
            }

            // Find the matching starting "${" variable delimiter
            // by looping until we find a start delimiter that is
            // greater than the stop delimiter we have found.
            startDelim = val.indexOf(delimStart);
            while (stopDelim >= 0) {
                int idx = val.indexOf(delimStart, startDelim + delimStart.length());
                if ((idx < 0) || (idx > stopDelim)) {
                    break;
                } else if (idx < stopDelim) {
                    startDelim = idx;
                }
            }
        } while (startDelim >= 0 && stopDelim >= 0 && stopDelim < startDelim + delimStart.length());

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        if ((startDelim < 0) || (stopDelim < 0)) {
            cycleMap.remove(currentKey);
            return val;
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable = val.substring(startDelim + delimStart.length(), stopDelim);

        String substValue =
                processSubstitution(variable, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + delimStop.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = doSubstVars(val, currentKey, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);

        cycleMap.remove(currentKey);

        // Return the value.
        return val;
    }

    protected String processSubstitution(
            String variable,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmptyString) {

        // Process chained operators from left to right
        int startIdx = 0;
        String substValue = null;

        while (startIdx < variable.length()) {
            int idx1 = variable.indexOf(":-", startIdx);
            int idx2 = variable.indexOf(":+", startIdx);
            int idx = idx1 >= 0 ? idx2 >= 0 ? Math.min(idx1, idx2) : idx1 : idx2;

            if (idx < 0) {
                // No more operators, process the final variable
                if (substValue == null) {
                    String currentVar = variable.substring(startIdx);
                    substValue = resolveVariable(
                            currentVar, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);
                }
                break;
            }

            // Get the current variable part before the operator
            String varPart = variable.substring(startIdx, idx);
            if (substValue == null) {
                substValue =
                        resolveVariable(varPart, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);
            }

            // Find the end of the current operator's value
            int nextIdx1 = variable.indexOf(":-", idx + 2);
            int nextIdx2 = variable.indexOf(":+", idx + 2);
            int nextIdx = nextIdx1 >= 0 ? nextIdx2 >= 0 ? Math.min(nextIdx1, nextIdx2) : nextIdx1 : nextIdx2;

            String op = variable.substring(idx, idx + 2);
            String opValue = variable.substring(idx + 2, nextIdx >= 0 ? nextIdx : variable.length());

            // Process the operator value through substitution if it contains variables
            String processedOpValue = doSubstVars(
                    opValue, variable, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);

            // Apply the operator
            if (":+".equals(op)) {
                if (substValue != null && !substValue.isEmpty()) {
                    substValue = processedOpValue;
                    // Skip any remaining operators since we've made a decision
                    break;
                }
            } else if (":-".equals(op)) {
                if (substValue == null || substValue.isEmpty()) {
                    substValue = processedOpValue;
                    // Skip any remaining operators since we've made a decision
                    break;
                }
            } else {
                throw new IllegalArgumentException(
                        "Bad substitution operator in: " + delimStart + variable + delimStop);
            }

            startIdx = nextIdx >= 0 ? nextIdx : variable.length();
        }

        if (substValue == null) {
            if (defaultsToEmptyString) {
                substValue = "";
            } else {
                substValue = marker + "{" + variable + "}";
            }
        }

        return substValue;
    }

    protected String resolveVariable(
            String variable,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            BinaryOperator<String> postprocessor,
            boolean defaultsToEmptyString) {

        // Verify that this is not a recursive variable reference
        if (!cycleMap.add(variable)) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        String substValue = null;
        // Try configuration properties first
        if (configProps != null) {
            substValue = configProps.get(variable);
        }
        if (substValue == null && !variable.isEmpty() && callback != null) {
            String s1 = callback.apply(variable);
            String s2 =
                    doSubstVars(s1, variable, cycleMap, configProps, callback, postprocessor, defaultsToEmptyString);
            substValue = postprocessor != null ? postprocessor.apply(variable, s2) : s2;
        }

        // Remove the variable from cycle map
        cycleMap.remove(variable);
        return substValue;
    }

    /**
     * Escapes special characters in the given string to prevent unwanted interpolation.
     *
     * @param val The string to be escaped.
     * @return The escaped string.
     */
    @Override
    public String escape(String val) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        return val.replace(expressionMarker, marker);
    }

    /**
     * Unescapes previously escaped characters in the given string.
     *
     * @param val The string to be unescaped.
     * @return The unescaped string.
     */
    @Override
    public String unescape(String val) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        val = val.replace(marker, expressionMarker);
        int escape = val.indexOf(escapeChar);
        while (escape >= 0 && escape < val.length() - 1) {
            char c = val.charAt(escape + 1);
            if (c == '{' || c == '}') {
                val = val.substring(0, escape) + val.substring(escape + 1);
            }
            escape = val.indexOf(escapeChar, escape + 1);
        }
        return val;
    }
}

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
package org.apache.maven.executor.verifier;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.ExecutorTool;
import org.apache.maven.executor.embedded.EmbeddedMavenExecutor;
import org.apache.maven.executor.forked.ForkedMavenExecutor;
import org.apache.maven.executor.support.DefaultExecutorTool;

import static java.util.Objects.requireNonNull;

/**
 * Runs Maven on a test project and checks the result: the log, files in the project and artifacts in the local
 * repository. It is the successor of {@code org.apache.maven.shared.verifier.Verifier} from maven-verifier, on top of
 * maven-executor.
 * <p>
 * Each {@link #execute()} runs Maven in {@link #getBasedir()} with {@code -e --batch-mode}, the local repository of
 * this verifier as {@code -Dmaven.repo.local}, {@code maven-clean-plugin:clean} first unless
 * {@link #setAutoclean(boolean) autoclean} is off, the {@link #setSystemProperty(String, String) system properties}
 * as {@code -D} arguments and then the {@link #addCliArgument(String) arguments}. Standard output and standard error
 * go to the {@link #setLogFileName(String) log file} in the base directory, which the {@code verify*InLog} checks
 * read.
 * <p>
 * Without an explicit {@link ExecutorHelper}, Maven is the installation in the {@code maven.home} system property,
 * run in the mode of the {@code verifier.forkMode} system property ({@code forked} when unset). The executors for an
 * installation are created once and kept for the life of the JVM, so an embedded Maven is not set up again for every
 * test.
 * <p>
 * The local repository and artifact paths come from an {@link ExecutorTool}, by default {@link DefaultExecutorTool},
 * which computes them without running Maven or resolving any plugin.
 */
public class Verifier {
    private static final String LOG_FILENAME = "log.txt";

    private static final String CLEAN_CLI_ARGUMENT = "org.apache.maven.plugins:maven-clean-plugin:clean";

    private static final String ARTIFACT_MARKER = "${artifact:";

    private static final Map<Path, ExecutorHelper> DEFAULT_EXECUTOR_HELPERS = new ConcurrentHashMap<>();

    /**
     * Packaging types that do not name their own file: the extension and classifier the artifact is stored with.
     */
    private static final Map<String, String[]> TYPES;

    static {
        Map<String, String[]> types = new HashMap<>();
        types.put("maven-plugin", new String[] {"jar", null});
        types.put("ejb", new String[] {"jar", null});
        types.put("test-jar", new String[] {"jar", "tests"});
        types.put("ejb-client", new String[] {"jar", "client"});
        types.put("java-source", new String[] {"jar", "sources"});
        types.put("javadoc", new String[] {"jar", "javadoc"});
        TYPES = Collections.unmodifiableMap(types);
    }

    private final String basedir;

    private final ExecutorHelper executorHelper;

    private ExecutorTool executorTool;

    private String localRepository;

    private final List<String> defaultCliArguments = new ArrayList<>(Arrays.asList("-e", "--batch-mode"));

    private final List<String> cliArguments = new ArrayList<>();

    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    private final Map<String, String> environmentVariables = new HashMap<>();

    private boolean autoclean = true;

    private boolean forkJvm;

    private String logFileName = LOG_FILENAME;

    private String executable = ExecutorRequest.MVN;

    /**
     * A verifier for the Maven installation in the {@code maven.home} system property.
     *
     * @param basedir the directory of the test project
     */
    public Verifier(String basedir) {
        this(basedir, defaultExecutorHelper());
    }

    /**
     * @param basedir the directory of the test project
     * @param executorHelper runs Maven; this verifier does not close it
     */
    public Verifier(String basedir, ExecutorHelper executorHelper) {
        this.basedir = requireNonNull(basedir, "basedir");
        this.executorHelper = requireNonNull(executorHelper, "executorHelper");
        this.executorTool = new DefaultExecutorTool(mavenHome());
    }

    /**
     * The shared helper for the installation in {@code maven.home}, in the mode of {@code verifier.forkMode}.
     */
    private static ExecutorHelper defaultExecutorHelper() {
        Path mavenHome = mavenHome();
        if (mavenHome == null) {
            throw new IllegalStateException("The maven.home system property must point to the Maven installation to"
                    + " run, or the verifier must be given an ExecutorHelper");
        }
        String forkMode = System.getProperty("verifier.forkMode");
        ExecutorHelper.Mode mode = forkMode == null || forkMode.isEmpty()
                ? ExecutorHelper.Mode.FORKED
                : ExecutorHelper.Mode.valueOf(forkMode.toUpperCase(Locale.ROOT));
        return DEFAULT_EXECUTOR_HELPERS.computeIfAbsent(
                mavenHome.toAbsolutePath().normalize(),
                home -> ExecutorHelper.forExecutors(
                        mode, new EmbeddedMavenExecutor(home), new ForkedMavenExecutor(home)));
    }

    private static Path mavenHome() {
        String mavenHome = System.getProperty("maven.home");
        return mavenHome == null || mavenHome.isEmpty() ? null : Paths.get(mavenHome);
    }

    // ----------------------------------------------------------------------
    // Running Maven
    // ----------------------------------------------------------------------

    /**
     * Runs Maven and writes its output to the log file.
     *
     * @throws VerificationException if Maven could not be run or exited with a non-zero code; the message has the
     *     command line and the log
     */
    public void execute() throws VerificationException {
        List<String> args = commandLine();
        File logFile = getLogFile();
        // the executor's pump threads close the streams they are given, so the log file is written afterwards
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        ExecutorResult result;
        try {
            ExecutorRequest.Builder builder = ExecutorRequest.mavenBuilder()
                    .command(executable)
                    .cwd(Paths.get(basedir))
                    .arguments(args)
                    .skipMavenRc(true)
                    .stdOut(stdOut)
                    .stdErr(stdErr);
            if (!environmentVariables.isEmpty()) {
                builder.environmentVariables(environmentVariables);
            }
            ExecutorHelper.Mode mode = forkJvm ? ExecutorHelper.Mode.FORKED : executorHelper.getDefaultMode();
            result = executorHelper.execute(mode, builder.build());
        } catch (ExecutorException e) {
            writeLogFile(logFile, stdOut, stdErr);
            throw new VerificationException("Failed to execute Maven", e);
        }
        writeLogFile(logFile, stdOut, stdErr);
        if (!result.success()) {
            throw new VerificationException(
                    "Exit code was non-zero: " + result.exitCode().orElse(-1)
                            + "; command line and log = \n" + executable + " " + String.join(" ", args) + "\n"
                            + logContents(logFile));
        }
    }

    private List<String> commandLine() {
        List<String> args = new ArrayList<>(defaultCliArguments);
        args.add("-Dmaven.repo.local=" + getLocalRepository());
        if (autoclean) {
            args.add(CLEAN_CLI_ARGUMENT);
        }
        for (Map.Entry<String, String> property : systemProperties.entrySet()) {
            args.add("-D" + property.getKey() + "=" + property.getValue());
        }
        for (String cliArgument : cliArguments) {
            args.add(cliArgument.replace("${basedir}", basedir));
        }
        return args;
    }

    private static void writeLogFile(File logFile, ByteArrayOutputStream stdOut, ByteArrayOutputStream stdErr)
            throws VerificationException {
        try {
            Files.createDirectories(logFile.toPath().toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(logFile.toPath())) {
                stdOut.writeTo(out);
                stdErr.writeTo(out);
            }
        } catch (IOException e) {
            throw new VerificationException("Could not write log file: " + logFile, e);
        }
    }

    private static String logContents(File logFile) {
        try {
            return new String(Files.readAllBytes(logFile.toPath()), Charset.defaultCharset());
        } catch (IOException e) {
            return "(Error reading log contents: " + e.getMessage() + ")";
        }
    }

    /**
     * @return the version of the Maven this verifier runs
     */
    public String getMavenVersion() throws VerificationException {
        try {
            return executorHelper.mavenVersion();
        } catch (ExecutorException e) {
            throw new VerificationException("Failed to determine Maven version", e);
        }
    }

    // ----------------------------------------------------------------------
    // Configuration
    // ----------------------------------------------------------------------

    /**
     * Adds one command line argument as it is, spaces included. {@code ${basedir}} is replaced with
     * {@link #getBasedir()} when Maven runs.
     */
    public void addCliArgument(String cliArgument) {
        cliArguments.add(requireNonNull(cliArgument, "cliArgument"));
    }

    /**
     * Adds command line arguments, each as it is. {@code ${basedir}} is replaced with {@link #getBasedir()} when
     * Maven runs.
     */
    public void addCliArguments(String... cliArguments) {
        for (String cliArgument : cliArguments) {
            addCliArgument(cliArgument);
        }
    }

    /**
     * Adds an option as it would be typed on the command line: {@code "-pl module-a -am"} becomes the three arguments
     * {@code -pl}, {@code module-a} and {@code -am}, as maven-verifier 1.x split its options. A value with spaces
     * needs {@link #addCliArgument(String)}.
     */
    public void addCliOption(String option) {
        for (String argument : requireNonNull(option, "option").trim().split("\\s+")) {
            if (!argument.isEmpty()) {
                addCliArgument(argument);
            }
        }
    }

    /**
     * @return the arguments added so far, without the defaults and the system properties
     */
    public List<String> getCliArguments() {
        return Collections.unmodifiableList(cliArguments);
    }

    /**
     * Passes a system property to Maven as {@code -Dkey=value}; {@code null} removes it.
     */
    public void setSystemProperty(String key, String value) {
        if (value != null) {
            systemProperties.put(key, value);
        } else {
            systemProperties.remove(key);
        }
    }

    public Map<String, String> getSystemProperties() {
        return Collections.unmodifiableMap(systemProperties);
    }

    /**
     * Sets an environment variable for Maven; {@code null} removes it.
     */
    public void setEnvironmentVariable(String key, String value) {
        if (value != null) {
            environmentVariables.put(key, value);
        } else {
            environmentVariables.remove(key);
        }
    }

    public boolean isAutoclean() {
        return autoclean;
    }

    /**
     * Whether {@code maven-clean-plugin:clean} runs first, on by default.
     */
    public void setAutoclean(boolean autoclean) {
        this.autoclean = autoclean;
    }

    /**
     * Runs Maven forked even when the default mode is embedded.
     */
    public void setForkJvm(boolean forkJvm) {
        this.forkJvm = forkJvm;
    }

    public String getExecutable() {
        return executable;
    }

    /**
     * The command to run, {@code mvn} by default, for example a wrapper script.
     */
    public void setExecutable(String executable) {
        this.executable = requireNonNull(executable, "executable");
    }

    public String getBasedir() {
        return basedir;
    }

    /**
     * @return the name of the log file, relative to the base directory
     */
    public String getLogFileName() {
        return logFileName;
    }

    /**
     * @param logFileName the name of the log file, relative to the base directory
     */
    public void setLogFileName(String logFileName) {
        if (logFileName == null || logFileName.isEmpty()) {
            throw new IllegalArgumentException("log file name unspecified");
        }
        this.logFileName = logFileName;
    }

    private File getLogFile() {
        return new File(basedir, logFileName);
    }

    /**
     * @param executorTool computes the local repository and artifact paths, {@link DefaultExecutorTool} by default
     */
    public void setExecutorTool(ExecutorTool executorTool) {
        this.executorTool = requireNonNull(executorTool, "executorTool");
    }

    /**
     * Uses this local repository instead of the one the {@link ExecutorTool} finds.
     */
    public void setLocalRepo(String localRepository) {
        this.localRepository = localRepository;
    }

    /**
     * @return the local repository Maven runs with: the one {@link #setLocalRepo(String) set}, else the one the
     *     {@link ExecutorTool} finds for the arguments, system properties and base directory of this verifier
     */
    public String getLocalRepository() {
        if (localRepository != null) {
            return localRepository;
        }
        return executorTool.localRepository(toolRequest());
    }

    private ExecutorRequest.Builder toolRequest() {
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, String> property : systemProperties.entrySet()) {
            args.add("-D" + property.getKey() + "=" + property.getValue());
        }
        args.addAll(cliArguments);
        return ExecutorRequest.mavenBuilder().cwd(Paths.get(basedir)).arguments(args);
    }

    // ----------------------------------------------------------------------
    // Log
    // ----------------------------------------------------------------------

    /**
     * Fails if a line of the log has {@code [ERROR]}, except the noise of the Velocity in old Doxia versions.
     */
    public void verifyErrorFreeLog() throws VerificationException {
        for (String line : loadFile(getLogFile(), false)) {
            if (stripAnsi(line).contains("[ERROR]") && !isVelocityError(line)) {
                throw new VerificationException("Error in execution: " + line);
            }
        }
    }

    private static boolean isVelocityError(String line) {
        return line.contains("VM_global_library.vm") || line.contains("VM #") && line.contains("macro");
    }

    /**
     * Fails unless a line of the log contains the text, ANSI colours ignored.
     */
    public void verifyTextInLog(String text) throws VerificationException {
        if (!logContains(text)) {
            throw new VerificationException("Text not found in log: " + text);
        }
    }

    /**
     * Fails if a line of the log contains the text, ANSI colours ignored.
     */
    public void verifyTextNotInLog(String text) throws VerificationException {
        if (logContains(text)) {
            throw new VerificationException("Text found in log: " + text);
        }
    }

    private boolean logContains(String text) throws VerificationException {
        for (String line : loadFile(getLogFile(), false)) {
            if (stripAnsi(line).contains(text)) {
                return true;
            }
        }
        return false;
    }

    public static String stripAnsi(String msg) {
        return msg.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    // ----------------------------------------------------------------------
    // Files of the test project
    // ----------------------------------------------------------------------

    /**
     * @param filename relative to the base directory
     */
    public Properties loadProperties(String filename) throws VerificationException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(new File(basedir, filename).toPath())) {
            properties.load(in);
        } catch (IOException e) {
            throw new VerificationException("Error reading properties file", e);
        }
        return properties;
    }

    /**
     * @param filename relative to the base directory
     * @param encoding {@code null} or empty for the platform encoding
     * @return the non-empty lines
     */
    public List<String> loadLines(String filename, String encoding) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(new File(basedir, filename).toPath(), charset(encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    public List<String> loadLines(String filename) throws IOException {
        return loadLines(filename, null);
    }

    public List<String> loadFile(String basedir, String filename, boolean hasCommand) throws VerificationException {
        return loadFile(new File(basedir, filename), hasCommand);
    }

    /**
     * Loads the trimmed lines that are neither empty nor {@code #} comments. A {@code ${artifact:g:a:v:ext}} marker
     * becomes the artifact path, followed by the {@code maven-metadata*.xml} files next to the artifact and its
     * version directory.
     *
     * @param hasCommand whether each line starts with a command and a space before the path
     */
    public List<String> loadFile(File file, boolean hasCommand) throws VerificationException {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) {
            return lines;
        }
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), Charset.defaultCharset())) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("#") && !line.isEmpty()) {
                    lines.addAll(replaceArtifacts(line, hasCommand));
                }
            }
        } catch (IOException e) {
            throw new VerificationException(e);
        }
        return lines;
    }

    private List<String> replaceArtifacts(String line, boolean hasCommand) {
        int index = line.indexOf(ARTIFACT_MARKER);
        if (index < 0) {
            return Collections.singletonList(line);
        }
        String newLine = line.substring(0, index);
        int end = line.indexOf('}', index);
        if (end < 0) {
            throw new IllegalArgumentException("line does not contain ending artifact marker: '" + line + "'");
        }
        String[] coordinates =
                line.substring(index + ARTIFACT_MARKER.length(), end).split(":");
        if (coordinates.length != 4) {
            throw new IllegalArgumentException("Artifact must have 4 tokens: '" + line + "'");
        }
        newLine += getArtifactPath(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
        newLine += line.substring(end + 1);

        List<String> lines = new ArrayList<>();
        lines.add(newLine);
        String command = null;
        String filespec = newLine;
        if (hasCommand) {
            int space = newLine.indexOf(' ');
            command = newLine.substring(0, space);
            filespec = newLine.substring(space + 1);
        }
        File artifactDir = new File(filespec).getParentFile();
        addMetadataToList(artifactDir, command, lines);
        addMetadataToList(artifactDir != null ? artifactDir.getParentFile() : null, command, lines);
        return lines;
    }

    private static void addMetadataToList(File dir, String command, List<String> lines) {
        String[] files =
                dir != null ? dir.list((d, name) -> name.startsWith("maven-metadata") && name.endsWith(".xml")) : null;
        if (files != null) {
            for (String file : files) {
                String path = new File(dir, file).getPath();
                lines.add(command != null ? command + " " + path : path);
            }
        }
    }

    /**
     * Replaces each key of the map with its value in the source file and writes the result.
     *
     * @param srcPath relative to the base directory
     * @param dstPath relative to the base directory, may be {@code srcPath}
     * @param fileEncoding {@code null} or empty for the platform encoding
     * @return the written file
     */
    public File filterFile(String srcPath, String dstPath, String fileEncoding, Map<String, String> filterMap)
            throws IOException {
        Charset charset = charset(fileEncoding);
        String data = new String(Files.readAllBytes(new File(basedir, srcPath).toPath()), charset);
        for (Map.Entry<String, String> entry : filterMap.entrySet()) {
            data = data.replace(entry.getKey(), entry.getValue());
        }
        File dstFile = new File(basedir, dstPath);
        Files.createDirectories(dstFile.toPath().toAbsolutePath().getParent());
        Files.write(dstFile.toPath(), data.getBytes(charset));
        return dstFile;
    }

    /**
     * {@link #filterFile(String, String, String, Map)} with {@link #newDefaultFilterMap()}.
     */
    public File filterFile(String srcPath, String dstPath, String fileEncoding) throws IOException {
        return filterFile(srcPath, dstPath, fileEncoding, newDefaultFilterMap());
    }

    /**
     * @return a new modifiable map from {@code @basedir@} to the base directory and from {@code @baseurl@} to its
     *     {@code file:} URL
     */
    public Map<String, String> newDefaultFilterMap() {
        Map<String, String> filterMap = new HashMap<>();
        Path base = Paths.get(basedir).toAbsolutePath();
        filterMap.put("@basedir@", base.toString());
        filterMap.put("@baseurl@", base.toUri().toASCIIString());
        return filterMap;
    }

    /**
     * @param path relative to the base directory
     */
    public void deleteDirectory(String path) throws IOException {
        ResourceExtractor.deleteRecursively(new File(basedir, path).toPath());
    }

    /**
     * Fails unless the file exists. A relative path is relative to the base directory; {@code *} in the file name
     * matches any characters; {@code a.jar!/entry} checks an entry of a jar in the base directory.
     */
    public void verifyFilePresent(String file) throws VerificationException {
        verifyFilePresence(file, true);
    }

    /**
     * Fails if the file exists, with the paths of {@link #verifyFilePresent(String)}.
     */
    public void verifyFileNotPresent(String file) throws VerificationException {
        verifyFilePresence(file, false);
    }

    private void verifyFilePresence(String filePath, boolean wanted) throws VerificationException {
        if (filePath.contains("!/")) {
            verifyJarEntryPresence(filePath, wanted);
            return;
        }
        File expectedFile = new File(filePath);
        // on Windows, a path with a leading (back-)slash is relative to the current drive
        if (!expectedFile.isAbsolute() && !expectedFile.getPath().startsWith(File.separator)) {
            expectedFile = new File(basedir, filePath);
        }
        boolean found;
        if (expectedFile.getName().indexOf('*') > -1) {
            String pattern = expectedFile.getName().replace(".", "\\.").replace("*", ".*");
            String[] candidates = expectedFile.getParentFile().list((dir, name) -> name.matches(pattern));
            found = candidates != null && candidates.length > 0;
            if (found != wanted) {
                throw new VerificationException((wanted ? "Expected" : "Unwanted") + " file pattern was "
                        + (wanted ? "not found: " : "found: ") + expectedFile.getPath());
            }
        } else {
            found = expectedFile.exists();
            if (found != wanted) {
                throw new VerificationException((wanted ? "Expected" : "Unwanted") + " file was "
                        + (wanted ? "not found: " : "found: ") + expectedFile.getPath());
            }
        }
    }

    private void verifyJarEntryPresence(String filePath, boolean wanted) throws VerificationException {
        String base = Paths.get(basedir).toAbsolutePath().toUri().toASCIIString();
        String url = "jar:" + base + (base.endsWith("/") ? "" : "/") + filePath;
        boolean found;
        try (InputStream in = new URL(url).openStream()) {
            found = in != null;
        } catch (IOException e) {
            found = false;
        }
        if (found != wanted) {
            throw new VerificationException((wanted ? "Expected" : "Unwanted") + " JAR resource was "
                    + (wanted ? "not found: " : "found: ") + filePath);
        }
    }

    // ----------------------------------------------------------------------
    // Local repository
    // ----------------------------------------------------------------------

    /**
     * @return the absolute path of the artifact in the local repository
     */
    public String getArtifactPath(String groupId, String artifactId, String version, String type) {
        return getArtifactPath(groupId, artifactId, version, type, null);
    }

    /**
     * @param type an extension, or a packaging type such as {@code maven-plugin} or {@code test-jar} that is stored
     *     with another extension and classifier
     * @param classifier {@code null} or empty for none; a classifier of the type is used when none is given
     * @return the absolute path of the artifact in the local repository
     */
    public String getArtifactPath(String groupId, String artifactId, String version, String type, String classifier) {
        String[] extensionAndClassifier = extensionAndClassifier(type);
        String extension = extensionAndClassifier[0];
        if ((classifier == null || classifier.isEmpty()) && extensionAndClassifier[1] != null) {
            classifier = extensionAndClassifier[1];
        }
        String gav = groupId + ":" + artifactId + ":" + extension
                + (classifier != null && !classifier.isEmpty() ? ":" + classifier : "") + ":" + version;
        return new File(getLocalRepository(), executorTool.artifactPath(toolRequest(), gav, null)).getPath();
    }

    /**
     * Maps a type that does not name its own file to the extension and classifier it is stored with, as
     * {@code {extension, classifier}} with a {@code null} classifier for none. Override it for types of a suite.
     */
    protected String[] extensionAndClassifier(String type) {
        String[] known = TYPES.get(type);
        return known != null ? known.clone() : new String[] {type, null};
    }

    /**
     * @return the artifact file and the {@code maven-metadata*.xml} files next to it and in its artifact directory
     */
    public List<String> getArtifactFileNameList(String groupId, String artifactId, String version, String type) {
        List<String> files = new ArrayList<>();
        String artifactPath = getArtifactPath(groupId, artifactId, version, type);
        files.add(artifactPath);
        File versionDir = new File(artifactPath).getParentFile();
        addMetadataToList(versionDir, null, files);
        addMetadataToList(versionDir.getParentFile(), null, files);
        return files;
    }

    /**
     * @return the path of {@code maven-metadata-local.xml} of the group, artifact or version
     */
    public String getArtifactMetadataPath(String groupId, String artifactId, String version) {
        return getArtifactMetadataPath(groupId, artifactId, version, "maven-metadata-local.xml");
    }

    public String getArtifactMetadataPath(String groupId, String artifactId) {
        return getArtifactMetadataPath(groupId, artifactId, null);
    }

    /**
     * @param artifactId {@code null} for a group-level file
     * @param version {@code null} for an artifact-level file
     * @param filename the file name as it is in the local repository
     * @return the absolute path of the file in the local repository
     */
    public String getArtifactMetadataPath(String groupId, String artifactId, String version, String filename) {
        Path path = Paths.get(getLocalRepository()).resolve(groupId.replace('.', '/'));
        if (artifactId != null) {
            path = path.resolve(artifactId);
            if (version != null) {
                path = path.resolve(version);
            }
        }
        return path.resolve(filename).toString();
    }

    public void verifyArtifactPresent(String groupId, String artifactId, String version, String type)
            throws VerificationException {
        verifyFilePresence(getArtifactPath(groupId, artifactId, version, type), true);
    }

    public void verifyArtifactNotPresent(String groupId, String artifactId, String version, String type)
            throws VerificationException {
        verifyFilePresence(getArtifactPath(groupId, artifactId, version, type), false);
    }

    /**
     * Fails unless the artifact exists with exactly this content, read in the platform encoding.
     */
    public void verifyArtifactContent(String groupId, String artifactId, String version, String type, String content)
            throws IOException, VerificationException {
        String fileName = getArtifactPath(groupId, artifactId, version, type);
        if (!content.equals(new String(Files.readAllBytes(Paths.get(fileName)), Charset.defaultCharset()))) {
            throw new VerificationException("Content of " + fileName + " does not equal " + content);
        }
    }

    /**
     * Deletes the artifact file and the {@code maven-metadata*.xml} files next to it and in its artifact directory.
     */
    public void deleteArtifact(String groupId, String artifactId, String version, String type) throws IOException {
        for (String fileName : getArtifactFileNameList(groupId, artifactId, version, type)) {
            Files.deleteIfExists(Paths.get(fileName));
        }
    }

    /**
     * Deletes everything of the group in the local repository.
     */
    public void deleteArtifacts(String groupId) throws IOException {
        ResourceExtractor.deleteRecursively(Paths.get(getLocalRepository(), groupId.replace('.', '/')));
    }

    /**
     * Deletes everything of the version in the local repository.
     */
    public void deleteArtifacts(String groupId, String artifactId, String version) throws IOException {
        ResourceExtractor.deleteRecursively(
                Paths.get(getLocalRepository(), groupId.replace('.', '/'), artifactId, version));
    }

    private static Charset charset(String encoding) {
        return encoding == null || encoding.isEmpty() ? Charset.defaultCharset() : Charset.forName(encoding);
    }
}

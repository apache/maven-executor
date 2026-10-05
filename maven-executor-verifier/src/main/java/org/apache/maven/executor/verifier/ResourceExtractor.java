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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Copies test projects from the class path, a directory or a jar, to a directory where Maven can run on them.
 */
public final class ResourceExtractor {
    private ResourceExtractor() {}

    /**
     * Extracts the resource into a fresh directory of the same name under {@code maven.test.tmpdir}, or else
     * {@code java.io.tmpdir}. A resource in a directory on the class path is used where it is, without a copy.
     *
     * @param cl the class whose class loader and package resolve {@code resourcePath}
     * @param resourcePath the resource, for example {@code /it0001}
     * @return the directory to run Maven in
     */
    public static File simpleExtractResources(Class<?> cl, String resourcePath) throws IOException {
        File tempDir = new File(System.getProperty("maven.test.tmpdir", System.getProperty("java.io.tmpdir")));
        File testDir = new File(tempDir, resourcePath);
        deleteRecursively(testDir.toPath());
        return extractResourcePath(cl, resourcePath, tempDir, false);
    }

    public static File extractResourcePath(String resourcePath, File dest) throws IOException {
        return extractResourcePath(ResourceExtractor.class, resourcePath, dest);
    }

    public static File extractResourcePath(Class<?> cl, String resourcePath, File dest) throws IOException {
        return extractResourcePath(cl, resourcePath, dest, false);
    }

    public static File extractResourcePath(Class<?> cl, String resourcePath, File tempDir, boolean alwaysExtract)
            throws IOException {
        return extractResourceToDestination(cl, resourcePath, new File(tempDir, resourcePath), alwaysExtract);
    }

    /**
     * @param alwaysExtract whether a resource in a directory on the class path is copied too; one in a jar always is
     * @return {@code destination}, or the resource itself when it is in a directory and not copied
     */
    public static File extractResourceToDestination(
            Class<?> cl, String resourcePath, File destination, boolean alwaysExtract) throws IOException {
        URL url = cl.getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }
        if ("jar".equalsIgnoreCase(url.getProtocol())) {
            extractResourcePathFromJar(cl, jarFile(url), resourcePath, destination);
            return destination;
        }
        File resourceFile;
        try {
            resourceFile = new File(new URI(url.toExternalForm()));
        } catch (URISyntaxException e) {
            throw new IOException("Couldn't convert URL to File: " + url, e);
        }
        if (!alwaysExtract) {
            return resourceFile;
        }
        copy(resourceFile.toPath(), destination.toPath());
        return destination;
    }

    private static void extractResourcePathFromJar(Class<?> cl, File jarFile, String resourcePath, File dest)
            throws IOException {
        String absolutePath = resourcePath.startsWith("/")
                ? resourcePath.substring(1)
                : cl.getPackage().getName().replace('.', '/') + "/" + resourcePath;
        String directory = absolutePath + "/";
        try (ZipFile zip = new ZipFile(jarFile, ZipFile.OPEN_READ)) {
            if (zip.getEntry(directory) == null) {
                Files.createDirectories(dest.toPath().toAbsolutePath().getParent());
                try (InputStream in = zip.getInputStream(zip.getEntry(absolutePath))) {
                    Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(directory)) {
                    continue;
                }
                Path target = dest.toPath()
                        .resolve(entry.getName().substring(directory.length()))
                        .normalize();
                if (!target.startsWith(dest.toPath().normalize())) {
                    throw new IOException("Entry outside of the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static File jarFile(URL url) throws IOException {
        String file = url.getFile();
        int index = file.indexOf('!');
        if (index == -1) {
            throw new IOException(url.toExternalForm() + " does not have a '!'");
        }
        try {
            return new File(new URI(file.substring(0, index)));
        } catch (URISyntaxException e) {
            throw new IOException("Couldn't convert URL to File: " + url, e);
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(
                        file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

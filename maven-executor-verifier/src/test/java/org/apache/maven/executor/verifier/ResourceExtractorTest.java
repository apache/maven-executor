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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceExtractorTest {
    @TempDir
    Path temp;

    /** Loaded from the test jar, so that its resources are looked up there. */
    public static class Marker {}

    @Test
    void extractsADirectoryWithSeveralFilesFromAJar() throws Exception {
        Path jar = temp.resolve("projects.jar");
        String markerClass = Marker.class.getName().replace('.', '/') + ".class";
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(markerClass));
            try (InputStream in = Marker.class.getClassLoader().getResourceAsStream(markerClass)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    zip.write(buffer, 0, n);
                }
            }
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("it0001/"));
            zip.closeEntry();
            entry(zip, "it0001/pom.xml", "<project/>");
            entry(zip, "it0001/src/main/resources/a.txt", "a");
            entry(zip, "it0001/src/main/resources/b.txt", "b");
            entry(zip, "it0002/pom.xml", "<other/>");
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            Class<?> marker = loader.loadClass(Marker.class.getName());
            File dir = ResourceExtractor.extractResourcePath(
                    marker, "/it0001", temp.resolve("out").toFile());

            assertEquals(temp.resolve("out").resolve("it0001").toFile(), dir);
            assertEquals("<project/>", read(dir.toPath().resolve("pom.xml")));
            assertEquals("a", read(dir.toPath().resolve("src/main/resources/a.txt")));
            assertEquals("b", read(dir.toPath().resolve("src/main/resources/b.txt")));
            assertTrue(!Files.exists(temp.resolve("out").resolve("it0002")));
        }
    }

    @Test
    void usesOrCopiesADirectoryOnTheClassPath() throws Exception {
        File inPlace = ResourceExtractor.extractResourcePath(getClass(), "/projects/simple", temp.toFile());
        assertTrue(new File(inPlace, "pom.xml").isFile());
        assertTrue(!inPlace.toPath().startsWith(temp));

        File copy = ResourceExtractor.extractResourcePath(getClass(), "/projects/simple", temp.toFile(), true);
        assertEquals(temp.resolve("projects/simple").toFile(), copy);
        assertTrue(new File(copy, "pom.xml").isFile());
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}

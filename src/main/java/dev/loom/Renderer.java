package dev.loom;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Renders the file plan produced by {@link Resolver} into an on-disk project. */
final class Renderer {

    static String template(String name) {
        try (InputStream in = Renderer.class.getResourceAsStream("/templates/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing template on classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Renders every planned file. Returns the number of files written. */
    static int generate(Resolver.Result r, Path root) throws IOException {
        Map<String, Object> model = r.model;
        for (String[] plan : r.files) {
            String rendered = Mustache.render(template(plan[0]), model);
            Path target = root.resolve(plan[1]);
            Files.createDirectories(target.getParent());
            Files.writeString(target, rendered, StandardCharsets.UTF_8);
        }
        return r.files.size();
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) Files.delete(p);
        }
    }

    /** Zips the generated project (paths inside the archive start with the project name). */
    static Path zip(Path projectRoot) throws IOException {
        Path zip = projectRoot.resolveSibling(projectRoot.getFileName() + ".zip");
        Files.deleteIfExists(zip);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(projectRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path f : files) {
                String entry = projectRoot.getFileName() + "/" + projectRoot.relativize(f).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entry));
                zos.write(Files.readAllBytes(f));
                zos.closeEntry();
            }
        }
        return zip;
    }
}

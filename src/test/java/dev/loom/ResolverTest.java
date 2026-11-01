package dev.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolverTest {

    private static Resolver.Result resolve(Spec s) {
        s.normalize();
        return Resolver.resolve(s);
    }

    private static boolean plans(Resolver.Result r, String outputSuffix) {
        return r.files.stream().anyMatch(f -> f[1].endsWith(outputSuffix));
    }

    @Test
    void versionComparison() {
        assertTrue(Resolver.isBefore("3.1.9", 3, 2, 0));
        assertFalse(Resolver.isBefore("3.2.0", 3, 2, 0));
        assertFalse(Resolver.isBefore("3.5.6", 3, 2, 0));
    }

    @Test
    void javaProjectGetsJavaSourcesAndPom() {
        Resolver.Result r = resolve(Spec.defaults());
        assertTrue(plans(r, "pom.xml"));
        assertTrue(plans(r, "Application.java"));
        assertFalse(plans(r, "Application.kt"));
    }

    @Test
    void kotlinProjectGetsKotlinSources() {
        Spec s = Spec.defaults();
        s.lang = "kotlin";
        Resolver.Result r = resolve(s);
        assertTrue(plans(r, "Application.kt"));
        assertFalse(plans(r, "Application.java"));
    }

    @Test
    void dockerAndCiCanBeDisabled() {
        Spec s = Spec.defaults();
        s.docker = false;
        s.ci = false;
        Resolver.Result r = resolve(s);
        assertFalse(plans(r, "Dockerfile"));
        assertFalse(plans(r, "ci.yml"));
    }

    @Test
    void generatedFilesHaveNoUnrenderedTags(@TempDir Path dir) throws Exception {
        Spec s = Spec.defaults();
        s.db = "postgres";
        s.migrations = "flyway";
        s.cache = "redis";
        s.mq = "kafka";
        Resolver.Result r = resolve(s);

        assertEquals(r.files.size(), Renderer.generate(r, dir));
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                assertFalse(Files.readString(p).contains("{{"), "unrendered tag in " + p);
            }
        }
    }
}

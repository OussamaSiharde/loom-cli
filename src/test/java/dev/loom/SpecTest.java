package dev.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecTest {

    private static boolean mentions(List<String> notes, String text) {
        return notes.stream().anyMatch(n -> n.contains(text));
    }

    @Test
    void boot3BumpsJava8To17() {
        Spec s = Spec.defaults();
        s.javaVersion = "8";
        assertTrue(mentions(s.normalize(), "bumped to 17"));
        assertEquals("17", s.javaVersion);
    }

    @Test
    void kotlinDisablesLombok() {
        Spec s = Spec.defaults();
        s.lang = "kotlin";
        s.lombok = true;
        s.normalize();
        assertFalse(s.lombok);
    }

    @Test
    void kotlinReplacesTestngWithJunit() {
        Spec s = Spec.defaults();
        s.lang = "kotlin";
        s.tests = "testng";
        s.normalize();
        assertEquals("junit", s.tests);
    }

    @Test
    void webfluxForcesJackson() {
        Spec s = Spec.defaults();
        s.web = "webflux";
        s.json = "gson";
        s.normalize();
        assertEquals("jackson", s.json);
    }

    @Test
    void openapiNeedsAWebLayer() {
        Spec s = Spec.defaults();
        s.web = "none";
        s.openapi = true;
        assertTrue(mentions(s.normalize(), "springdoc skipped"));
        assertFalse(s.openapi);
    }

    @Test
    void boot2FallsBackFromRestClient() {
        Spec s = Spec.defaults();
        s.boot = "2";
        s.javaVersion = "11";
        s.http = "restclient";
        s.normalize();
        assertEquals("resttemplate", s.http);
    }

    @Test
    void boot2ReplacesR2dbcWithJpa() {
        Spec s = Spec.defaults();
        s.boot = "2";
        s.javaVersion = "11";
        s.db = "postgres";
        s.data = "r2dbc";
        s.normalize();
        assertEquals("jpa", s.data);
    }

    @Test
    void mongoSkipsSqlMigrations() {
        Spec s = Spec.defaults();
        s.db = "mongo";
        s.migrations = "flyway";
        s.normalize();
        assertEquals("none", s.migrations);
    }

    @Test
    void validDefaultsProduceNoNotes() {
        assertTrue(Spec.defaults().normalize().isEmpty());
    }
}

package dev.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MustacheTest {

    @Test
    void interpolatesVariablesRaw() {
        assertEquals("a <b> c", Mustache.render("a {{x}} c", Map.of("x", "<b>")));
    }

    @Test
    void rendersSectionOnlyWhenTruthy() {
        assertEquals("yes", Mustache.render("{{#on}}yes{{/on}}", Map.of("on", true)));
        assertEquals("", Mustache.render("{{#on}}yes{{/on}}", Map.of("on", false)));
    }

    @Test
    void rendersInvertedSectionWhenFalsy() {
        assertEquals("no", Mustache.render("{{^on}}no{{/on}}", Map.of("on", false)));
        assertEquals("", Mustache.render("{{^on}}no{{/on}}", Map.of("on", true)));
    }

    @Test
    void iteratesLists() {
        assertEquals("a,b,", Mustache.render("{{#xs}}{{.}},{{/xs}}", Map.of("xs", List.of("a", "b"))));
    }

    @Test
    void dropsComments() {
        assertEquals("ab", Mustache.render("a{{! note }}b", Map.of()));
    }

    @Test
    void standaloneSectionTagsLeaveNoBlankLines() {
        assertEquals("start\nbody\nend\n",
                Mustache.render("start\n{{#on}}\nbody\n{{/on}}\nend\n", Map.of("on", true)));
    }
}

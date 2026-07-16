package dev.loom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Mustache-subset renderer. Supports:
 *   {{var}}            variable interpolation (raw, no HTML escaping)
 *   {{#name}}...{{/name}}  section: rendered when truthy; iterates lists
 *   {{^name}}...{{/name}}  inverted section: rendered when falsy
 *   {{! comment}}      comment, dropped
 *   {{.}}              current list item
 *
 * Section/comment tags that sit alone on a line are stripped together with
 * the line, so templates stay readable without leaking blank lines into the
 * generated files. Just enough Mustache for Loom - and zero dependencies.
 */
public final class Mustache {

    public static String render(String template, Map<String, Object> model) {
        List<Object> stack = new ArrayList<>();
        stack.add(model);
        return new Mustache(template).renderRange(0, template.length(), stack);
    }

    private final String t;

    private Mustache(String template) {
        this.t = template;
    }

    private String renderRange(int start, int end, List<Object> stack) {
        StringBuilder out = new StringBuilder();
        int i = start;
        while (i < end) {
            int open = t.indexOf("{{", i);
            if (open < 0 || open >= end) {
                out.append(t, i, end);
                break;
            }
            int close = t.indexOf("}}", open + 2);
            if (close < 0 || close + 2 > end) {
                out.append(t, i, end);
                break;
            }
            String tag = t.substring(open + 2, close).trim();
            if (tag.isEmpty()) {
                out.append(t, i, close + 2);
                i = close + 2;
                continue;
            }
            char kind = tag.charAt(0);
            if (kind == '#' || kind == '^') {
                String name = tag.substring(1).trim();
                int[] closer = findSectionClose(name, close + 2, end);
                if (closer == null) { // unbalanced: emit literally
                    out.append(t, i, close + 2);
                    i = close + 2;
                    continue;
                }
                int openLs = standaloneLineStart(open);
                int openLe = standaloneLineEnd(close + 2);
                boolean openStandalone = openLs >= 0 && openLe >= 0;
                int emitEnd = openStandalone ? openLs : open;
                int bodyStart = openStandalone ? openLe : close + 2;

                int closeLs = standaloneLineStart(closer[0]);
                int closeLe = standaloneLineEnd(closer[1]);
                boolean closeStandalone = closeLs >= 0 && closeLe >= 0;
                int bodyEnd = closeStandalone ? closeLs : closer[0];
                int after = closeStandalone ? closeLe : closer[1];

                if (emitEnd > i) out.append(t, i, emitEnd);

                Object v = lookup(name, stack);
                boolean truthy = truthy(v);
                if (kind == '#' && truthy) {
                    if (v instanceof List<?> list) {
                        for (Object item : list) {
                            out.append(renderRange(bodyStart, bodyEnd, push(stack, item)));
                        }
                    } else if (v instanceof Map<?, ?> map) {
                        out.append(renderRange(bodyStart, bodyEnd, push(stack, map)));
                    } else {
                        out.append(renderRange(bodyStart, bodyEnd, stack));
                    }
                } else if (kind == '^' && !truthy) {
                    out.append(renderRange(bodyStart, bodyEnd, stack));
                }
                i = after;
            } else if (kind == '!') {
                int ls = standaloneLineStart(open);
                int le = standaloneLineEnd(close + 2);
                if (ls >= 0 && le >= 0) {
                    if (ls > i) out.append(t, i, ls);
                    i = le;
                } else {
                    out.append(t, i, open);
                    i = close + 2;
                }
            } else {
                out.append(t, i, open);
                Object v = lookup(tag, stack);
                if (v != null) out.append(v);
                i = close + 2;
            }
        }
        return out.toString();
    }

    /** Finds the matching close tag, honouring nesting of same-named sections. */
    private int[] findSectionClose(String name, int from, int end) {
        int depth = 1;
        int i = from;
        while (i < end) {
            int open = t.indexOf("{{", i);
            if (open < 0 || open >= end) return null;
            int close = t.indexOf("}}", open + 2);
            if (close < 0 || close + 2 > end) return null;
            String tag = t.substring(open + 2, close).trim();
            if (tag.equals("#" + name) || tag.equals("^" + name)) {
                depth++;
            } else if (tag.equals("/" + name)) {
                depth--;
                if (depth == 0) return new int[]{open, close + 2};
            }
            i = close + 2;
        }
        return null;
    }

    /** Line start index if everything before {@code pos} on its line is whitespace, else -1. */
    private int standaloneLineStart(int pos) {
        int ls = t.lastIndexOf('\n', pos - 1) + 1;
        for (int j = ls; j < pos; j++) {
            char c = t.charAt(j);
            if (c != ' ' && c != '\t') return -1;
        }
        return ls;
    }

    /** Index just past the trailing newline if only whitespace follows {@code pos} on its line, else -1. */
    private int standaloneLineEnd(int pos) {
        int j = pos;
        while (j < t.length() && (t.charAt(j) == ' ' || t.charAt(j) == '\t')) j++;
        if (j >= t.length()) return j;
        if (t.charAt(j) == '\n') return j + 1;
        if (t.charAt(j) == '\r' && j + 1 < t.length() && t.charAt(j + 1) == '\n') return j + 2;
        return -1;
    }

    private static Object lookup(String key, List<Object> stack) {
        if (key.equals(".")) {
            for (Object ctx : stack) {
                if (!(ctx instanceof Map)) return ctx;
            }
            return null;
        }
        for (Object ctx : stack) {
            if (ctx instanceof Map<?, ?> m && m.containsKey(key)) {
                return m.get(key);
            }
        }
        return null;
    }

    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    private static List<Object> push(List<Object> stack, Object item) {
        List<Object> next = new ArrayList<>(stack.size() + 1);
        next.add(item);
        next.addAll(stack);
        return next;
    }
}

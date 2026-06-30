package com.aozainkmc.sigillum.glyph;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlyphSemantics {
    private static final Map<String, List<String>> WORD_TAGS = new HashMap<>();

    static {
        WORD_TAGS.put("镇", List.of("sigillum.suppress", "sigillum.control"));
        WORD_TAGS.put("封", List.of("sigillum.seal", "sigillum.ward"));
        WORD_TAGS.put("退", List.of("sigillum.repel", "sigillum.cleanse"));
        WORD_TAGS.put("引", List.of("sigillum.lure", "sigillum.pull"));
        WORD_TAGS.put("净", List.of("sigillum.purify", "sigillum.heal"));
        WORD_TAGS.put("明", List.of("sigillum.reveal", "sigillum.light"));
    }

    private GlyphSemantics() {}

    public static List<String> tagsFor(String word) {
        return WORD_TAGS.getOrDefault(word, Collections.emptyList());
    }

    public static boolean isKnown(String word) {
        return WORD_TAGS.containsKey(word);
    }

    public static Set<String> words() {
        return Collections.unmodifiableSet(WORD_TAGS.keySet());
    }
}
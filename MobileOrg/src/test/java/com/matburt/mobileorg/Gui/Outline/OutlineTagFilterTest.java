package com.matburt.mobileorg.Gui.Outline;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class OutlineTagFilterTest {

    private Set<String> tags(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

    // === matchesTags pure function tests ===

    @Test
    public void orMode_matchesAny() {
        assertTrue(OutlineTagFilter.matchesTags("work:urgent", null, tags("work"), false));
    }

    @Test
    public void orMode_noMatch() {
        assertFalse(OutlineTagFilter.matchesTags("home:personal", null, tags("work"), false));
    }

    @Test
    public void orMode_inheritedTagsMatch() {
        assertTrue(OutlineTagFilter.matchesTags(null, "project:work", tags("work"), false));
    }

    @Test
    public void andMode_matchesAll() {
        assertTrue(OutlineTagFilter.matchesTags("work:urgent", null, tags("work", "urgent"), true));
    }

    @Test
    public void andMode_missingOne() {
        assertFalse(OutlineTagFilter.matchesTags("work:home", null, tags("work", "urgent"), true));
    }

    @Test
    public void nullTags() {
        assertFalse(OutlineTagFilter.matchesTags(null, null, tags("work"), false));
    }

    @Test
    public void emptyTags() {
        assertFalse(OutlineTagFilter.matchesTags("", "", tags("work"), false));
    }

    @Test
    public void mixedOwnAndInherited() {
        assertTrue(OutlineTagFilter.matchesTags("home", "work", tags("work"), false));
    }

    @Test
    public void emptySelectedTags() {
        assertFalse(OutlineTagFilter.matchesTags("work", null, new HashSet<String>(), false));
    }

    @Test
    public void andMode_inheritedCompletesOwn() {
        // own=home, inherited=work => combined={home,work}. AND(work,home) => true
        assertTrue(OutlineTagFilter.matchesTags("home", "work", tags("work", "home"), true));
    }

    // === isActive / clearAll tests ===

    @Test
    public void isActive_falseWhenEmpty() {
        OutlineTagFilter filter = new OutlineTagFilter();
        assertFalse(filter.isActive());
    }

    @Test
    public void isActive_trueAfterSelection() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        assertTrue(filter.isActive());
    }

    @Test
    public void clearAll_deactivates() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.clearAll();
        assertFalse(filter.isActive());
    }

    @Test
    public void setSelectedTags_roundTrip() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setSelectedTags(new String[]{"work", "urgent"});
        String[] result = filter.getSelectedTagsArray();
        assertEquals(2, result.length);
        assertTrue(Arrays.asList(result).contains("work"));
        assertTrue(Arrays.asList(result).contains("urgent"));
    }

    @Test
    public void setTagSelected_remove() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.setTagSelected("urgent", true);
        filter.setTagSelected("work", false);
        assertTrue(filter.isActive());
        String[] result = filter.getSelectedTagsArray();
        assertEquals(1, result.length);
        assertEquals("urgent", result[0]);
    }

    @Test
    public void andMode_defaultFalse() {
        OutlineTagFilter filter = new OutlineTagFilter();
        assertFalse(filter.isAndMode());
    }

    @Test
    public void andMode_setAndGet() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setAndMode(true);
        assertTrue(filter.isAndMode());
    }

    // === Additional edge case tests ===

    @Test
    public void orMode_singleTagMatch() {
        assertTrue(OutlineTagFilter.matchesTags("work", null, tags("work"), false));
    }

    @Test
    public void orMode_singleTagNoMatch() {
        assertFalse(OutlineTagFilter.matchesTags("home", null, tags("work"), false));
    }

    @Test
    public void andMode_singleTagMatch() {
        assertTrue(OutlineTagFilter.matchesTags("work", null, tags("work"), true));
    }

    @Test
    public void andMode_singleTagNoMatch() {
        assertFalse(OutlineTagFilter.matchesTags("home", null, tags("work"), true));
    }

    @Test
    public void orMode_multipleSelectedOneMatches() {
        // node has "work", selected has "home","work","urgent" → OR mode matches
        assertTrue(OutlineTagFilter.matchesTags("work", null, tags("home", "work", "urgent"), false));
    }

    @Test
    public void andMode_inheritedOnlyMatches() {
        // own=null, inherited="work", selected={"work"} → AND single tag matches
        assertTrue(OutlineTagFilter.matchesTags(null, "work", tags("work"), true));
    }

    @Test
    public void orMode_colonSeparatedMultipleTags() {
        // Tags stored as "work:urgent" (colon-separated)
        assertTrue(OutlineTagFilter.matchesTags("work:urgent", null, tags("urgent"), false));
    }

    @Test
    public void clearAll_resetsState() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.setTagSelected("home", true);
        filter.setAndMode(true);
        filter.clearAll();
        assertFalse(filter.isActive());
        assertFalse(filter.isAndMode());
        assertEquals(0, filter.getSelectedTagsArray().length);
    }

    @Test
    public void setSelectedTags_emptyArrayDeactivates() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.setSelectedTags(new String[]{});
        assertFalse(filter.isActive());
    }

    @Test
    public void setTagSelected_idempotent() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.setTagSelected("work", true);
        String[] result = filter.getSelectedTagsArray();
        assertEquals(1, result.length);
        assertEquals("work", result[0]);
    }
}

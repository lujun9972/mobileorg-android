package com.matburt.mobileorg.Gui.Outline;

import android.content.ContentResolver;
import android.database.Cursor;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OutlineTagFilter {

    private Set<String> selectedTags = new HashSet<>();
    private boolean andMode = false;
    private Set<Long> matchingNodeIds = new HashSet<>();
    private Set<Long> containerIds = new HashSet<>();

    public OutlineTagFilter() {}

    public void setSelectedTags(String[] tags) {
        selectedTags.clear();
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    selectedTags.add(tag);
                }
            }
        }
    }

    public void setAndMode(boolean andMode) {
        this.andMode = andMode;
    }

    public void setTagSelected(String tag, boolean selected) {
        if (selected) {
            selectedTags.add(tag);
        } else {
            selectedTags.remove(tag);
        }
    }

    public void clearAll() {
        selectedTags.clear();
        matchingNodeIds.clear();
        containerIds.clear();
    }

    public boolean isActive() {
        return !selectedTags.isEmpty();
    }

    public boolean matches(long nodeId) {
        return matchingNodeIds.contains(nodeId);
    }

    public boolean isContainer(long nodeId) {
        return containerIds.contains(nodeId);
    }

    public boolean shouldShow(long nodeId) {
        return matches(nodeId) || isContainer(nodeId);
    }

    public void rebuild(ContentResolver resolver) {
        matchingNodeIds.clear();
        containerIds.clear();

        if (!isActive()) {
            return;
        }

        HashMap<Long, Long> parentMap = new HashMap<>();

        Cursor cursor = new OrgNodeRepository(resolver).getTagFilterCursor();

        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long parentId = cursor.getLong(1);
                String tags = cursor.getString(2);
                String tagsInherited = cursor.getString(3);

                parentMap.put(id, parentId);

                if (matchesTags(tags, tagsInherited, selectedTags, andMode)) {
                    matchingNodeIds.add(id);
                }
            }
        } finally {
            cursor.close();
        }

        for (Long matchId : matchingNodeIds) {
            Long ancestorId = parentMap.get(matchId);
            while (ancestorId != null && ancestorId != -1 && !matchingNodeIds.contains(ancestorId)) {
                containerIds.add(ancestorId);
                ancestorId = parentMap.get(ancestorId);
            }
        }
    }

    public String[] getSelectedTagsArray() {
        return selectedTags.toArray(new String[0]);
    }

    public boolean isAndMode() {
        return andMode;
    }

    /** Pure function: testable without ContentResolver. */
    static boolean matchesTags(String tags, String tagsInherited,
                               Set<String> selectedTags, boolean andMode) {
        Set<String> nodeTags = new HashSet<>();
        if (tags != null) {
            for (String t : tags.split(":"))
                if (!t.isEmpty()) nodeTags.add(t);
        }
        if (tagsInherited != null) {
            for (String t : tagsInherited.split(":"))
                if (!t.isEmpty()) nodeTags.add(t);
        }
        if (andMode) {
            return nodeTags.containsAll(selectedTags);
        } else {
            for (String t : selectedTags)
                if (nodeTags.contains(t)) return true;
            return false;
        }
    }
}

package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

/**
 * Repository for all OrgNode data access operations.
 *
 * Holds a ContentResolver and provides methods that were previously
 * scattered across OrgNode (as instance methods taking ContentResolver)
 * and OrgProviderUtils (as static methods taking ContentResolver).
 *
 * @see OrgNode — pure domain model (fields, toString, equals, payload parsing)
 */
public class OrgNodeRepository {

    private final ContentResolver resolver;

    public OrgNodeRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    // =====================================================================
    // CRUD
    // =====================================================================

    public OrgNode getById(long id) throws OrgNodeNotFoundException {
        Cursor cursor = resolver.query(OrgData.buildIdUri(id),
                OrgData.DEFAULT_COLUMNS, null, null, null);
        if (cursor == null || !cursor.moveToFirst())
            throw new OrgNodeNotFoundException("Node with id \"" + id + "\" not found");
        OrgNode node = new OrgNode(cursor);
        cursor.close();
        return node;
    }

    /** Insert or update the node. Sets node.id on insert. */
    public void write(OrgNode node) {
        if (node.id < 0)
            insertNode(node);
        else
            updateNode(node);
    }

    private void insertNode(OrgNode node) {
        Uri uri = resolver.insert(OrgData.CONTENT_URI, getContentValues(node));
        node.id = Long.parseLong(OrgData.getId(uri));
    }

    private void updateNode(OrgNode node) {
        resolver.update(OrgData.buildIdUri(node.id), getContentValues(node), null, null);
    }

    /** Update this node and all other nodes sharing the same :ID: property. */
    public void updateAllNodes(OrgNode node) {
        updateNode(node);

        String nodeId = getNodeId(node);
        if (!nodeId.startsWith("olp:")) {
            String nodeIdQuery = "%" + nodeId + "%";
            resolver.update(OrgData.CONTENT_URI, getSimpleContentValues(node),
                    OrgData.PAYLOAD + " LIKE ?", new String[]{nodeIdQuery});
        }
    }

    public void deleteNode(OrgNode node) {
        OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.DELETE, resolver);
        edit.write(resolver);
        resolver.delete(OrgData.buildIdUri(node.id), null, null);
    }

    // =====================================================================
    // Tree traversal
    // =====================================================================

    public ArrayList<OrgNode> getChildren(long nodeId) {
        String sort = nodeId == -1 ? OrgData.NAME_SORT : null;
        Cursor childCursor = resolver.query(OrgData.buildChildrenUri(nodeId),
                OrgData.DEFAULT_COLUMNS, null, null, sort);
        ArrayList<OrgNode> result = cursorToNodeList(childCursor);
        childCursor.close();
        return result;
    }

    public OrgNode getChild(long parentId, String name) throws OrgNodeNotFoundException {
        ArrayList<OrgNode> children = getChildren(parentId);
        for (OrgNode child : children) {
            if (child.name.equals(name))
                return child;
        }
        throw new OrgNodeNotFoundException("Couldn't find child of node "
                + parentId + " with name " + name);
    }

    public boolean hasChildren(long nodeId) {
        Cursor childCursor = resolver.query(OrgData.buildChildrenUri(nodeId),
                OrgData.DEFAULT_COLUMNS, null, null, null);
        int childCount = childCursor.getCount();
        childCursor.close();
        return childCount > 0;
    }

    public OrgNode getParent(long nodeId) throws OrgNodeNotFoundException {
        OrgNode node = getById(nodeId);
        return getById(node.parentId);
    }

    public ArrayList<String> getChildrenStringArray(long nodeId) {
        ArrayList<String> result = new ArrayList<String>();
        for (OrgNode node : getChildren(nodeId))
            result.add(node.name);
        return result;
    }

    public ArrayList<String> getSiblingsStringArray(long nodeId) throws OrgNodeNotFoundException {
        OrgNode node = getById(nodeId);
        OrgNode parent = getParent(nodeId);
        return getChildrenStringArray(parent.id);
    }

    public OrgNode getSibling(long nodeId, String name) throws OrgNodeNotFoundException {
        OrgNode parent = getParent(nodeId);
        return getChild(parent.id, name);
    }

    // =====================================================================
    // Node identity & location
    // =====================================================================

    /** Returns the :ID:, :ORIGINAL_ID:, or olp link for a node. */
    public String getNodeId(OrgNode node) {
        node.preparePayload();

        String id = node.getOrgNodePayload().getId();
        if (id != null && !id.equals(""))
            return id;
        else
            return getOlpId(node);
    }

    public String getOlpId(OrgNode node) {
        StringBuilder result = new StringBuilder();

        ArrayList<OrgNode> nodesFromRoot;
        try {
            nodesFromRoot = getNodePathFromTopLevel(node.parentId);
        } catch (IllegalStateException e) {
            return "";
        }

        if (nodesFromRoot.size() == 0) {
            try {
                return "olp:" + getOrgFile(node).name;
            } catch (OrgFileNotFoundException e) {
                return "";
            }
        }

        OrgNode topNode = nodesFromRoot.get(0);
        nodesFromRoot.remove(0);
        result.append("olp:").append(getFilename(topNode)).append(":");

        for (OrgNode n : nodesFromRoot)
            result.append(getStrippedNameForOlpPathLink(n.name)).append("/");

        result.append(getStrippedNameForOlpPathLink(node.name));
        return result.toString();
    }

    /** Build the ancestor chain from a node back to its file root. */
    public ArrayList<OrgNode> getNodePathFromTopLevel(long nodeId) {
        ArrayList<OrgNode> nodes = new ArrayList<OrgNode>();

        long currentId = nodeId;
        while (currentId >= 0) {
            try {
                OrgNode node = getById(currentId);
                nodes.add(node);
                currentId = node.parentId;
            } catch (OrgNodeNotFoundException e) {
                throw new IllegalStateException(
                        "Couldn't build entire path to root from a given node");
            }
        }

        Collections.reverse(nodes);
        return nodes;
    }

    /** Resolve an OLP path to the actual OrgNode. */
    public OrgNode getOrgNodeFromOlpPath(String olpPath)
            throws OrgNodeNotFoundException, OrgFileNotFoundException {
        if (olpPath == null || olpPath.equals(""))
            throw new IllegalArgumentException("Empty Olp path received");

        Matcher matcher = Pattern.compile("olp:([^:]+):?" + "(.*)").matcher(olpPath);

        String filename;
        String[] nodes = new String[0];
        if (matcher.find()) {
            filename = matcher.group(1);

            if (matcher.group(2) != null && !matcher.group(2).trim().equals("")) {
                nodes = matcher.group(2).split("/");
            }
        } else
            throw new IllegalArgumentException("Olp path " + olpPath + " is not valid");

        OrgFile file = new OrgFile(filename, resolver);
        OrgNode node = getById(file.nodeId);

        for (String nodeName : nodes)
            node = getChild(node.id, nodeName);

        return node;
    }

    /**
     * For agenda nodes, find the original source node via :ID: or :ORIGINAL_ID:.
     * For non-agenda nodes, returns the same node.
     */
    public OrgNode findOriginalNode(OrgNode node) {
        if (node.parentId == -1)
            return node;

        if (!getFilename(node).equals(OrgFile.AGENDA_FILE))
            return node;

        String nodeIdStr = getNodeId(node);
        if (!nodeIdStr.startsWith("olp:")) {
            String nodeIdQuery = OrgData.PAYLOAD + " LIKE '%" + nodeIdStr + "%'";
            try {
                OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
                if (agendaFile != null)
                    nodeIdQuery += " AND NOT " + OrgData.FILE_ID + "=" + agendaFile.nodeId;
            } catch (OrgFileNotFoundException e) {
            }

            Cursor query = resolver.query(OrgData.CONTENT_URI,
                    OrgData.DEFAULT_COLUMNS, nodeIdQuery, null, null);
            try {
                OrgNode found = new OrgNode(query);
                query.close();
                return found;
            } catch (OrgNodeNotFoundException e) {
                if (query != null)
                    query.close();
            }
        }

        return node;
    }

    // =====================================================================
    // Metadata queries
    // =====================================================================

    public String getFilename(OrgNode node) {
        try {
            OrgFile file = new OrgFile(node.fileId, resolver);
            return file.filename;
        } catch (OrgFileNotFoundException e) {
            return "";
        }
    }

    public OrgFile getOrgFile(OrgNode node) throws OrgFileNotFoundException {
        return new OrgFile(node.fileId, resolver);
    }

    public void setFilename(OrgNode node, String filename) throws OrgFileNotFoundException {
        OrgFile file = new OrgFile(filename, resolver);
        node.fileId = file.nodeId;
    }

    public boolean isFilenode(OrgNode node) {
        try {
            OrgFile file = new OrgFile(node.fileId, resolver);
            return file.nodeId == node.id;
        } catch (OrgFileNotFoundException e) {
            return false;
        }
    }

    public boolean isNodeEditable(OrgNode node) {
        if (node.id < 0)
            return true;

        if (node.parentId < 0)
            return false;

        try {
            OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
            if (agendaFile != null && agendaFile.nodeId == node.parentId)
                return false;

            if (node.fileId == agendaFile.id
                    && node.name.startsWith(OrgFileParser.BLOCK_SEPARATOR_PREFIX))
                return false;
        } catch (OrgFileNotFoundException e) {
        }

        return true;
    }

    public boolean areChildrenEditable(OrgNode node) {
        if (node.id < 0)
            return false;

        try {
            OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
            if (agendaFile != null && agendaFile.id == node.fileId)
                return false;
        } catch (OrgFileNotFoundException e) {
        }

        return true;
    }

    // =====================================================================
    // Edit generation
    // =====================================================================

    /**
     * Compare old and new node, generate OrgEdit entries for each changed field.
     * Mutates oldNode to reflect the new values as edits are generated.
     */
    public ArrayList<OrgEdit> generateApplyEditNodes(OrgNode oldNode, OrgNode newNode, String olpPath) {
        ArrayList<OrgEdit> edits = new ArrayList<OrgEdit>();

        if (!oldNode.name.equals(newNode.name)) {
            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.HEADING, newNode.name, resolver));
            oldNode.name = newNode.name;
        }
        if (newNode.todo != null && !oldNode.todo.equals(newNode.todo)) {
            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.TODO, newNode.todo, resolver));
            oldNode.todo = newNode.todo;
        }
        if (newNode.priority != null && !oldNode.priority.equals(newNode.priority)) {
            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.PRIORITY, newNode.priority, resolver));
            oldNode.priority = newNode.priority;
        }
        if (newNode.getPayload() != null && !newNode.getPayload().equals(oldNode.getPayload())) {
            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.BODY, newNode.getPayload(), resolver));
            oldNode.setPayload(newNode.getPayload());
        }
        if (oldNode.tags != null && !oldNode.tags.equals(newNode.tags)) {
            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.TAGS, newNode.tags, resolver));
            oldNode.tags = newNode.tags;
        }
        if (newNode.parentId != oldNode.parentId) {
            OrgNode parent = getParentSafe(newNode, olpPath);
            String newId = getNodeId(parent);

            edits.add(new OrgEdit(oldNode, OrgEdit.TYPE.REFILE, newId, resolver));
            oldNode.parentId = newNode.parentId;
            oldNode.fileId = newNode.fileId;
            oldNode.level = parent.level + 1;
        }

        return edits;
    }

    /** Compare old and new node, generate and persist OrgEdit entries. */
    public void generateApplyWriteEdits(OrgNode oldNode, OrgNode newNode, String olpPath) {
        ArrayList<OrgEdit> edits = generateApplyEditNodes(oldNode, newNode, olpPath);
        boolean generateEdits = !getFilename(oldNode).equals(FileUtils.CAPTURE_FILE);

        if (generateEdits)
            for (OrgEdit edit : edits)
                edit.write(resolver);
    }

    /** Create an OrgEdit for adding a new heading under this node's parent. */
    public OrgEdit createParentNewheading(OrgNode node, String olpPath) {
        OrgNode parent = getParentSafe(node, olpPath);
        node.level = parent.level + 1;

        boolean generateEdit = true;
        try {
            OrgFile file = new OrgFile(parent.fileId, resolver);
            generateEdit = file.generateEditsForFile();
        } catch (OrgFileNotFoundException e) {
        }

        if (generateEdit) {
            long tempLevel = node.level;
            node.level = 0;
            OrgEdit edit = new OrgEdit(parent, OrgEdit.TYPE.ADDHEADING, node.toString(), resolver);
            node.level = tempLevel;
            return edit;
        } else
            return new OrgEdit();
    }

    /**
     * Safely get the parent node. Falls back to OLP path resolution,
     * then to the capture file root node.
     */
    public OrgNode getParentSafe(OrgNode node, String olpPath) {
        try {
            return getById(node.parentId);
        } catch (OrgNodeNotFoundException e) {
            try {
                return getOrgNodeFromOlpPath(olpPath);
            } catch (Exception ex) {
                OrgFile captureFile = OrgProviderUtils.getOrCreateCaptureFile(resolver);
                try {
                    return getById(captureFile.nodeId);
                } catch (OrgNodeNotFoundException e2) {
                    return new OrgNode();
                }
            }
        }
    }

    // =====================================================================
    // Archive
    // =====================================================================

    public OrgEdit archiveNode(OrgNode node) {
        OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.ARCHIVE, resolver);
        edit.write(resolver);
        resolver.delete(OrgData.buildIdUri(node.id), null, null);
        return edit;
    }

    public OrgEdit archiveNodeToSibling(OrgNode node) {
        OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.ARCHIVE_SIBLING, resolver);
        edit.write(resolver);

        OrgNode parent;
        try {
            parent = getParent(node.id);
        } catch (OrgNodeNotFoundException e) {
            throw new IllegalArgumentException(
                    "Couldn't archive correctly, didn't find parent of node "
                            + node.name);
        }

        OrgNode archiveNode;
        try {
            archiveNode = getChild(parent.id, OrgNode.ARCHIVE_NODE);
        } catch (OrgNodeNotFoundException e) {
            archiveNode = new OrgNode();
            archiveNode.name = OrgNode.ARCHIVE_NODE;
            archiveNode.parentId = parent.id;
            archiveNode.fileId = parent.fileId;
            write(archiveNode);
        }

        node.parentId = archiveNode.id;
        write(node);

        return edit;
    }

    // =====================================================================
    // Payload modification
    // =====================================================================

    public void addLogbook(OrgNode node, long startTime, long endTime, String elapsedTime) {
        StringBuilder rawPayload = new StringBuilder(node.getPayload());
        rawPayload = OrgNodePayload.addLogbook(rawPayload, startTime, endTime, elapsedTime);
        writePayloadWithEdits(node, rawPayload.toString());
    }

    public void appendFileLink(OrgNode node, String filePath) {
        String link = "[[file:" + filePath + "]]";
        StringBuilder rawPayload = new StringBuilder(node.getPayload());
        rawPayload.append("\n").append(link);
        writePayloadWithEdits(node, rawPayload.toString());
    }

    private void writePayloadWithEdits(OrgNode node, String newPayload) {
        boolean generateEdits = !getFilename(node).equals(FileUtils.CAPTURE_FILE);
        if (generateEdits) {
            OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.BODY, newPayload, resolver);
            edit.write(resolver);
        }
        node.setPayload(newPayload);
        write(node);
    }

    // =====================================================================
    // Serialization
    // =====================================================================

    /** Serialize a node and its entire subtree to org-format text. */
    public StringBuilder nodesToString(long nodeId, long level) {
        StringBuilder result = new StringBuilder();

        try {
            OrgNode node = getById(nodeId);

            if (level != 0)
                result.append(node.toString()).append("\n");

            for (OrgNode child : getChildren(node.id))
                result.append(nodesToString(child.id, level + 1));

        } catch (OrgNodeNotFoundException e) {
        }

        return result;
    }

    // =====================================================================
    // File-level node lookups (migrated from OrgProviderUtils)
    // =====================================================================

    public OrgNode getOrgNodeFromFilename(String filename) throws OrgFileNotFoundException {
        OrgFile file = new OrgFile(filename, resolver);
        try {
            return getById(file.nodeId);
        } catch (OrgNodeNotFoundException e) {
            throw new IllegalStateException("OrgNode for file " + file.name
                    + " should exist");
        }
    }

    public OrgNode getOrgNodeFromFileAlias(String fileAlias) throws OrgNodeNotFoundException {
        Cursor cursor = resolver.query(OrgData.CONTENT_URI,
                OrgData.DEFAULT_COLUMNS,
                OrgData.NAME + "=? AND " + OrgData.PARENT_ID + "=-1",
                new String[]{fileAlias}, null);
        OrgNode node = new OrgNode();
        node.set(cursor);
        return node;
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private ContentValues getContentValues(OrgNode node) {
        ContentValues values = new ContentValues();
        values.put(OrgData.NAME, node.name);
        values.put(OrgData.TODO, node.todo);
        values.put(OrgData.FILE_ID, node.fileId);
        values.put(OrgData.LEVEL, node.level);
        values.put(OrgData.PARENT_ID, node.parentId);
        values.put(OrgData.PAYLOAD, node.getPayload());
        values.put(OrgData.PRIORITY, node.priority);
        values.put(OrgData.TAGS, node.tags);
        values.put(OrgData.TAGS_INHERITED, node.tags_inherited);
        return values;
    }

    private ContentValues getSimpleContentValues(OrgNode node) {
        ContentValues values = new ContentValues();
        values.put(OrgData.NAME, node.name);
        values.put(OrgData.TODO, node.todo);
        values.put(OrgData.PAYLOAD, node.getPayload());
        values.put(OrgData.PRIORITY, node.priority);
        values.put(OrgData.TAGS, node.tags);
        values.put(OrgData.TAGS_INHERITED, node.tags_inherited);
        return values;
    }

    private ArrayList<OrgNode> cursorToNodeList(Cursor cursor) {
        ArrayList<OrgNode> result = new ArrayList<OrgNode>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            try {
                result.add(new OrgNode(cursor));
            } catch (OrgNodeNotFoundException e) {
            }
            cursor.moveToNext();
        }
        return result;
    }

    /** Strip brackets and special chars that break OLP paths. */
    private static String getStrippedNameForOlpPathLink(String name) {
        return name.replaceAll("\\[[^\\]]*\\]", "");
    }
}

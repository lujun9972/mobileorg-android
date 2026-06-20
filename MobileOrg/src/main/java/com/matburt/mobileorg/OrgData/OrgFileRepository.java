package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgContract.Priorities;
import com.matburt.mobileorg.OrgData.OrgContract.Tags;
import com.matburt.mobileorg.OrgData.OrgContract.Todos;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;

/**
 * Repository for file-level and metadata operations.
 * All file CRUD, todo/tag/priority metadata, search, and schedule queries.
 * Created as part of the Repository pattern (see ADR-0001) to replace
 * OrgProviderUtils static methods and OrgFile data-access methods.
 */
public class OrgFileRepository {
    private final ContentResolver resolver;

    public OrgFileRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    // ── File list queries ──

    public HashMap<String, String> getFileChecksums() {
        HashMap<String, String> checksums = new HashMap<String, String>();
        Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
                null, null, null);
        if (cursor == null) return checksums;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            OrgFile orgFile = new OrgFile();
            try {
                orgFile.set(cursor);
                checksums.put(orgFile.filename, orgFile.checksum);
            } catch (OrgFileNotFoundException e) {}
            cursor.moveToNext();
        }
        cursor.close();
        return checksums;
    }

    public ArrayList<String> getFilenames() {
        ArrayList<String> result = new ArrayList<String>();
        Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
                null, null, Files.DEFAULT_SORT);
        if (cursor == null) return result;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            OrgFile orgFile = new OrgFile();
            try {
                orgFile.set(cursor);
                result.add(orgFile.filename);
            } catch (OrgFileNotFoundException e) {}
            cursor.moveToNext();
        }
        cursor.close();
        return result;
    }

    public ArrayList<String> getFileAliases() {
        ArrayList<String> result = new ArrayList<String>();
        Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
                null, null, Files.DEFAULT_SORT);
        if (cursor == null) return result;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            OrgFile orgFile = new OrgFile();
            try {
                orgFile.set(cursor);
                result.add(orgFile.name);
            } catch (OrgFileNotFoundException e) {}
            cursor.moveToNext();
        }
        cursor.close();
        return result;
    }

    // ── File CRUD ──

    public OrgFile getById(long id) throws OrgFileNotFoundException {
        Cursor cursor = resolver.query(Files.buildIdUri(id),
                Files.DEFAULT_COLUMNS, null, null, null);
        if (cursor == null || cursor.getCount() < 1) {
            if (cursor != null) cursor.close();
            throw new OrgFileNotFoundException("File with id \"" + id + "\" not found");
        }
        OrgFile file = new OrgFile();
        file.set(cursor);
        cursor.close();
        return file;
    }

    public OrgFile getByFilename(String filename) throws OrgFileNotFoundException {
        Cursor cursor = resolver.query(Files.CONTENT_URI,
                Files.DEFAULT_COLUMNS, Files.FILENAME + "=?", new String[] {filename}, null);
        if (cursor == null || cursor.getCount() <= 0) {
            if (cursor != null) cursor.close();
            throw new OrgFileNotFoundException("File \"" + filename + "\" not found");
        }
        OrgFile file = new OrgFile();
        file.set(cursor);
        cursor.close();
        return file;
    }

    public boolean doesFileExist(String filename) {
        Cursor cursor = resolver.query(Files.buildFilenameUri(filename),
                Files.DEFAULT_COLUMNS, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count > 0;
    }

    public void write(OrgFile file) {
        if (file.id >= 0 && doesFileExist(file.filename)) {
            updateFile(file);
        } else {
            addFile(file);
        }
    }

    public void addFile(OrgFile file) {
        if (file.includeInOutline) {
            // Create root OrgData node for this file
            ContentValues orgdata = new ContentValues();
            orgdata.put(OrgData.NAME, file.name);
            orgdata.put(OrgData.TODO, "");
            orgdata.put(OrgData.PRIORITY, "");
            orgdata.put(OrgData.LEVEL, 0);
            orgdata.put(OrgData.PARENT_ID, -1);
            Uri orgUri = resolver.insert(OrgData.CONTENT_URI, orgdata);
            long nodeId = Long.parseLong(OrgData.getId(orgUri));
            file.nodeId = nodeId;
        }

        // Insert file record
        ContentValues values = new ContentValues();
        values.put(Files.FILENAME, file.filename);
        values.put(Files.NAME, file.name);
        values.put(Files.CHECKSUM, file.checksum);
        values.put(Files.NODE_ID, file.nodeId);
        Uri uri = resolver.insert(Files.CONTENT_URI, values);
        file.id = Long.parseLong(Files.getId(uri));

        // Link OrgData node back to file
        ContentValues linkValues = new ContentValues();
        linkValues.put(OrgData.FILE_ID, file.id);
        resolver.update(OrgData.buildIdUri(file.nodeId), linkValues, null, null);
    }

    private void updateFile(OrgFile file) {
        // Currently a no-op in original code
    }

    public long removeFile(OrgFile file) {
        // Remove all OrgData nodes belonging to this file
        int total = resolver.delete(OrgData.CONTENT_URI, OrgData.FILE_ID + "=?",
                new String[] { Long.toString(file.id) });
        total += resolver.delete(OrgData.buildIdUri(file.nodeId), null, null);
        // Remove file record
        resolver.delete(Files.buildIdUri(file.id), Files.NAME + "=? AND "
                + Files.FILENAME + "=?", new String[] { file.name, file.filename });
        return total;
    }

    // ── File creation helpers ──

    public OrgFile getOrCreateCaptureFile() {
        return getOrCreateFile(FileUtils.CAPTURE_FILE, FileUtils.CAPTURE_FILE_ALIAS);
    }

    public OrgFile getOrCreateFile(String filename, String fileAlias) {
        OrgFile file = new OrgFile(filename, fileAlias, "");
        if (!doesFileExist(filename)) {
            file.includeInOutline = true;
            write(file);
        } else {
            try {
                file = getByFilename(filename);
            } catch (OrgFileNotFoundException e) {}
        }
        return file;
    }

    public OrgFile getOrCreateFileFromAlias(String fileAlias) {
        Cursor cursor = resolver.query(Files.CONTENT_URI,
                Files.DEFAULT_COLUMNS, Files.NAME + "=?", new String[] {fileAlias}, null);
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) cursor.close();
            if (fileAlias.equals(OrgFile.CAPTURE_FILE_ALIAS))
                return getOrCreateCaptureFile();
            else
                return getOrCreateFile(fileAlias, fileAlias);
        } else {
            OrgFile file = new OrgFile();
            try {
                file.set(cursor);
            } catch (OrgFileNotFoundException e) {}
            cursor.close();
            return file;
        }
    }

    public OrgNode getOrgNode(OrgFile file) {
        try {
            return new OrgNodeRepository(resolver).getById(file.nodeId);
        } catch (Exception e) {
            throw new IllegalStateException("Org node for file " + file.filename
                    + " should exist");
        }
    }

    public String nodesToString(OrgFile file) {
        return new OrgNodeRepository(resolver).nodesToString(file.nodeId, 0).toString();
    }

    // ── File-scoped queries ──

    public Cursor getFileSchedule(String filename, boolean showHabits) throws OrgFileNotFoundException {
        OrgFile file = getByFilename(filename);
        String whereQuery = OrgData.FILE_ID + "=? AND (" + OrgData.PAYLOAD + " LIKE '%<%>%'";
        if (!showHabits)
            whereQuery += " AND NOT " + OrgData.PAYLOAD + " LIKE '%:STYLE: habit%'";
        whereQuery += ")";
        Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, whereQuery,
                new String[] { Long.toString(file.id) }, null);
        cursor.moveToFirst();
        return cursor;
    }

    public Cursor search(String query) {
        return resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
                OrgData.NAME + " LIKE ?", new String[] { query },
                OrgData.DEFAULT_SORT);
    }

    public int getChangesCount() {
        int changes = 0;
        Cursor cursor = resolver.query(Edits.CONTENT_URI,
                Edits.DEFAULT_COLUMNS, null, null, null);
        if (cursor != null) {
            changes += cursor.getCount();
            cursor.close();
        }
        long file_id = -2;
        try {
            file_id = getByFilename(FileUtils.CAPTURE_FILE).nodeId;
        } catch (OrgFileNotFoundException e) {}
        cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
                new String[] { Long.toString(file_id) }, null);
        if (cursor != null) {
            int captures = cursor.getCount();
            if (captures > 0)
                changes += captures;
            cursor.close();
        }
        return changes;
    }

    // ── Todo metadata ──

    public ArrayList<String> getTodos() {
        Cursor cursor = resolver.query(Todos.CONTENT_URI, new String[] { Todos.NAME },
                null, null, Todos.ID);
        ArrayList<String> todos = cursorToArrayList(cursor);
        cursor.close();
        return todos;
    }

    public void setTodos(ArrayList<HashMap<String, Boolean>> todos) {
        resolver.delete(Todos.CONTENT_URI, null, null);
        int grouping = 0;
        for (HashMap<String, Boolean> entry : todos) {
            for (String name : entry.keySet()) {
                ContentValues values = new ContentValues();
                values.put(Todos.NAME, name);
                values.put(Todos.GROUP, grouping);
                if (entry.get(name))
                    values.put(Todos.ISDONE, 1);
                resolver.insert(Todos.CONTENT_URI, values);
            }
            grouping++;
        }
    }

    public ArrayList<String> getActiveTodos() {
        ArrayList<String> result = new ArrayList<String>();
        Cursor cursor = resolver.query(Todos.CONTENT_URI,
                Todos.DEFAULT_COLUMNS, null, null, null);
        if (cursor == null)
            return result;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int isdone = cursor.getInt(cursor.getColumnIndex(Todos.ISDONE));
            if (isdone == 0)
                result.add(cursor.getString(cursor.getColumnIndex(Todos.NAME)));
            cursor.moveToNext();
        }
        cursor.close();
        return result;
    }

    public boolean isTodoActive(String todo) {
        if (TextUtils.isEmpty(todo))
            return true;
        Cursor cursor = resolver.query(Todos.CONTENT_URI, Todos.DEFAULT_COLUMNS,
                Todos.NAME + " = ?", new String[] { todo }, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            int isdone = cursor.getInt(cursor.getColumnIndex(Todos.ISDONE));
            cursor.close();
            return isdone == 0;
        }
        cursor.close();
        return false;
    }

    // ── Tags metadata ──

    public ArrayList<String> getTags() {
        Cursor cursor = resolver.query(Tags.CONTENT_URI, new String[] { Tags.NAME },
                null, null, Tags.ID);
        ArrayList<String> tags = cursorToArrayList(cursor);
        cursor.close();
        return tags;
    }

    public void setTags(ArrayList<String> tags) {
        resolver.delete(Tags.CONTENT_URI, null, null);
        for (String tag : tags) {
            ContentValues values = new ContentValues();
            values.put(Tags.NAME, tag);
            resolver.insert(Tags.CONTENT_URI, values);
        }
    }

    // ── Priorities metadata ──

    public ArrayList<String> getPriorities() {
        Cursor cursor = resolver.query(Priorities.CONTENT_URI,
                new String[] { Priorities.NAME }, null, null, Priorities.ID);
        ArrayList<String> priorities = cursorToArrayList(cursor);
        cursor.close();
        return priorities;
    }

    public void setPriorities(ArrayList<String> priorities) {
        resolver.delete(Priorities.CONTENT_URI, null, null);
        for (String priority : priorities) {
            ContentValues values = new ContentValues();
            values.put(Priorities.NAME, priority);
            resolver.insert(Priorities.CONTENT_URI, values);
        }
    }

    // ── Utility ──

    public ArrayList<OrgNode> orgDataCursorToArrayList(Cursor cursor) {
        ArrayList<OrgNode> result = new ArrayList<OrgNode>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            try {
                result.add(new OrgNode(cursor));
            } catch (Exception e) {}
            cursor.moveToNext();
        }
        return result;
    }

    private ArrayList<String> cursorToArrayList(Cursor cursor) {
        ArrayList<String> list = new ArrayList<String>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            list.add(cursor.getString(cursor.getColumnIndex("name")));
            cursor.moveToNext();
        }
        return list;
    }
}

package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgContract.Priorities;
import com.matburt.mobileorg.OrgData.OrgContract.Tags;
import com.matburt.mobileorg.OrgData.OrgContract.Todos;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.util.OrgFileNotFoundException;

public class OrgProviderUtils {
	
	public static HashMap<String, String> getFileChecksums(ContentResolver resolver) {
		HashMap<String, String> checksums = new HashMap<String, String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, null);
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
	
	public static ArrayList<String> getFilenames(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, Files.DEFAULT_SORT);
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
	
	public static ArrayList<String> getFileAliases(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, Files.DEFAULT_SORT);
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
	
	public static void setTodos(ArrayList<HashMap<String, Boolean>> todos,
			ContentResolver resolver) {
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
	public static ArrayList<String> getTodos(ContentResolver resolver) {
		Cursor cursor = resolver.query(Todos.CONTENT_URI, new String[] { Todos.NAME }, null, null, Todos.ID);
		ArrayList<String> todos = cursorToArrayList(cursor);

		cursor.close();
		return todos;
	}
	
	public static void setPriorities(ArrayList<String> priorities, ContentResolver resolver) {
		resolver.delete(Priorities.CONTENT_URI, null, null);

		for (String priority : priorities) {
			ContentValues values = new ContentValues();
			values.put(Priorities.NAME, priority);
			resolver.insert(Priorities.CONTENT_URI, values);
		}
	}

	public static ArrayList<String> getPriorities(ContentResolver resolver) {
		Cursor cursor = resolver.query(Priorities.CONTENT_URI, new String[] { Priorities.NAME },
				null, null, Priorities.ID);
		ArrayList<String> priorities = cursorToArrayList(cursor);

		cursor.close();
		return priorities;
	}
	

	public static void setTags(ArrayList<String> priorities, ContentResolver resolver) {
		resolver.delete(Tags.CONTENT_URI, null, null);

		for (String priority : priorities) {
			ContentValues values = new ContentValues();
			values.put(Tags.NAME, priority);
			resolver.insert(Tags.CONTENT_URI, values);
		}
	}
	public static ArrayList<String> getTags(ContentResolver resolver) {
		Cursor cursor = resolver.query(Tags.CONTENT_URI, new String[] { Tags.NAME },
				null, null, Tags.ID);
		ArrayList<String> tags = cursorToArrayList(cursor);

		cursor.close();
		return tags;
	}
	
	public static ArrayList<String> cursorToArrayList(Cursor cursor) {
		ArrayList<String> list = new ArrayList<String>();
		cursor.moveToFirst();

		while (!cursor.isAfterLast()) {
			list.add(cursor.getString(cursor.getColumnIndex("name")));
			cursor.moveToNext();
		}
		return list;
	}
	
	public static ArrayList<OrgNode> getOrgNodePathFromTopLevel(long node_id, ContentResolver resolver) {
		return new OrgNodeRepository(resolver).getNodePathFromTopLevel(node_id);
	}
	
	public static OrgNode getOrgNodeFromOlpPath(String olpPath, ContentResolver resolver) throws OrgNodeNotFoundException, OrgFileNotFoundException {
		return new OrgNodeRepository(resolver).getOrgNodeFromOlpPath(olpPath);
	}
	
	public static StringBuilder nodesToString(long node_id, long level, ContentResolver resolver) {
		return new OrgNodeRepository(resolver).nodesToString(node_id, level);
	}
	
	public static void clearDB(ContentResolver resolver) {
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
		resolver.delete(Edits.CONTENT_URI, null, null);
	}
	
	
	public static OrgNode getOrgNodeFromFilename(String filename, ContentResolver resolver) throws OrgFileNotFoundException {
		return new OrgNodeRepository(resolver).getOrgNodeFromFilename(filename);
	}
	
	public static OrgFile getOrCreateCaptureFile (ContentResolver resolver) {
		return getOrCreateFile(FileUtils.CAPTURE_FILE, FileUtils.CAPTURE_FILE_ALIAS, resolver);
	}
	
	public static OrgFile getOrCreateFile(String filename, String fileAlias, ContentResolver resolver) {
		OrgFile file = new OrgFile(filename, fileAlias, "");
		if(!file.doesFileExist(resolver)) {
			file.includeInOutline = true;
			file.write(resolver);
		} else {
			try {
			file = new OrgFile(filename, resolver);
			} catch (OrgFileNotFoundException e) {}
		}
		return file;
	}
	
	public static OrgNode getOrgNodeFromFileAlias(String fileAlias, ContentResolver resolver) throws OrgNodeNotFoundException {
		return new OrgNodeRepository(resolver).getOrgNodeFromFileAlias(fileAlias);
	}
	
	public static OrgFile getOrCreateFileFromAlias(String fileAlias, ContentResolver resolver) {
		Cursor cursor = resolver.query(Files.CONTENT_URI,
				Files.DEFAULT_COLUMNS, Files.NAME + "=?", new String[] {fileAlias}, null);
		if(cursor == null || cursor.getCount() == 0) {
			if(fileAlias.equals(OrgFile.CAPTURE_FILE_ALIAS))
				return getOrCreateCaptureFile(resolver);
			else
				return getOrCreateFile(fileAlias, fileAlias, resolver);
		} else {
			OrgFile file = new OrgFile();
			try {
				file.set(cursor);
			} catch (OrgFileNotFoundException e) {}
			cursor.close();
			return file;
		}
	}
	
	public static ArrayList<String> getActiveTodos(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Todos.CONTENT_URI,
				Todos.DEFAULT_COLUMNS, null, null, null);
		if(cursor == null)
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
	
	public static boolean isTodoActive(String todo, ContentResolver resolver) {
		if(TextUtils.isEmpty(todo))
			return true;

		Cursor cursor = resolver.query(Todos.CONTENT_URI, Todos.DEFAULT_COLUMNS, Todos.NAME + " = ?",
				new String[] { todo }, null);		
		
		if(cursor.getCount() > 0) {
			cursor.moveToFirst();
			int isdone = cursor.getInt(cursor.getColumnIndex(Todos.ISDONE));
			cursor.close();
			
			return isdone == 0;
		}
		
		return false;
	}
	
	public static Cursor getFileSchedule(String filename, boolean showHabits, ContentResolver resolver) throws OrgFileNotFoundException {
		OrgFile file = new OrgFile(filename, resolver);
		
		String whereQuery = OrgData.FILE_ID + "=? AND (" + OrgData.PAYLOAD + " LIKE '%<%>%'";

		if(!showHabits)
			whereQuery += " AND NOT " + OrgData.PAYLOAD + " LIKE '%:STYLE: habit%'";
		
		whereQuery += ")";
			
		Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, whereQuery,
				new String[] { Long.toString(file.id) }, null);
		cursor.moveToFirst();
		return cursor;
	}
	
	public static Cursor search(String query, ContentResolver resolver) {
		Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
				OrgData.NAME + " LIKE ?", new String[] { query },
				OrgData.DEFAULT_SORT);
		
		return cursor;
	}
	
	public static int getChangesCount(ContentResolver resolver) {
		int changes = 0;
		Cursor cursor = resolver.query(Edits.CONTENT_URI,
				Edits.DEFAULT_COLUMNS, null, null, null);
		if(cursor != null) {
			changes += cursor.getCount();
			cursor.close();
		}
		
		long file_id = -2;
		try {
			file_id = new OrgFile(FileUtils.CAPTURE_FILE, resolver).nodeId;
		} catch (OrgFileNotFoundException e) {}
		cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
				new String[] { Long.toString(file_id) }, null);
		if(cursor != null) {
			int captures = cursor.getCount();
			if(captures > 0)
				changes += captures;
			cursor.close();
		}
		
		return changes;
	}
	
	public static ArrayList<OrgNode> getOrgNodeChildren(long nodeId, ContentResolver resolver) {
		return new OrgNodeRepository(resolver).getChildren(nodeId);
	}
	
	public static ArrayList<OrgNode> orgDataCursorToArrayList(Cursor cursor) {
		ArrayList<OrgNode> result = new ArrayList<OrgNode>();
		
		cursor.moveToFirst();
		
		while(!cursor.isAfterLast()) {
			try {
				result.add(new OrgNode(cursor));
			} catch (OrgNodeNotFoundException e) {}
			cursor.moveToNext();
		}
		
		return result;
	}
}

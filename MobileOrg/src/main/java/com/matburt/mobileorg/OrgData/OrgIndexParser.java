package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.TextUtils;

/**
 * Pure parsing utilities for index.org content. No database dependency.
 * Extracts checksums, file lists, TODOs, priorities, and tags from
 * the index file that Synchronizer downloads before syncing org files.
 */
public class OrgIndexParser {

	/**
	 * Parses the checksum file.
	 * @return HashMap with Filename-&gt;checksum
	 */
	public static HashMap<String, String> getChecksums(String filecontents) {
		HashMap<String, String> checksums = new HashMap<String, String>();
		for (String line : filecontents.split("[\\n\\r]+")) {
			if (TextUtils.isEmpty(line))
				continue;
			String[] chksTuple = line.split("  ", 2);
			if(chksTuple.length == 2)
				checksums.put(chksTuple[1], chksTuple[0]);
		}
		return checksums;
	}

	private static final String fileMatchPattern = "\\[file:(.*?)\\]\\[(.*?)\\]\\]";
	/**
	 * Parses the file list from index file.
	 * @return HashMap with Filename-&gt;Filename Alias
	 */
	public static HashMap<String, String> getFilesFromIndex(String filecontents) {
		Pattern indexOrgFilePattern = Pattern.compile(fileMatchPattern);
		Matcher indexOrgFileMatcher = indexOrgFilePattern.matcher(filecontents);
		HashMap<String, String> allOrgFiles = new HashMap<String, String>();

		while (indexOrgFileMatcher.find()) {
			allOrgFiles.put(indexOrgFileMatcher.group(1), indexOrgFileMatcher.group(2));
		}

		return allOrgFiles;
	}


	private static final Pattern getTodos = Pattern
			.compile("#\\+TODO:([^\\|]+)(\\| ([^\\n]*))*");
	public static ArrayList<HashMap<String, Boolean>> getTodosFromIndex(String filecontents) {
		Matcher m = getTodos.matcher(filecontents);
		ArrayList<HashMap<String, Boolean>> todoList = new ArrayList<HashMap<String, Boolean>>();
		while (m.find()) {
			String lastTodo = "";
			HashMap<String, Boolean> holding = new HashMap<String, Boolean>();
			Boolean isDone = false;
			for (int idx = 1; idx <= m.groupCount(); idx++) {
				if (m.group(idx) != null && m.group(idx).trim().length() > 0) {
					if (m.group(idx).indexOf("|") != -1) {
						isDone = true;
						continue;
					}
					String[] grouping = m.group(idx).trim().split("\\s+");
					for (String group : grouping) {
						lastTodo = group.trim();
						holding.put(group.trim(), isDone);
					}
				}
			}
			if (!isDone) {
				holding.put(lastTodo, true);
			}
			todoList.add(holding);
		}
		return todoList;
	}

	private static final Pattern getPriorities = Pattern
			.compile("#\\+ALLPRIORITIES:([^\\n]+)");
	public static ArrayList<String> getPrioritiesFromIndex(String filecontents) {
		Matcher t = getPriorities.matcher(filecontents);

		ArrayList<String> priorities = new ArrayList<String>();

		if (t.find()) {
			if (t.group(1) != null && t.group(1).trim().length() > 0) {
				String[] grouping = t.group(1).trim().split("\\s+");
				for (String group : grouping)
					priorities.add(group.trim());
			}
		}
		return priorities;
	}


	private static final Pattern getTags = Pattern.compile("#\\+TAGS:([^\\n]+)");
	public static ArrayList<String> getTagsFromIndex(String filecontents) {
		Matcher matcher = getTags.matcher(filecontents);
		ArrayList<String> tagList = new ArrayList<String>();

		if(matcher.find()) {
			String tags = matcher.group(1).trim().replaceAll("[\\{\\}]", "");
			String[] split = tags.split("\\s+");

			if(split.length == 1 && split[0].equals(""))
				return tagList;

			for(String tag: split)
				tagList.add(tag);
		}

		return tagList;
	}
}

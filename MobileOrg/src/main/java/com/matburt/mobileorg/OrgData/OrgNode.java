package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;

import android.content.Context;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.matburt.mobileorg.Gui.Outline.OutlineItem;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.util.OrgUtils;

public class OrgNode {
	public static final String ARCHIVE_NODE = "Archive";

	public long id = -1;
	public long parentId = -1;
	public long fileId = -1;

	public long level = 0;
	public String priority = "";
	public String todo = "";
	public String tags = "";
	public String tags_inherited = "";
	public String name = "";
	private String payload = "";

	private OrgNodePayload orgNodePayload = null;

	public OrgNode() {
	}

	public OrgNode(OrgNode node) {
		this.id = node.id;
		this.parentId = node.parentId;
		this.fileId = node.fileId;
		this.level = node.level;
		this.priority = node.priority;
		this.todo = node.todo;
		this.tags = node.tags;
		this.tags_inherited = node.tags_inherited;
		this.name = node.name;
		setPayload(node.getPayload());
	}

	public OrgNode(Cursor cursor) throws OrgNodeNotFoundException {
		set(cursor);
	}

	public void set(Cursor cursor) throws OrgNodeNotFoundException {
		if (cursor != null && cursor.getCount() > 0) {
			if(cursor.isBeforeFirst() || cursor.isAfterLast())
				cursor.moveToFirst();
			id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
			parentId = cursor.getLong(cursor
					.getColumnIndexOrThrow(OrgData.PARENT_ID));
			fileId = cursor.getLong(cursor
					.getColumnIndexOrThrow(OrgData.FILE_ID));
			level = cursor.getLong(cursor.getColumnIndexOrThrow(OrgData.LEVEL));
			priority = cursor.getString(cursor
					.getColumnIndexOrThrow(OrgData.PRIORITY));
			todo = cursor.getString(cursor.getColumnIndexOrThrow(OrgData.TODO));
			tags = cursor.getString(cursor.getColumnIndexOrThrow(OrgData.TAGS));
			tags_inherited = cursor.getString(cursor
					.getColumnIndexOrThrow(OrgData.TAGS_INHERITED));
			name = cursor.getString(cursor.getColumnIndexOrThrow(OrgData.NAME));
			payload = cursor.getString(cursor
					.getColumnIndexOrThrow(OrgData.PAYLOAD));
		} else {
			throw new OrgNodeNotFoundException(
					"Failed to create OrgNode from cursor");
		}
	}

	void preparePayload() {
		if(this.orgNodePayload == null)
			this.orgNodePayload = new OrgNodePayload(this.payload);
	}

	public boolean isHabit() {
		preparePayload();
		return orgNodePayload.getProperty("STYLE").equals("habit");
	}

	/**
	 * This will split up the tag string that it got from the tag entry in the
	 * database. The leading and trailing : are stripped out from the tags by
	 * the parser. A double colon (::) means that the tags before it are
	 * inherited.
	 */
	public ArrayList<String> getTags() {
		ArrayList<String> result = new ArrayList<String>();

		if(tags == null)
			return result;

		String[] split = tags.split("\\:");

		for (String tag : split)
			result.add(tag);

		if (tags.endsWith(":"))
			result.add("");

		return result;
	}

	public String getCleanedName() {
		StringBuilder nameBuilder = new StringBuilder(this.name);

		Matcher matcher = OutlineItem.urlPattern.matcher(nameBuilder);
		while(matcher.find()) {
			nameBuilder.delete(matcher.start(), matcher.end());
			nameBuilder.insert(matcher.start(), matcher.group(1));
			matcher = OutlineItem.urlPattern.matcher(nameBuilder);
		}

		return nameBuilder.toString();
	}

	public String getCleanedPayload() {
		preparePayload();
		return this.orgNodePayload.getCleanedPayload();
	}

	public String getPayload() {
		preparePayload();
		return this.orgNodePayload.get();
	}

    public HashMap getPropertiesPayload() {
        preparePayload();
        return this.orgNodePayload.getPropertiesPayload();
    }

	public void setPayload(String payload) {
		this.orgNodePayload = null;
		this.payload = payload;
	}

	public OrgNodePayload getOrgNodePayload() {
		preparePayload();
		return this.orgNodePayload;
	}

	public String toString() {
		return toString(this.level);
	}

	/**
	 * Serialize with an explicit star count. Used to normalize subtree levels
	 * when sharing: the subtree root becomes level 1 regardless of its depth
	 * in the source file.
	 */
	public String toString(long level) {
		StringBuilder result = new StringBuilder();

		for(long i = 0; i < level; i++)
			result.append("*");
		result.append(" ");

		if (!TextUtils.isEmpty(todo))
			result.append(todo + " ");

		if (!TextUtils.isEmpty(priority))
			result.append("[#" + priority + "] ");

		result.append(name);

		if(tags != null && !TextUtils.isEmpty(tags))
			result.append(" ").append(":" + tags + ":");


		if (payload != null && !TextUtils.isEmpty(payload))
			result.append("\n").append(payload);

		return result.toString();
	}

	public boolean equals(OrgNode node) {
		return name.equals(node.name) && tags.equals(node.tags)
				&& priority.equals(node.priority) && todo.equals(node.todo)
				&& payload.equals(node.payload);
	}

	public void addAutomaticTimestamp() {
		Context context = MobileOrgApplication.getContext();
		boolean addTimestamp = PreferenceManager.getDefaultSharedPreferences(
				context).getBoolean("captureWithTimestamp", false);
		if(addTimestamp)
			setPayload(getPayload() + OrgUtils.getTimestamp());
	}
}

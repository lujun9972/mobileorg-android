package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Matcher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.matburt.mobileorg.Gui.Outline.OutlineItem;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
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
		this.level = node.level;
		this.priority = node.priority;
		this.todo = node.todo;
		this.tags = node.tags;
		this.tags_inherited = node.tags_inherited;
		this.name = node.name;
		setPayload(node.getPayload());
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getById(long)} */
	@Deprecated
	public OrgNode(long id, ContentResolver resolver) throws OrgNodeNotFoundException {
		Cursor cursor = resolver.query(OrgData.buildIdUri(id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		if(cursor == null || !cursor.moveToFirst())
			throw new OrgNodeNotFoundException("Node with id \"" + id + "\" not found");
		set(cursor);
		cursor.close();
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
	
	/** @deprecated Use {@link OrgNodeRepository#getFilename(OrgNode)} */
	@Deprecated
	public String getFilename(ContentResolver resolver) {
		try {
			OrgFile file = new OrgFile(fileId, resolver);
			return file.filename;
		} catch (OrgFileNotFoundException e) {
			return "";
		}
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getOrgFile(OrgNode)} */
	@Deprecated
	public OrgFile getOrgFile(ContentResolver resolver) throws OrgFileNotFoundException {
		return new OrgFile(fileId, resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#setFilename(OrgNode, String)} */
	@Deprecated
	public void setFilename(String filename, ContentResolver resolver) throws OrgFileNotFoundException {
		OrgFile file = new OrgFile(filename, resolver);
		this.fileId = file.nodeId;
	}
	
	void preparePayload() {
		if(this.orgNodePayload == null)
			this.orgNodePayload = new OrgNodePayload(this.payload);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#write(OrgNode)} */
	@Deprecated
	public void write(ContentResolver resolver) {
		if(id < 0)
			addNode(resolver);
		else
			updateNode(resolver);
	}
	
	private long addNode(ContentResolver resolver) {
		Uri uri = resolver.insert(OrgData.CONTENT_URI, getContentValues());
		this.id = Long.parseLong(OrgData.getId(uri));
		return id;
	}
	
	private int updateNode(ContentResolver resolver) {
		return resolver.update(OrgData.buildIdUri(id), getContentValues(), null, null);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#updateAllNodes(OrgNode)} */
	@Deprecated
	public void updateAllNodes(ContentResolver resolver) {
		updateNode(resolver);
		
		String nodeId = getNodeId(resolver);
		if (!nodeId.startsWith("olp:")) { // Update all nodes that have this :ID:
			String nodeIdQuery = "%" + nodeId + "%";
			resolver.update(OrgData.CONTENT_URI, getSimpleContentValues(),
					OrgData.PAYLOAD + " LIKE ?", new String[] { nodeIdQuery });
		}
	}
	
	/** @deprecated Use {@link OrgNodeRepository#findOriginalNode(OrgNode)} */
	@Deprecated
	public OrgNode findOriginalNode(ContentResolver resolver) {
		if(parentId == -1)
			return this;
		
		if (!getFilename(resolver).equals(OrgFile.AGENDA_FILE))
			return this;
		
		String nodeId = getNodeId(resolver);
		if (!nodeId.startsWith("olp:")) { // Update all nodes that have this :ID:
			String nodeIdQuery = OrgData.PAYLOAD + " LIKE '%" + nodeId + "%'";
			try {
				OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
				if(agendaFile != null)
					nodeIdQuery += " AND NOT " + OrgData.FILE_ID + "=" + agendaFile.nodeId;
			} catch (OrgFileNotFoundException e) {}
			
			Cursor query = resolver.query(OrgData.CONTENT_URI,
					OrgData.DEFAULT_COLUMNS, nodeIdQuery, null,
					null);
			try {
				OrgNode node = new OrgNode(query);
				query.close();
				return node;
			} catch (OrgNodeNotFoundException e) {
				if (query != null)
					query.close();
			}
		}
		
		return this;
	}

	public boolean isHabit() {
		preparePayload();
		return orgNodePayload.getProperty("STYLE").equals("habit");
	}
	
	private ContentValues getSimpleContentValues() {
		ContentValues values = new ContentValues();
		values.put(OrgData.NAME, name);
		values.put(OrgData.TODO, todo);
		values.put(OrgData.PAYLOAD, payload);
		values.put(OrgData.PRIORITY, priority);
		values.put(OrgData.TAGS, tags);
		values.put(OrgData.TAGS_INHERITED, tags_inherited);
		return values;
	}
	
	private ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(OrgData.NAME, name);
		values.put(OrgData.TODO, todo);
		values.put(OrgData.FILE_ID, fileId);
		values.put(OrgData.LEVEL, level);
		values.put(OrgData.PARENT_ID, parentId);
		values.put(OrgData.PAYLOAD, payload);
		values.put(OrgData.PRIORITY, priority);
		values.put(OrgData.TAGS, tags);
		values.put(OrgData.TAGS_INHERITED, tags_inherited);
		return values;
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
	
	/** @deprecated Use {@link OrgNodeRepository#getChildren(long)} */
	@Deprecated
	public ArrayList<OrgNode> getChildren(ContentResolver resolver) {
		return OrgProviderUtils.getOrgNodeChildren(id, resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getChildrenStringArray(long)} */
	@Deprecated
	public ArrayList<String> getChildrenStringArray(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();
		ArrayList<OrgNode> children = getChildren(resolver);

		for (OrgNode node : children)
			result.add(node.name);

		return result;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getChild(long, String)} */
	@Deprecated
	public OrgNode getChild(String name, ContentResolver resolver) throws OrgNodeNotFoundException {
		ArrayList<OrgNode> children = getChildren(resolver);
		
		for(OrgNode child: children) {
			if(child.name.equals(name))
				return child;
		}
		throw new OrgNodeNotFoundException("Couln't find child of node "
				+ this.name + " with name " + name);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#hasChildren(long)} */
	@Deprecated
	public boolean hasChildren(ContentResolver resolver) {
		Cursor childCursor = resolver.query(OrgData.buildChildrenUri(id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		
		int childCount = childCursor.getCount();
		childCursor.close();
		
		return childCount > 0;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#hasChildren(long)} */
	@Deprecated
	public static boolean hasChildren (long node_id, ContentResolver resolver) {
		try {
			OrgNode node = new OrgNode(node_id, resolver);
			return node.hasChildren(resolver);
		} catch (OrgNodeNotFoundException e) {
		}
		
		return false;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getParent(long)} */
	@Deprecated
	public OrgNode getParent(ContentResolver resolver) throws OrgNodeNotFoundException {
		Cursor cursor = resolver.query(OrgData.buildIdUri(this.parentId),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		return new OrgNode(cursor);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getSiblingsStringArray(long)} */
	@Deprecated
	public ArrayList<String> getSiblingsStringArray(ContentResolver resolver) {
		try {
			OrgNode parent = getParent(resolver);
			return parent.getChildrenStringArray(resolver);
		}
		catch (OrgNodeNotFoundException e) {
			throw new IllegalArgumentException("Couldn't get parent for node " + name);
		}
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getSibling(long, String)} */
	@Deprecated
	public OrgNode getSibling(String name, ContentResolver resolver) throws OrgNodeNotFoundException {
		OrgNode parent = getParent(resolver);
		return parent.getChild(name, resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#isFilenode(OrgNode)} */
	@Deprecated
	public boolean isFilenode(ContentResolver resolver) {
		try {
			OrgFile file = new OrgFile(fileId, resolver);
			if(file.nodeId == this.id)
				return true;
		} catch (OrgFileNotFoundException e) {}
		
		return false;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#isNodeEditable(OrgNode)} */
	@Deprecated
	public boolean isNodeEditable(ContentResolver resolver) {
		if(id < 0) // Node is not in database
			return true;
		
		if(id >= 0 && parentId < 0) // Top-level node
			return false;
		
		try {
			OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
			if (agendaFile != null && agendaFile.nodeId == parentId) // Second level in agendas file
				return false;
			
			if(fileId == agendaFile.id && name.startsWith(OrgFileParser.BLOCK_SEPARATOR_PREFIX))
				return false;
		} catch (OrgFileNotFoundException e) {}

		return true;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#areChildrenEditable(OrgNode)} */
	@Deprecated
	public boolean areChildrenEditable(ContentResolver resolver) {
		if(id < 0) // Node is not in database
			return false;
		
		try {
			OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, resolver);
			if (agendaFile != null && agendaFile.id == fileId) // In agenda file
				return false;
		} catch (OrgFileNotFoundException e) {}

		return true;
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

	
	/**
	 * @return The :ID:, :ORIGINAL_ID: or olp link of node.
	 */
	/** @deprecated Use {@link OrgNodeRepository#getNodeId(OrgNode)} */
	@Deprecated
	public String getNodeId(ContentResolver resolver) {
		preparePayload();

		String id = orgNodePayload.getId();				
		if(id != null && !id.equals(""))
			return id;
		else
			return getOlpId(resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getOlpId(OrgNode)} */
	@Deprecated
	public String getOlpId(ContentResolver resolver) {
		StringBuilder result = new StringBuilder();
		
		ArrayList<OrgNode> nodesFromRoot;
		try {
			nodesFromRoot = OrgProviderUtils.getOrgNodePathFromTopLevel(
					parentId, resolver);
		} catch (IllegalStateException e) {
			return "";
		}
		
		if (nodesFromRoot.size() == 0) {
			try {
				return "olp:" + getOrgFile(resolver).name;
			} catch (OrgFileNotFoundException e) {
				return "";
			}
		}
			
		OrgNode topNode = nodesFromRoot.get(0);
		nodesFromRoot.remove(0);
		result.append("olp:" + topNode.getFilename(resolver) + ":");
		
		for(OrgNode node: nodesFromRoot)
			result.append(node.getStrippedNameForOlpPathLink() + "/");
		
		result.append(getStrippedNameForOlpPathLink());
		return result.toString();
	}
	
	/**
	 * Olp paths containing certain symbols can't be applied by org-mode. To
	 * prevent node names from injecting bad symbols, we strip them out here.
	 */
	private String getStrippedNameForOlpPathLink() {
		String result = this.name;
		result = result.replaceAll("\\[[^\\]]*\\]", ""); // Strip out "[*]"
		return result;
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
	
	/** @deprecated Use {@link OrgNodeRepository#createParentNewheading(OrgNode, String)} */
	@Deprecated
	public OrgEdit createParentNewheading(ContentResolver resolver) {
		return createParentNewheading(resolver, "");
	}
	
	/** @deprecated Use {@link OrgNodeRepository#createParentNewheading(OrgNode, String)} */
	@Deprecated
	public OrgEdit createParentNewheading(ContentResolver resolver, String olpPath) {
		OrgNode parent = getParentSafe(olpPath, resolver);
		this.level = parent.level + 1;

		boolean generateEdit = true;
		try {
			OrgFile file = new OrgFile(parent.fileId, resolver);
			generateEdit = file.generateEditsForFile();
		} catch (OrgFileNotFoundException e) {}
		
		if (generateEdit) {
			// Add new heading nodes; need the entire content of node without
			// star headings
			long tempLevel = level;
			level = 0;
			OrgEdit edit = new OrgEdit(parent, OrgEdit.TYPE.ADDHEADING, this.toString(), resolver);
			level = tempLevel;
			return edit;
		} else
			return new OrgEdit();
	}
	
	/** @deprecated Use {@link OrgNodeRepository#getParentSafe(OrgNode, String)} */
	@Deprecated
	public OrgNode getParentSafe(String olpPath, ContentResolver resolver) {
		OrgNode parent;
		try {
			parent = new OrgNode(this.parentId, resolver);
		} catch (OrgNodeNotFoundException e) {
			try {
				parent = OrgProviderUtils.getOrgNodeFromOlpPath(olpPath,
						resolver);
			} catch (Exception ex) {
				parent = OrgProviderUtils.getOrCreateCaptureFile(resolver)
						.getOrgNode(resolver);
			}
		}
		return parent;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#generateApplyWriteEdits(OrgNode, OrgNode, String)} */
	@Deprecated
	public void generateApplyWriteEdits(OrgNode newNode, String olpPath,
			ContentResolver resolver) {
		ArrayList<OrgEdit> generateEditNodes = generateApplyEditNodes(newNode, olpPath, resolver);
		boolean generateEdits = !getFilename(resolver).equals(FileUtils.CAPTURE_FILE);
				
		if(generateEdits)
			for(OrgEdit edit: generateEditNodes)
				edit.write(resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#generateApplyEditNodes(OrgNode, OrgNode, String)} */
	@Deprecated
	public ArrayList<OrgEdit> generateApplyEditNodes(OrgNode newNode, ContentResolver resolver) {
		return generateApplyEditNodes(newNode, "", resolver);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#generateApplyEditNodes(OrgNode, OrgNode, String)} */
	@Deprecated
	public ArrayList<OrgEdit> generateApplyEditNodes(OrgNode newNode,
			String olpPath, ContentResolver resolver) {
		ArrayList<OrgEdit> edits = new ArrayList<OrgEdit>();

		if (!name.equals(newNode.name)) {
			edits.add(new OrgEdit(this, OrgEdit.TYPE.HEADING, newNode.name, resolver));
			this.name = newNode.name;			
		}
		if (newNode.todo != null && !todo.equals(newNode.todo)) {
			edits.add(new OrgEdit(this, OrgEdit.TYPE.TODO, newNode.todo, resolver));
			this.todo = newNode.todo;
		}
		if (newNode.priority != null && !priority.equals(newNode.priority)) {
			edits.add(new OrgEdit(this, OrgEdit.TYPE.PRIORITY, newNode.priority, resolver));
			this.priority = newNode.priority;
		}
		if (newNode.getPayload() != null && !newNode.getPayload().equals(getPayload())) {
			edits.add(new OrgEdit(this, OrgEdit.TYPE.BODY, newNode.getPayload(), resolver));
			setPayload(newNode.getPayload());
		}
		if (tags != null && !tags.equals(newNode.tags)) {
			edits.add(new OrgEdit(this, OrgEdit.TYPE.TAGS, newNode.tags, resolver));
			this.tags = newNode.tags;
		}
		if (newNode.parentId != parentId) {
			OrgNode parent = newNode.getParentSafe(olpPath, resolver);
			String newId = parent.getNodeId(resolver);

			edits.add(new OrgEdit(this, OrgEdit.TYPE.REFILE, newId, resolver));
			this.parentId = newNode.parentId;
			this.fileId = newNode.fileId;
			this.level = parent.level + 1;
		}
		
		return edits;
	}
	
	public String toString() {
		StringBuilder result = new StringBuilder();
		
		for(int i = 0; i < level; i++)
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

	
	/** @deprecated Use {@link OrgNodeRepository#deleteNode(OrgNode)} */
	@Deprecated
	public void deleteNode(ContentResolver resolver) {
		OrgEdit edit = new OrgEdit(this, OrgEdit.TYPE.DELETE, resolver);
		edit.write(resolver);
		resolver.delete(OrgData.buildIdUri(id), null, null);
	}
	
	/** @deprecated Use {@link OrgNodeRepository#archiveNode(OrgNode)} */
	@Deprecated
	public OrgEdit archiveNode(ContentResolver resolver) {
		OrgEdit edit = new OrgEdit(this, OrgEdit.TYPE.ARCHIVE, resolver);
		edit.write(resolver);
		resolver.delete(OrgData.buildIdUri(id), null, null);
		return edit;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#archiveNodeToSibling(OrgNode)} */
	@Deprecated
	public OrgEdit archiveNodeToSibling(ContentResolver resolver) {
		OrgEdit edit = new OrgEdit(this, OrgEdit.TYPE.ARCHIVE_SIBLING, resolver);
		edit.write(resolver);

		OrgNode parent;
		try {
			parent = getParent(resolver);
		} catch (OrgNodeNotFoundException e) {
			throw new IllegalArgumentException(
					"Could't archive correctly, didn't find parent of node "
							+ this.name);
		}
		
		OrgNode archiveNode;
		try {
			archiveNode = parent.getChild(ARCHIVE_NODE, resolver);
		} catch (OrgNodeNotFoundException e) {
			archiveNode = new OrgNode();
			archiveNode.name = ARCHIVE_NODE;
			archiveNode.parentId = parent.id;
			archiveNode.fileId = parent.fileId;
			archiveNode.write(resolver);
		}

		this.parentId = archiveNode.id;
		this.write(resolver);

		return edit;
	}
	
	/** @deprecated Use {@link OrgNodeRepository#addLogbook(OrgNode, long, long, String)} */
	@Deprecated
	public void addLogbook(long startTime, long endTime, String elapsedTime, ContentResolver resolver) {
		Log.d("MobileOrg", "[ClockIn] OrgNode.addLogbook: node.id=" + this.id
				+ ", startTime=" + startTime + ", endTime=" + endTime
				+ ", elapsedTime=" + elapsedTime);
		StringBuilder rawPayload = new StringBuilder(getPayload());
		Log.d("MobileOrg", "[ClockIn] OrgNode.addLogbook: rawPayload before = [" + rawPayload + "]");
		rawPayload = OrgNodePayload.addLogbook(rawPayload, startTime, endTime, elapsedTime);
		Log.d("MobileOrg", "[ClockIn] OrgNode.addLogbook: rawPayload after  = [" + rawPayload + "]");

			writePayloadWithEdits(rawPayload.toString(), resolver);
			Log.d("MobileOrg", "[ClockIn] OrgNode.addLogbook: writePayloadWithEdits done");
	}

	/**
	 * Append a file link (e.g. [[file:xxx.aac]]) to the node body.
	 */
	/** @deprecated Use {@link OrgNodeRepository#appendFileLink(OrgNode, String)} */
	@Deprecated
	public void appendFileLink(String filePath, ContentResolver resolver) {
		String link = "[[file:" + filePath + "]]";
		StringBuilder rawPayload = new StringBuilder(getPayload());
		rawPayload.append("\n").append(link);

			writePayloadWithEdits(rawPayload.toString(), resolver);
	}

	private void writePayloadWithEdits(String newPayload, ContentResolver resolver) {
		boolean generateEdits = !getFilename(resolver).equals(FileUtils.CAPTURE_FILE);
		if (generateEdits) {
			OrgEdit edit = new OrgEdit(this, OrgEdit.TYPE.BODY, newPayload, resolver);
			edit.write(resolver);
		}
		setPayload(newPayload);
		write(resolver);
	}

	public void addAutomaticTimestamp() {
		Context context = MobileOrgApplication.getContext();
		boolean addTimestamp = PreferenceManager.getDefaultSharedPreferences(
				context).getBoolean("captureWithTimestamp", false);
		if(addTimestamp)
			setPayload(getPayload() + OrgUtils.getTimestamp());
	}
}

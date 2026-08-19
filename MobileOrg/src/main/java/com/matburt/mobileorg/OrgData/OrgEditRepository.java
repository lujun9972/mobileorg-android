package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;

public class OrgEditRepository {

	public enum UndoResult { SUCCESS, NOTHING_TO_UNDO, NODE_MISSING }

	private final ContentResolver resolver;

	public OrgEditRepository(ContentResolver resolver) {
		this.resolver = resolver;
	}

	/** Next batch id = max existing batch_id + 1 (starts at 1). */
	public long nextBatchId() {
		Long latest = getLatestBatchId();
		return (latest == null) ? 1 : latest + 1;
	}

	/** Highest non-null batch_id, or null when no undoable batch exists. */
	public Long getLatestBatchId() {
		Cursor cursor = resolver.query(Edits.CONTENT_URI,
				new String[]{Edits.BATCH_ID}, null, null,
				Edits.BATCH_ID + " DESC");
		try {
			if (cursor.moveToFirst() && !cursor.isNull(0))
				return cursor.getLong(0);
			return null;
		} finally {
			cursor.close();
		}
	}

	/** All edit rows of one batch, ordered by insertion. */
	public ArrayList<OrgEdit> getBatchEdits(long batchId) {
		Cursor cursor = resolver.query(Edits.CONTENT_URI,
				Edits.DEFAULT_COLUMNS, Edits.BATCH_ID + "=?",
				new String[]{String.valueOf(batchId)}, Edits.ID + " ASC");
		ArrayList<OrgEdit> result = new ArrayList<OrgEdit>();
		try {
			while (cursor.moveToNext())
				result.add(new OrgEdit(cursor));
		} finally {
			cursor.close();
		}
		return result;
	}

	/** Human label for the undo menu, e.g. "修改标题 '学日语'"; null when nothing to undo. */
	public String describeLatestBatch() {
		Long batchId = getLatestBatchId();
		if (batchId == null)
			return null;
		ArrayList<OrgEdit> edits = getBatchEdits(batchId);
		if (edits.isEmpty())
			return null;
		OrgEdit first = edits.get(0);
		String verb;
		switch (first.type) {
		case HEADING:  verb = "修改标题";   break;
		case TODO:     verb = "修改状态";   break;
		case PRIORITY: verb = "修改优先级"; break;
		case TAGS:     verb = "修改标签";   break;
		default:       verb = "编辑内容";   break;
		}
		return verb + " '" + first.title + "'";
	}
}

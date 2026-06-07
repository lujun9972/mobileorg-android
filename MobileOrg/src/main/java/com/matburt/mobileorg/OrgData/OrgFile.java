package com.matburt.mobileorg.OrgData;

import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.util.OrgFileNotFoundException;

/**
 * Pure domain model for an OrgFile.
 * Data access methods are in OrgFileRepository.
 * @see OrgFileRepository
 */
public class OrgFile {
	public static final String CAPTURE_FILE = "mobileorg.org";
	public static final String CAPTURE_FILE_ALIAS = "Captures";
	public static final String AGENDA_FILE = "agendas.org";
	public static final String AGENDA_FILE_ALIAS = "Agenda Views";
	
	public String filename = "";
	public String name = "";
	public String checksum = "";
	public boolean includeInOutline = true;
	public long id = -1;
	public long nodeId = -1;
		
	public OrgFile() {
	}
	
	public OrgFile(String filename, String name, String checksum) {
		this.checksum = checksum;
		this.filename = filename;
		
        if (name == null || name.equals("null"))
            this.name = filename;
        else
        	this.name = name;
	}
	
	public OrgFile(Cursor cursor) throws OrgFileNotFoundException {
		set(cursor);
	}
	
	public void set(Cursor cursor) throws OrgFileNotFoundException {
		if (cursor != null && cursor.getCount() > 0) {
			if(cursor.isBeforeFirst() || cursor.isAfterLast())
				cursor.moveToFirst();
			this.name = cursor.getString(cursor.getColumnIndexOrThrow(Files.NAME));
			this.filename = cursor.getString(cursor.getColumnIndexOrThrow(Files.FILENAME));
			this.checksum = cursor.getString(cursor.getColumnIndexOrThrow(Files.CHECKSUM));
			this.id = cursor.getLong(cursor.getColumnIndexOrThrow(Files.ID));
			this.nodeId = cursor.getLong(cursor.getColumnIndexOrThrow(Files.NODE_ID));
		} else {
			throw new OrgFileNotFoundException(
					"Failed to create OrgFile from cursor");
		}	
	}
	
	public boolean isEncrypted() {
		return filename.endsWith(".gpg") || filename.endsWith(".pgp")
				|| filename.endsWith(".enc") || filename.endsWith(".asc");
	}
	
	public boolean generateEditsForFile() {
		if(filename.equals(CAPTURE_FILE))
			return false;
		if(filename.equals(AGENDA_FILE))
			return false;
		return true;
	}
	
	public boolean equals(OrgFile file) {
		return filename.equals(file.filename) && name.equals(file.name);
	}
	
	@Override
	public String toString() {
		return filename;
	}
}

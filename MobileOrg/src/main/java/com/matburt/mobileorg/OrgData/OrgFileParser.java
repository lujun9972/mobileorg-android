package com.matburt.mobileorg.OrgData;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.PreferenceUtils;

public class OrgFileParser {

	private ContentResolver resolver;    
    private OrgDatabase db;
 
    private boolean combineAgenda = false;

    private ParseStack parseStack;
	private StringBuilder payload;
	private OrgFile orgFile;
	private OrgNodeParser orgNodeParser;
	private HashSet<String> excludedTags;
	
	public OrgFileParser(OrgDatabase db, ContentResolver resolver) {
		this.db = db;
		this.resolver = resolver;
	}

	private void init(OrgFile orgFile) {
		new OrgFileRepository(resolver).removeFile(orgFile);
		new OrgFileRepository(resolver).addFile(orgFile);
		this.orgFile = orgFile;

		this.parseStack = new ParseStack();
		this.parseStack.add(0, orgFile.nodeId, "");
		
		this.payload = new StringBuilder();
		
		this.orgNodeParser = new OrgNodeParser(
				new OrgFileRepository(resolver).getTodos());
	}
	
	public void parse(OrgFile orgFile, BufferedReader breader, Context context) {
		this.combineAgenda = PreferenceUtils.getCombineBlockAgendas();
		this.excludedTags = PreferenceUtils.getExcludedTags();
		
		parse(orgFile, breader);
	}
	
	public void parse(OrgFile orgFile, BufferedReader breader) {
		init(orgFile);
		db.beginTransaction();
		try {
			String currentLine;
			while ((currentLine = breader.readLine()) != null)
				parseLine(currentLine);
			
			// Add payload to the final node
			db.fastInsertNodePayload(parseStack.getCurrentNodeId(), this.payload.toString());

		} catch (IOException e) {}
		
		db.endTransaction();

		if(combineAgenda && orgFile.filename.equals(OrgFile.AGENDA_FILE)) {
			try {
				combineBlockAgendas();
			} catch (OrgFileNotFoundException e) {}
		}
	}

	private void parseLine(String line) {
		Log.v("parsing", "parseLine : " + line);
		if (TextUtils.isEmpty(line))
			return;

		int numstars = numberOfStars(line);
		if (numstars > 0) {
			db.fastInsertNodePayload(parseStack.getCurrentNodeId(), this.payload.toString());
			this.payload = new StringBuilder();
			parseHeading(line, numstars);
		} else {
			payload.append(line).append("\n");
		}
	}
	
	private void parseHeading(String thisLine, int numstars) {
		if (numstars == parseStack.getCurrentLevel()) { // Node on same level
			parseStack.pop();
		} else if (numstars < parseStack.getCurrentLevel()) { // Node on lower level
			while (numstars <= parseStack.getCurrentLevel())
				parseStack.pop();
		}
        
		OrgNode node = this.orgNodeParser.parseLine(thisLine, numstars);
		node.tags_inherited = parseStack.getCurrentTags();
		node.fileId = orgFile.id;
		node.parentId = parseStack.getCurrentNodeId();
		long newId = db.fastInsertNode(node);
		parseStack.add(numstars, newId, node.tags);      
    }

	private static final Pattern starPattern = Pattern.compile("^(\\**)\\s");
	private static int numberOfStars(String thisLine) {
		Matcher matcher = starPattern.matcher(thisLine);
		if(matcher.find()) {
			return matcher.end(1) - matcher.start(1);
		} else
			return 0;
	}
	
    
	private class ParseStack {
		private Stack<Pair<Integer, Long>> parseStack;
		private Stack<String> tagStack;

		public ParseStack() {
			this.parseStack = new Stack<Pair<Integer, Long>>();
			this.tagStack = new Stack<String>();
		}
		
		public void add(int level, long nodeId, String tags) {
			parseStack.push(new Pair<Integer, Long>(level, nodeId));
			tagStack.push(stripTags(tags));
		}
		
		private String stripTags(String tags) {
			if (excludedTags == null || TextUtils.isEmpty(tags))
				return tags;
			
			StringBuilder result = new StringBuilder();
			for (String tag: tags.split(":")) {
				if (!excludedTags.contains(tag)) {
					result.append(tag);
					result.append(":");
				}
			}
			
			if(!TextUtils.isEmpty(result))
				result.deleteCharAt(result.lastIndexOf(":"));
			
			return result.toString();
		}
		
		public void pop() {
			this.parseStack.pop();
			this.tagStack.pop();
		}
		
		public int getCurrentLevel() {
			return parseStack.peek().first;
		}
		
		public long getCurrentNodeId() {
			return parseStack.peek().second;
		}
		
		public String getCurrentTags() {
			return tagStack.peek();
		}
	}
	
	
	public static final String BLOCK_SEPARATOR_PREFIX = "#HEAD#";	
	private void combineBlockAgendas() throws OrgFileNotFoundException {		
		OrgNode agendaFile = new OrgNodeRepository(resolver).getOrgNodeFromFilename(
				OrgFile.AGENDA_FILE);
		
		String previousAgendaBlockTitle = "";
		OrgNode previousBlockNode = null;
		
		for(OrgNode node: new OrgNodeRepository(resolver).getChildren(agendaFile.id)) {
			if(node.name.indexOf(">") == -1)
				continue;
			
			String agendaBlockName = node.name.substring(0, node.name.indexOf(">"));
			String blockEntryName = node.name.substring(node.name.indexOf(">") + 1);
			
			if(!TextUtils.isEmpty(agendaBlockName)) { // Is a block agenda
				if(!agendaBlockName.equals(previousAgendaBlockTitle)) { // Create new node to contain block agenda	
					previousAgendaBlockTitle = agendaBlockName;

					previousBlockNode = new OrgNode();
					previousBlockNode.fileId = agendaFile.fileId;
					previousBlockNode.name = agendaBlockName;
					previousBlockNode.parentId = agendaFile.id;
					previousBlockNode.level = agendaFile.level + 1;
					previousBlockNode.id = db.fastInsertNode(previousBlockNode);
				}
				
				ArrayList<OrgNode> children = new OrgNodeRepository(resolver).getChildren(node.id);
				if(blockEntryName.startsWith("Day-agenda") || blockEntryName.startsWith("Week-agenda")) {
					for(OrgNode child: children)
						cloneChildren(child, previousBlockNode, child.name);
				} else
					cloneChildren(node, previousBlockNode, blockEntryName); // Normal cloning
				
				resolver.delete(OrgData.buildIdUri(node.id), null, null);
			}
		}
	}

	private void cloneChildren(OrgNode node, OrgNode parent, String blockTitle) {
		OrgNode blockSeparator = new OrgNode();
		blockSeparator.name = BLOCK_SEPARATOR_PREFIX + blockTitle;
		blockSeparator.fileId = parent.fileId;
		blockSeparator.parentId = parent.id;
		blockSeparator.level = parent.level + 1;
		db.fastInsertNode(blockSeparator);
		
		for(OrgNode child: new OrgNodeRepository(resolver).getChildren(node.id)) {
			OrgNode clonedChild = new OrgNode(child);
			clonedChild.parentId = parent.id;
			clonedChild.fileId = parent.fileId;
			clonedChild.level = parent.level + 1;
			long id = db.fastInsertNode(clonedChild);
			db.fastInsertNodePayload(id, child.getPayload());
			resolver.delete(OrgData.buildIdUri(node.id), null, null);
		}
	}
    
}

package com.matburt.mobileorg.Gui.Capture;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.util.Log;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

public class EditActivityControllerAddChild extends EditActivityController {

	private String nodeOlpPath;

	public EditActivityControllerAddChild(long node_id, ContentResolver resolver, String defaultTodo) {
		this.node = new OrgNode();
		this.resolver = resolver;
		this.repo = new OrgNodeRepository(resolver);

		this.node.todo = null;
		setupTodoAndParentId(node_id);

		if (this.node.todo == null)
			this.node.todo = defaultTodo;

		try {
			OrgNode parent = repo.getById(node_id);
			this.nodeOlpPath = repo.getOlpId(parent);
		} catch (OrgNodeNotFoundException e) {}

		this.node.addAutomaticTimestamp();
	}

	private void setupTodoAndParentId(long parentId) {
		if(parentId >= 0) {
			try {
				OrgNode parent = repo.getById(parentId);
				OrgNode realParent = repo.findOriginalNode(parent);
				this.node.parentId = realParent.id;
				this.node.todo = getTodo(realParent);
			} catch (OrgNodeNotFoundException e) {
				this.node.parentId = parentId;
			}
		}
		else
			this.node.parentId = OrgProviderUtils
					.getOrCreateCaptureFile(resolver).nodeId;
	}

	private String getTodo(OrgNode parent) {
		if (parent == null)
			return null;

		ArrayList<OrgNode> children = repo.getChildren(parent.id);

		if (children == null || children.size() == 0)
			return null;

		OrgNode lastSibling = children.get(children.size() - 1);
		return lastSibling.todo;
	}

	@Override
	public OrgNode getParentOrgNode() {
		try {
			return repo.getById(this.node.parentId);
		} catch (OrgNodeNotFoundException e) {}

		return new OrgNode();
	}

	@Override
	public void saveEdits(OrgNode newNode) {
		try {
			OrgEdit edit = repo.createParentNewheading(newNode, "");
			edit.write(resolver);
		} catch (IllegalStateException e) {
			Log.e("MobileOrg", e.getLocalizedMessage());
		}
		repo.write(newNode);
	}

	@Override
	public String getActionMode() {
		return ACTIONMODE_ADDCHILD;
	}

	@Override
	public boolean hasEdits(OrgNode newNode) {
		return !this.node.equals(newNode);
	}
}

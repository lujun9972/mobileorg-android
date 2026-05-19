package com.matburt.mobileorg.Gui.Capture;

import android.content.ContentResolver;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

public class EditActivityControllerEdit extends EditActivityController {

	private String nodeOlpPath;

	public EditActivityControllerEdit(long node_id, ContentResolver resolver) {
		this.resolver = resolver;
		this.repo = new OrgNodeRepository(resolver);
		try {
			this.node = repo.findOriginalNode(repo.getById(node_id));
			this.nodeOlpPath = repo.getOlpId(node);
		} catch (OrgNodeNotFoundException e) {}
	}

	public OrgNode getParentOrgNode() {
		OrgNode parent;
		try {
			parent = repo.getParent(node.id);
		} catch (OrgNodeNotFoundException e) {
			parent = new OrgNode();
			parent.parentId = -2;
		}
		return parent;
	}

	@Override
	public void saveEdits(OrgNode newNode) {
		repo.generateApplyWriteEdits(this.node, newNode, this.nodeOlpPath);
		repo.updateAllNodes(this.node);
	}

	@Override
	public String getActionMode() {
		return ACTIONMODE_EDIT;
	}

	@Override
	public boolean hasEdits(OrgNode newNode) {
		int numberOfEdits = 0;
		try {
			OrgNode clonedNode = repo.getById(node.id);
			numberOfEdits = repo.generateApplyEditNodes(clonedNode, newNode, "").size();
		} catch (OrgNodeNotFoundException e) {}

		return numberOfEdits > 0;
	}
}

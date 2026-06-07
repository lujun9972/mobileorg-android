package com.matburt.mobileorg.Gui;

import java.net.MalformedURLException;
import java.net.URL;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgRenderer;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

public class ViewFragment extends Fragment {

	protected ContentResolver resolver;
	protected WebView webView;
	private String currentFilename = "";
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.view_fragment, container);
		
		this.webView = (WebView) view.findViewById(R.id.viewfragment_webview);
		this.webView.setWebViewClient(new InternalWebViewClient());
		this.webView.getSettings().setBuiltInZoomControls(true);
		
		int backgroundColor = DefaultTheme.getTheme(getActivity()).defaultBackground;
		this.webView.setBackgroundColor(backgroundColor);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		this.resolver = getActivity().getContentResolver();
	}
	
	public void displayPayload(OrgNode node) {
		OrgRenderer renderer = new OrgRenderer(resolver, getActivity());
		String html = renderer.payloadToHTML(node);
		displayHtml(html);
	}

	public void display(OrgNode node, int levelOfRecursion, ContentResolver resolver) {
		OrgRenderer renderer = new OrgRenderer(resolver, getActivity());
		this.currentFilename = new OrgNodeRepository(resolver).getFilename(node);
		String html = renderer.toHTML(node, levelOfRecursion);
		displayHtml(html);
	}

	
	public void displayError() {
		String fontColor = DefaultTheme.getTheme(getActivity()).defaultFontColor;
		String html = "<html><body><font color='" + fontColor + "'>"
				+ getString(R.string.error_loading_node)
				+ "</font></body></html>";
		displayHtml(html);
	}
	
	public void displayHtml(String html) {
		this.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
	}

	protected class InternalWebViewClient extends WebViewClient {
		public InternalWebViewClient() {
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			if (url.startsWith("orgfile:")) {
				handleFileLink(url.substring("orgfile:".length()));
				return true;
			}
			if (url.startsWith("orgid:")) {
				handleIdLink(url.substring("orgid:".length()));
				return true;
			}
			if (url.startsWith("orginternal:")) {
				handleInternalLink(url.substring("orginternal:".length()));
				return true;
			}
			// Keep existing file:// handling for backward compatibility
			try {
				URL urlObj = new URL(url);
				if (urlObj.getProtocol().equals("file")) {
					handleInternalOrgUrl(url);
					return true;
				}
			} catch (MalformedURLException e) {
				Log.e("MobileOrg", "Malformed url :" + url);
			}
			// External links - open in system browser
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			try { startActivity(intent); } catch (ActivityNotFoundException e) {}
			return true;
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
		}

	}

	private void handleFileLink(String target) {
		try {
			OrgNodeRepository repo = new OrgNodeRepository(resolver);
			long nodeId;
			int headingIdx = target.indexOf("::*");
			if (headingIdx > -1) {
				String filename = target.substring(0, headingIdx);
				String heading = target.substring(headingIdx + 2); // skip "::*"
				nodeId = repo.getNodeByHeading(filename, heading);
			} else {
				nodeId = repo.getNodeFromPath("file://" + target);
			}
			Intent intent = new Intent(getActivity(), ViewActivity.class);
			intent.putExtra(ViewActivity.NODE_ID, nodeId);
			startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleIdLink(String id) {
		try {
			long nodeId = new OrgNodeRepository(resolver).getNodeById(id);
			Intent intent = new Intent(getActivity(), ViewActivity.class);
			intent.putExtra(ViewActivity.NODE_ID, nodeId);
			startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleInternalLink(String heading) {
		try {
			long nodeId = new OrgNodeRepository(resolver).getNodeByHeading(currentFilename, heading);
			Intent intent = new Intent(getActivity(), ViewActivity.class);
			intent.putExtra(ViewActivity.NODE_ID, nodeId);
			startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleInternalOrgUrl(String url) {
		try {
			long nodeId = new OrgNodeRepository(resolver).getNodeFromPath(url);

			Intent intent = new Intent(getActivity(), ViewActivity.class);
			intent.putExtra(ViewActivity.NODE_ID, nodeId);
			startActivity(intent);
		} catch (OrgFileNotFoundException e) {
		}
	}
}
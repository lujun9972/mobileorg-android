package com.matburt.mobileorg.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;

public class OrgUtils {

	public static final int MAX_SHARE_LENGTH = 400000;

	public static String getTimestamp() {
		SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd EEE HH:mm]");
		return sdf.format(new Date());
	}

	/**
	 * Serialize the node's subtree and hand it to the system share sheet
	 * (ACTION_SEND, text/plain). Shows a toast and returns silently if the
	 * node no longer exists; truncates overly large text to stay under the
	 * Binder transaction limit.
	 */
	public static void shareNode(Context context, long nodeId) {
		OrgNodeRepository repo = new OrgNodeRepository(context.getContentResolver());
		OrgNode node;
		String text;
		try {
			node = repo.getById(nodeId);
			text = repo.getSubtreeText(nodeId);
		} catch (OrgNodeNotFoundException e) {
			Toast.makeText(context, R.string.share_node_not_found, Toast.LENGTH_SHORT).show();
			return;
		}
		if (text.length() > MAX_SHARE_LENGTH) {
			text = text.substring(0, MAX_SHARE_LENGTH);
			Toast.makeText(context, R.string.share_truncated, Toast.LENGTH_SHORT).show();
		}
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, text);
		intent.putExtra(Intent.EXTRA_SUBJECT, node.name);
		context.startActivity(Intent.createChooser(intent,
				context.getString(R.string.menu_share)));
	}

    public static void setupSpinnerWithEmpty(Spinner spinner, ArrayList<String> data,
			String selection) {
		data.add("");
		setupSpinner(spinner, data, selection);
    }
    
	public static void setupSpinner(Spinner spinner, ArrayList<String> data,
			String selection) {		
		if(!TextUtils.isEmpty(selection) && !data.contains(selection))
			data.add(selection);
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(spinner.getContext(),
				android.R.layout.simple_spinner_item, data);
		adapter.setDropDownViewResource(R.layout.edit_spinner_layout);
		spinner.setAdapter(adapter);
		int pos = data.indexOf(selection);
		if (pos < 0) {
			pos = 0;
		}
		spinner.setSelection(pos, true);
	}
	
	private static final Pattern URL_PATTERN = Pattern
			.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:(//)?\\S+$");

	public static OrgNode getCaptureIntentContents(Intent intent) {
		String subject = intent
				.getStringExtra("android.intent.extra.SUBJECT");
		String text = intent.getStringExtra("android.intent.extra.TEXT");
		if (text == null)
			text = intent.getStringExtra("android.intent.extra.PROCESS_TEXT");

		if (text != null && subject != null && !subject.isEmpty()) {
			if (isUrlLike(text)) {
				subject = "[[" + text + "][" + subject + "]]";
				text = "";
			}
			// 非 URL（如 ReadEra 引用）：SUBJECT 作标题，正文保留全文
		} else if (text != null) {
			subject = generateTitle(text);
		}

		if (subject == null)
			subject = "";
		if (text == null)
			text = "";

		OrgNode node = new OrgNode();
		node.name = subject;
		node.setPayload(text);
		return node;
	}

	private static boolean isUrlLike(String text) {
		return URL_PATTERN.matcher(text.trim()).matches();
	}

	private static String generateTitle(String text) {
		for (String line : text.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;
			if (trimmed.length() <= 40)
				return trimmed;
			return trimmed.substring(0, 40) + "…";
		}
		return "";
	}

    public static String getStringFromResource(int resource, Context context) {
        try {
            InputStream is = context.getResources().openRawResource(resource);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            try {
                return FileUtils.read(br);
            } finally {
                br.close();
            }
        } catch (IOException e) {
            return "";
        }
    }

    public static void setTheme(Activity activity) {
    	String themeName = PreferenceUtils.getThemeName();

    	if(themeName.equals("Dark"))
    		activity.setTheme(R.style.Theme_MobileOrg_Dark);
    	else if(themeName.equals("Monochrome"))
    		activity.setTheme(R.style.Theme_MobileOrg_Monochrome);
    	else
    		activity.setTheme(R.style.Theme_MobileOrg_Light);
    }

	public static String lookUpValueFromArray(Context context, int keyID, int valID, String value) {
		String[] keys = context.getResources().getStringArray(keyID);
		String[] values = context.getResources().getStringArray(valID);
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value)) {
				return keys[i];
			}
		}
		return null;
	}

	public static final Pattern CHECKBOX_LINE = Pattern
			.compile("^(\\s*[-+]\\s+)\\[( |X|x)\\]\\s*(.*)$");

	public static String toggleCheckboxLine(String payload, int rawLineIdx) {
		if (payload == null || rawLineIdx < 0)
			return payload;
		String[] lines = payload.split("\n", -1);
		if (rawLineIdx >= lines.length)
			return payload;
		Matcher m = CHECKBOX_LINE.matcher(lines[rawLineIdx]);
		if (!m.find())
			return payload;
		String mark = m.group(2).trim().isEmpty() ? "[X]" : "[ ]";
		lines[rawLineIdx] = m.group(1) + mark + " " + m.group(3);
		return String.join("\n", lines);
	}

	private static final Pattern COOKIE_FRACTION = Pattern
			.compile("^(\\s*[-+]+.*\\s)\\[(\\d*)/(\\d*)\\]\\s*$");
	private static final Pattern COOKIE_PERCENT = Pattern
			.compile("^(\\s*[-+]+.*\\s)\\[(\\d+)%\\]\\s*$");

	public static String refreshCookies(String payload) {
		if (payload == null)
			return payload;
		String[] lines = payload.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			Matcher frac = COOKIE_FRACTION.matcher(lines[i]);
			Matcher pct = COOKIE_PERCENT.matcher(lines[i]);
			boolean isFrac = frac.find();
			if (!isFrac && !pct.find())
				continue;
			Matcher cookie = isFrac ? frac : pct;
			int cookieIndent = indentWidth(lines[i]);
			int done = 0;
			int total = 0;
			for (int j = i + 1; j < lines.length; j++) {
				String l = lines[j];
				if (!l.trim().isEmpty() && indentWidth(l) <= cookieIndent)
					break;
				Matcher m = CHECKBOX_LINE.matcher(l);
				if (m.find()) {
					total++;
					if (!m.group(2).trim().isEmpty())
						done++;
				}
			}
			if (total == 0)
				continue;
			if (isFrac)
				lines[i] = cookie.group(1) + "[" + done + "/" + total + "]";
			else
				lines[i] = cookie.group(1) + "["
						+ Math.round(100.0 * done / total) + "%]";
		}
		return String.join("\n", lines);
	}

	private static int indentWidth(String line) {
		int n = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == ' ' || c == '\t')
				n++;
			else
				break;
		}
		return n;
	}
}

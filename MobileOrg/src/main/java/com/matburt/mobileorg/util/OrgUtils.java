package com.matburt.mobileorg.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.OrgData.OrgNode;

public class OrgUtils {
	
	public static String getTimestamp() {
		SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd EEE HH:mm]");		
		return sdf.format(new Date());
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
	
	public static OrgNode getCaptureIntentContents(Intent intent) {
		String subject = intent
				.getStringExtra("android.intent.extra.SUBJECT");
		String text = intent.getStringExtra("android.intent.extra.TEXT");
		android.util.Log.d("MobileOrgCap", "[DEBUG-share] subject="
				+ (subject == null ? "null"
						: subject.length() + ":<" + subject.substring(0, Math.min(50, subject.length())) + ">")
				+ " text=" + (text == null ? "null"
						: text.length() + " chars/" + text.split("\n").length + " lines")
				+ " processText=" + (intent.getStringExtra("android.intent.extra.PROCESS_TEXT") == null
						? "null" : "present"));
		if (text == null)
			text = intent.getStringExtra("android.intent.extra.PROCESS_TEXT");

		if (text != null && subject != null && !subject.isEmpty()) {
			subject = "[[" + text + "][" + subject + "]]";
			text = "";
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
}

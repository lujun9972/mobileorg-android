package com.matburt.mobileorg.Services;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.util.Log;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

public class TimeclockDialog extends FragmentActivity {

	private OrgNode node;
	private int hour = 0;
	private int minute = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("MobileOrg", "[ClockIn] TimeclockDialog.onCreate");

		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.timeclock_dialog);
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
				android.R.drawable.ic_dialog_alert);

		Button button = (Button) findViewById(R.id.timeclock_cancel);
		button.setOnClickListener(cancelListener);
		button = (Button) findViewById(R.id.timeclock_edit);
		button.setOnClickListener(editListener);
		button = (Button) findViewById(R.id.timeclock_save);
		button.setOnClickListener(saveListener);
	}

	@Override
	protected void onStart() {
		super.onStart();

		TimeclockService service = TimeclockService.getInstance();
		Log.d("MobileOrg", "[ClockIn] TimeclockDialog.onStart: service=" + (service != null ? "alive" : "NULL"));

		String elapsedTime = (service != null) ? service.getElapsedTimeString() : "0:00";
		parseElapsedTime(elapsedTime);
		Log.d("MobileOrg", "[ClockIn] TimeclockDialog.onStart: elapsedTime=" + elapsedTime
				+ ", parsed hour=" + this.hour + ", minute=" + this.minute);

		setTitle("MobileOrg Timeclock");
		TextView textView = (TextView) findViewById(R.id.timeclock_text);

		long node_id = (service != null) ? service.getNodeID() : -1;
		Log.d("MobileOrg", "[ClockIn] TimeclockDialog.onStart: node_id from service=" + node_id);

		try {
			this.node = new OrgNode(node_id, getContentResolver());
			Log.d("MobileOrg", "[ClockIn] TimeclockDialog.onStart: node loaded, name=" + node.name);
		} catch (OrgNodeNotFoundException e) {
			Log.e("MobileOrg", "[ClockIn] TimeclockDialog.onStart: node not found! node_id=" + node_id, e);
			this.node = null;
		}

		if (textView != null) {
			String name = (node != null) ? node.name : "(unknown)";
			textView.setText(name + "@" + elapsedTime);
		}
	}

	private void parseElapsedTime(String elapsedTime) {
		String[] split = elapsedTime.trim().split(":");
		try {
			this.hour = Integer.parseInt(split[0]);
			this.minute = Integer.parseInt(split[1]);
		} catch(NumberFormatException e) {
			Log.w("MobileOrg", "[ClockIn] parseElapsedTime failed for: " + elapsedTime);
		}
	}

	private void saveClock(int hour, int minute) {
		Log.d("MobileOrg", "[ClockIn] saveClock called: hour=" + hour + ", minute=" + minute
				+ ", node=" + (node != null ? "id=" + node.id + " name=" + node.name : "NULL"));
		if (node == null) {
			Log.e("MobileOrg", "[ClockIn] saveClock ABORTED: node is null!");
			return;
		}
		TimeclockService service = TimeclockService.getInstance();
		long startTime = (service != null) ? service.getStartTime() : System.currentTimeMillis();
		long durationMillis = (hour * 3600L + minute * 60L) * 1000L;
		long endTime = startTime + durationMillis;
		String elapsedTime = String.format("%d:%02d", hour, minute);

		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Log.d("MobileOrg", "[ClockIn] saveClock: duration=" + elapsedTime
				+ ", startTime=" + startTime + " (" + sdf.format(new java.util.Date(startTime)) + ")"
				+ ", endTime=" + endTime + " (" + sdf.format(new java.util.Date(endTime)) + ")"
				+ ", durationMillis=" + durationMillis
				+ ", service=" + (service != null ? "alive" : "NULL"));

		Log.d("MobileOrg", "[ClockIn] saveClock: payload BEFORE = [" + node.getPayload() + "]");
		node.addLogbook(startTime, endTime, elapsedTime, getContentResolver());
		Log.d("MobileOrg", "[ClockIn] saveClock: payload AFTER  = [" + node.getPayload() + "]");
	}

	private void endTimeclock() {
		Log.d("MobileOrg", "[ClockIn] endTimeclock called");
		TimeclockService service = TimeclockService.getInstance();
		if (service != null) {
			Log.d("MobileOrg", "[ClockIn] endTimeclock: service alive, calling cancelNotification");
			service.cancelNotification();
		} else {
			Log.w("MobileOrg", "[ClockIn] endTimeclock: service is NULL!");
		}
		finish();
	}

	private View.OnClickListener cancelListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[ClockIn] CANCEL button clicked");
			endTimeclock();
		}
	};

	private View.OnClickListener saveListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[ClockIn] SAVE button clicked: hour=" + hour + ", minute=" + minute);
			saveClock(hour, minute);
			endTimeclock();
		}
	};

	private View.OnClickListener editListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[ClockIn] EDIT button clicked: current hour=" + hour + ", minute=" + minute);
			FragmentTransaction ft = getSupportFragmentManager()
					.beginTransaction();
			DialogFragment newFragment = DurationPickerFragment.newInstance(hour, minute);
			newFragment.show(ft, "DurationDialog");
		}
	};

	/**
	 * Duration picker using NumberPicker widgets.
	 * Replaces TimePickerDialog which returns wrong values on some devices.
	 */
	public static class DurationPickerFragment extends DialogFragment {
		private NumberPicker hourPicker;
		private NumberPicker minutePicker;

		public static DurationPickerFragment newInstance(int hour, int minute) {
			DurationPickerFragment f = new DurationPickerFragment();
			Bundle args = new Bundle();
			args.putInt("hour", hour);
			args.putInt("minute", minute);
			f.setArguments(args);
			return f;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			int initHour = getArguments().getInt("hour", 0);
			int initMinute = getArguments().getInt("minute", 0);
			Log.d("MobileOrg", "[ClockIn] DurationPicker.onCreateDialog: initHour=" + initHour + ", initMinute=" + initMinute);
			TimeclockDialog activity = (TimeclockDialog) getActivity();

			// Build layout with two NumberPickers
			LinearLayout layout = new LinearLayout(getActivity());
			layout.setOrientation(LinearLayout.HORIZONTAL);
			layout.setGravity(Gravity.CENTER);
			int pad = (int) (24 * getResources().getDisplayMetrics().density);
			layout.setPadding(pad, pad, pad, pad);

			// Hours picker
			hourPicker = new NumberPicker(getActivity());
			hourPicker.setMinValue(0);
			hourPicker.setMaxValue(23);
			hourPicker.setValue(initHour);
			hourPicker.setWrapSelectorWheel(true);
			layout.addView(hourPicker);

			// Separator
			TextView sep = new TextView(getActivity());
			sep.setText(" : ");
			sep.setTextSize(24);
			sep.setGravity(Gravity.CENTER);
			layout.addView(sep);

			// Minutes picker
			minutePicker = new NumberPicker(getActivity());
			minutePicker.setMinValue(0);
			minutePicker.setMaxValue(59);
			minutePicker.setValue(initMinute);
			minutePicker.setWrapSelectorWheel(true);
			layout.addView(minutePicker);

			return new AlertDialog.Builder(getActivity())
					.setTitle("Edit Duration (hours : minutes)")
					.setView(layout)
					.setPositiveButton("OK", (dialog, which) -> {
						// Must clear focus to commit scrolled value before reading
						hourPicker.clearFocus();
						minutePicker.clearFocus();
						int h = hourPicker.getValue();
						int m = minutePicker.getValue();
						Log.d("MobileOrg", "[ClockIn] DurationPicker OK: picked hour=" + h + ", minute=" + m);
						activity.saveClock(h, m);
						activity.endTimeclock();
					})
					.setNegativeButton("Cancel", null)
					.create();
		}
	}
}

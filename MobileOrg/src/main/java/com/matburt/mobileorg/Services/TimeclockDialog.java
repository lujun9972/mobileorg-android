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

		String elapsedTime = TimeclockService.getInstance().getElapsedTimeString();
		parseElapsedTime(elapsedTime);

		setTitle("MobileOrg Timeclock");
		TextView textView = (TextView) findViewById(R.id.timeclock_text);

		long node_id = TimeclockService.getInstance().getNodeID();

		try {
			this.node = new OrgNode(node_id, getContentResolver());
		} catch (OrgNodeNotFoundException e) {
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
		}
	}

	private void saveClock(int hour, int minute) {
		if (node == null) return;
		TimeclockService service = TimeclockService.getInstance();
		long startTime = (service != null) ? service.getStartTime() : System.currentTimeMillis();
		long durationMillis = (hour * 3600L + minute * 60L) * 1000L;
		long endTime = startTime + durationMillis;
		String elapsedTime = String.format("%d:%02d", hour, minute);
		Log.d("MobileOrg", "saveClock: duration=" + elapsedTime
				+ ", startTime=" + startTime + ", endTime=" + endTime);
		node.addLogbook(startTime, endTime, elapsedTime, getContentResolver());
	}

	private void endTimeclock() {
		TimeclockService.getInstance().cancelNotification();
		finish();
	}

	private View.OnClickListener cancelListener = new View.OnClickListener() {
		public void onClick(View v) {
			endTimeclock();
		}
	};

	private View.OnClickListener saveListener = new View.OnClickListener() {
		public void onClick(View v) {
			saveClock(hour, minute);
			endTimeclock();
		}
	};

	private View.OnClickListener editListener = new View.OnClickListener() {
		public void onClick(View v) {
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
						int h = hourPicker.getValue();
						int m = minutePicker.getValue();
						Log.d("MobileOrg", "DurationPicker: hour=" + h + ", minute=" + m);
						activity.saveClock(h, m);
						activity.endTimeclock();
					})
					.setNegativeButton("Cancel", null)
					.create();
		}
	}
}

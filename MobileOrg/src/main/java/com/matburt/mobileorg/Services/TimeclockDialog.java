package com.matburt.mobileorg.Services;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
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
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
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
		Log.d("MobileOrg", "[TimeclockDialog] onStart: service=" + (service != null ? "alive" : "NULL"));

		setTitle("MobileOrg Timeclock");

		// Pomodoro section
		LinearLayout pomoSection = findViewById(R.id.pomodoro_section);
		if (service != null && service.isPomodoroRunning()) {
			pomoSection.setVisibility(View.VISIBLE);
			TextView pomoTime = findViewById(R.id.pomodoro_time);
			String remaining = service.getPomodoroRemainingString();
			pomoTime.setText("\uD83C\uDF45 " + remaining);
			if (service.isPomodoroTimedOut()) {
				pomoTime.setTextColor(Color.RED);
			}
			Button stopBtn = findViewById(R.id.pomodoro_stop_button);
			stopBtn.setOnClickListener(v -> {
				sendServiceAction(TimeclockService.ACTION_POMODORO_STOP);
				pomoSection.setVisibility(View.GONE);
				maybeFinish();
			});
		} else {
			pomoSection.setVisibility(View.GONE);
		}

		// Clock section
		LinearLayout clockSection = findViewById(R.id.clock_section);
		if (service != null && service.isClockedIn()) {
			clockSection.setVisibility(View.VISIBLE);
			OrgNode clockNode = service.getClockNode();
			this.node = clockNode;
			String name = (clockNode != null) ? clockNode.name : "(unknown)";
			String elapsed = service.getClockElapsedString();
			parseElapsedTime(elapsed);
			TextView textView = findViewById(R.id.timeclock_text);
			textView.setText(name + " @ " + elapsed);
		} else {
			clockSection.setVisibility(View.GONE);
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

	private View.OnClickListener cancelListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[TimeclockDialog] CANCEL button clicked");
			Intent intent = new Intent(TimeclockDialog.this, TimeclockService.class);
			intent.setAction(TimeclockService.ACTION_CLOCK_CANCEL);
			startService(intent);
			maybeFinish();
		}
	};

	private View.OnClickListener saveListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[TimeclockDialog] SAVE button clicked: hour=" + hour + ", minute=" + minute);
			Intent intent = new Intent(TimeclockDialog.this, TimeclockService.class);
			intent.setAction(TimeclockService.ACTION_CLOCK_OUT);
			intent.putExtra(TimeclockService.CLOCK_DURATION, hour * 60 + minute);
			startService(intent);
			maybeFinish();
		}
	};

	private View.OnClickListener editListener = new View.OnClickListener() {
		public void onClick(View v) {
			Log.d("MobileOrg", "[TimeclockDialog] EDIT button clicked: current hour=" + hour + ", minute=" + minute);
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
			Log.d("MobileOrg", "[TimeclockDialog] DurationPicker.onCreateDialog: initHour=" + initHour + ", initMinute=" + initMinute);
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
						Log.d("MobileOrg", "[TimeclockDialog] DurationPicker OK: picked hour=" + h + ", minute=" + m);
						Intent intent = new Intent(getActivity(), TimeclockService.class);
						intent.setAction(TimeclockService.ACTION_CLOCK_OUT);
						intent.putExtra(TimeclockService.CLOCK_DURATION, h * 60 + m);
						getActivity().startService(intent);
						activity.maybeFinish();
					})
					.setNegativeButton("Cancel", null)
					.create();
		}
	}

	private void maybeFinish() {
		TimeclockService service = TimeclockService.getInstance();
		if (service == null || (!service.isPomodoroRunning() && !service.isClockedIn())) {
			finish();
		}
	}

	private void sendServiceAction(String action) {
		Intent intent = new Intent(this, TimeclockService.class);
		intent.setAction(action);
		startService(intent);
	}
}

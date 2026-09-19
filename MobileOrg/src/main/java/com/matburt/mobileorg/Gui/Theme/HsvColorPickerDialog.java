package com.matburt.mobileorg.Gui.Theme;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Convenience wrapper showing the HSV picker inside an AlertDialog. */
public class HsvColorPickerDialog {

	public interface OnColorPicked {
		void onColor(int color);
	}

	public static void show(AppCompatActivity activity, int currentColor,
			CharSequence title, final OnColorPicked cb) {
		HsvColorPickerView view = new HsvColorPickerView(activity);
		view.setColor(currentColor);
		new AlertDialog.Builder(activity)
				.setTitle(title)
				.setView(view)
				.setPositiveButton(android.R.string.ok, (d, w) ->
						cb.onColor(view.getColor()))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}

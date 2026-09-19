package com.matburt.mobileorg.Gui.Theme;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * In-app editor for the Custom theme. All edits go to a working set and
 * reach storage only through Save (preview is not commit). Base radio and
 * copy-from-preset are working-set operations too.
 */
public class ThemeEditorActivity extends AppCompatActivity {

	private static final Object[][] BASIC_COLORS = {
			{ CustomThemeStore.KEY_BG, R.string.theme_editor_bg },
			{ CustomThemeStore.KEY_FG, R.string.theme_editor_fg },
	};
	private static final Object[][] SEMANTIC_COLORS = {
			{ CustomThemeStore.KEY_GRAY, R.string.theme_editor_gray },
			{ CustomThemeStore.KEY_C1, R.string.theme_editor_c1 },
			{ CustomThemeStore.KEY_CA, R.string.theme_editor_ca },
			{ CustomThemeStore.KEY_C3, R.string.theme_editor_c3 },
			{ CustomThemeStore.KEY_C4, R.string.theme_editor_c4 },
	};
	private static final String[] BASE_NAMES = {
			CustomThemeStore.BASE_DARK, CustomThemeStore.BASE_LIGHT,
			CustomThemeStore.BASE_MONO };

	private final Map<String, Integer> workingColors = new HashMap<>();
	private final int[] workingLevels = new int[10];
	private String workingBase;
	private boolean dirty = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		OrgUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_theme_editor);
		// OrgUtils.applyCustomThemeWindow(this);  // wired in the window-overlay task
		loadWorkingSet();
		buildPreview();
		buildBaseSelector();
		buildColorRows();
		wireButtons();
	}

	private void loadWorkingSet() {
		Map<String, String> stored = CustomThemeStore.loadAll();
		DefaultTheme seed = new DefaultTheme();
		workingColors.put(CustomThemeStore.KEY_BG,
				parseOr(stored.get(CustomThemeStore.KEY_BG), seed.defaultBackground));
		workingColors.put(CustomThemeStore.KEY_FG,
				parseOr(stored.get(CustomThemeStore.KEY_FG), seed.defaultForeground));
		workingColors.put(CustomThemeStore.KEY_GRAY,
				parseOr(stored.get(CustomThemeStore.KEY_GRAY), seed.gray));
		workingColors.put(CustomThemeStore.KEY_C1,
				parseOr(stored.get(CustomThemeStore.KEY_C1), seed.c1Red));
		workingColors.put(CustomThemeStore.KEY_CA,
				parseOr(stored.get(CustomThemeStore.KEY_CA), seed.caLGreen));
		workingColors.put(CustomThemeStore.KEY_C3,
				parseOr(stored.get(CustomThemeStore.KEY_C3), seed.c3Yellow));
		workingColors.put(CustomThemeStore.KEY_C4,
				parseOr(stored.get(CustomThemeStore.KEY_C4), seed.c4Blue));
		for (int i = 0; i < 10; i++)
			workingLevels[i] = seed.levelColors[i];
		String csv = stored.get(CustomThemeStore.KEY_LEVEL_COLORS);
		if (csv != null && !csv.isEmpty()) {
			try {
				String[] parts = csv.split(",");
				for (int i = 0; i < 10; i++)
					workingLevels[i] = Color.parseColor(parts[i % parts.length].trim());
			} catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
				// keep seed levels
			}
		}
		String base = stored.get(CustomThemeStore.KEY_BASE);
		workingBase = CustomThemeStore.getBase();
		if (base != null)
			workingBase = base;
	}

	private static int parseOr(String s, int fallback) {
		if (s == null)
			return fallback;
		try {
			return Color.parseColor(s);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	private void buildPreview() {
		LinearLayout c = findViewById(R.id.previewContainer);
		c.removeAllViews();
		c.setPadding(dp(12), dp(12), dp(12), dp(12));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(workingColors.get(CustomThemeStore.KEY_BG));
		bg.setCornerRadius(dp(6));
		c.setBackground(bg);

		int fg = workingColors.get(CustomThemeStore.KEY_FG);
		int gray = workingColors.get(CustomThemeStore.KEY_GRAY);

		SpannableStringBuilder line1 = new SpannableStringBuilder();
		style(line1, "[#A] ", workingColors.get(CustomThemeStore.KEY_C3));
		style(line1, "TODO ", workingColors.get(CustomThemeStore.KEY_C1));
		style(line1, getString(R.string.theme_editor_preview_todo), fg);
		style(line1, " :work:", gray);
		c.addView(previewLine(line1));

		SpannableStringBuilder line2 = new SpannableStringBuilder();
		style(line2, "  •  ", workingLevels[1]);
		int start = line2.length();
		style(line2, "org mode", workingColors.get(CustomThemeStore.KEY_C4));
		line2.setSpan(new UnderlineSpan(), start, line2.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		c.addView(previewLine(line2));

		SpannableStringBuilder line3 = new SpannableStringBuilder();
		style(line3, "DONE ", workingColors.get(CustomThemeStore.KEY_CA));
		style(line3, getString(R.string.theme_editor_preview_done), fg);
		c.addView(previewLine(line3));
	}

	private TextView previewLine(SpannableStringBuilder sb) {
		TextView tv = new TextView(this);
		tv.setText(sb);
		tv.setTextSize(16);
		tv.setPadding(0, dp(4), 0, dp(4));
		return tv;
	}

	private void style(SpannableStringBuilder sb, String text, int color) {
		int start = sb.length();
		sb.append(text);
		sb.setSpan(new ForegroundColorSpan(color), start, sb.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void buildBaseSelector() {
		((RadioButton) findViewById(R.id.baseDark)).setChecked(
				CustomThemeStore.BASE_DARK.equals(workingBase));
		((RadioButton) findViewById(R.id.baseLight)).setChecked(
				CustomThemeStore.BASE_LIGHT.equals(workingBase));
		((RadioButton) findViewById(R.id.baseMono)).setChecked(
				CustomThemeStore.BASE_MONO.equals(workingBase));
		((RadioButton) findViewById(R.id.baseDark)).setOnCheckedChangeListener((b, on) -> {
			if (on) setBase(CustomThemeStore.BASE_DARK);
		});
		((RadioButton) findViewById(R.id.baseLight)).setOnCheckedChangeListener((b, on) -> {
			if (on) setBase(CustomThemeStore.BASE_LIGHT);
		});
		((RadioButton) findViewById(R.id.baseMono)).setOnCheckedChangeListener((b, on) -> {
			if (on) setBase(CustomThemeStore.BASE_MONO);
		});
	}

	private void setBase(String base) {
		if (!base.equals(workingBase)) {
			workingBase = base;
			dirty = true;
		}
	}

	private void buildColorRows() {
		LinearLayout basic = findViewById(R.id.basicContainer);
		LinearLayout semantic = findViewById(R.id.semanticContainer);
		basic.removeAllViews();
		semantic.removeAllViews();
		for (Object[] def : BASIC_COLORS)
			basic.addView(colorRow((String) def[0], (Integer) def[1]));
		for (Object[] def : SEMANTIC_COLORS)
			semantic.addView(colorRow((String) def[0], (Integer) def[1]));
		buildLevelRows();
	}

	private View colorRow(String key, int labelRes) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, dp(8), 0, dp(8));
		TextView tv = new TextView(this);
		tv.setText(labelRes);
		tv.setLayoutParams(new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		View swatch = new View(this);
		swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(80), dp(36)));
		paintSwatch(swatch, workingColors.get(key));
		swatch.setOnClickListener(v ->
				HsvColorPickerDialog.show(this, workingColors.get(key), tv.getText(),
						color -> {
							workingColors.put(key, color);
							paintSwatch(swatch, color);
							buildPreview();
							dirty = true;
						}));
		row.addView(tv);
		row.addView(swatch);
		return row;
	}

	private void buildLevelRows() {
		LinearLayout container = findViewById(R.id.levelsContainer);
		container.removeAllViews();
		for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int col = 0; col < 5; col++) {
				int level = rowIdx * 5 + col;
				row.addView(levelCell(level));
			}
			container.addView(row);
		}
	}

	private View levelCell(int level) {
		LinearLayout cell = new LinearLayout(this);
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER_HORIZONTAL);
		cell.setPadding(0, dp(8), dp(8), 0);
		View swatch = new View(this);
		swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(28)));
		paintSwatch(swatch, workingLevels[level]);
		swatch.setOnClickListener(v ->
				HsvColorPickerDialog.show(this, workingLevels[level],
						getString(R.string.theme_editor_level, level + 1),
						color -> {
							workingLevels[level] = color;
							paintSwatch(swatch, color);
							buildPreview();
							dirty = true;
						}));
		TextView tv = new TextView(this);
		tv.setText(getString(R.string.theme_editor_level, level + 1));
		tv.setTextSize(11);
		cell.addView(swatch);
		cell.addView(tv);
		return cell;
	}

	private void paintSwatch(View v, int color) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(dp(4));
		v.setBackground(d);
	}

	private void wireButtons() {
		findViewById(R.id.copyBtn).setOnClickListener(v -> showCopyDialog());
		findViewById(R.id.resetBtn).setOnClickListener(v -> {
			applyPresetValues(CustomThemeStore.presetValues(CustomThemeStore.BASE_DARK));
			refreshAll();
			dirty = true;
		});
		findViewById(R.id.saveBtn).setOnClickListener(v -> save());
	}

	private void showCopyDialog() {
		String[] labels = {
				getString(R.string.theme_base_dark),
				getString(R.string.theme_base_light),
				getString(R.string.theme_base_mono) };
		new AlertDialog.Builder(this)
				.setTitle(R.string.theme_editor_copy)
				.setItems(labels, (d, which) -> {
					applyPresetValues(CustomThemeStore.presetValues(BASE_NAMES[which]));
					refreshAll();
					dirty = true;
				})
				.show();
	}

	private void applyPresetValues(Map<String, String> values) {
		for (Map.Entry<String, String> e : values.entrySet()) {
			if (CustomThemeStore.KEY_LEVEL_COLORS.equals(e.getKey())) {
				String[] parts = e.getValue().split(",");
				for (int i = 0; i < 10 && i < parts.length; i++)
					workingLevels[i] = Color.parseColor(parts[i].trim());
			} else if (workingColors.containsKey(e.getKey())) {
				workingColors.put(e.getKey(), Color.parseColor(e.getValue()));
			}
		}
	}

	private void refreshAll() {
		buildPreview();
		buildColorRows();
	}

	private void save() {
		Map<String, String> values = new HashMap<>();
		values.put(CustomThemeStore.KEY_GRAY, hex(workingColors.get(CustomThemeStore.KEY_GRAY)));
		values.put(CustomThemeStore.KEY_C1, hex(workingColors.get(CustomThemeStore.KEY_C1)));
		values.put(CustomThemeStore.KEY_CA, hex(workingColors.get(CustomThemeStore.KEY_CA)));
		values.put(CustomThemeStore.KEY_C3, hex(workingColors.get(CustomThemeStore.KEY_C3)));
		values.put(CustomThemeStore.KEY_C4, hex(workingColors.get(CustomThemeStore.KEY_C4)));
		values.put(CustomThemeStore.KEY_FG, hex(workingColors.get(CustomThemeStore.KEY_FG)));
		values.put(CustomThemeStore.KEY_BG, hex(workingColors.get(CustomThemeStore.KEY_BG)));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			if (i > 0) sb.append(',');
			sb.append(hex(workingLevels[i]));
		}
		values.put(CustomThemeStore.KEY_LEVEL_COLORS, sb.toString());

		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = p.edit();
		editor.putString(CustomThemeStore.KEY_BASE, workingBase);
		CustomThemeStore.saveAll(values, editor);
		editor.apply();
		dirty = false;
		Toast.makeText(this, R.string.theme_editor_saved, Toast.LENGTH_SHORT).show();
		finish();
	}

	private static String hex(int color) {
		return String.format("#%06X", 0xFFFFFF & color);
	}

	@Override
	public void onBackPressed() {
		if (!dirty) {
			super.onBackPressed();
			return;
		}
		new AlertDialog.Builder(this)
				.setMessage(R.string.theme_editor_dirty_confirm)
				.setPositiveButton(R.string.theme_editor_discard, (d, w) -> {
					dirty = false;
					finish();
				})
				.setNegativeButton(R.string.theme_editor_cancel, null)
				.show();
	}

}

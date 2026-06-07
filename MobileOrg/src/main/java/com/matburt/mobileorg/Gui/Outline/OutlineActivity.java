package com.matburt.mobileorg.Gui.Outline;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Agenda.AgendasActivity;
import com.matburt.mobileorg.Gui.Statistics.StatisticsActivity;
import com.matburt.mobileorg.Gui.Wizard.WizardActivity;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.Services.TimeclockService;
import com.matburt.mobileorg.Settings.SettingsActivity;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.HashSet;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.widget.ToggleButton;

public class OutlineActivity extends AppCompatActivity {

	public final static String NODE_ID = "node_id";
	private final static String OUTLINE_NODES = "nodes";
	private final static String OUTLINE_CHECKED_POS = "selection";
	private final static String OUTLINE_SCROLL_POS = "scrollPosition";

    public final static String SYNC_FAILED = "com.matburt.mobileorg.SYNC_FAILED";

	private Long node_id;

	private OutlineListView listView;

	private OutlineSyncController syncController;
	private OutlineTimeclockController timeclockController;

	private OutlineTagFilter tagFilter = new OutlineTagFilter();
	private boolean programmaticChipChange = false;
	private boolean allPreviouslyChecked = true;
	private Chip allFilterChip;
	private static final String STATE_FILTER_TAGS = "filter_tags";
	private static final String STATE_FILTER_AND_MODE = "filter_and_mode";
	private String currentThemeName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		currentThemeName = PreferenceUtils.getThemeName();
		OrgUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.outline);

		Intent intent = getIntent();
		node_id = intent.getLongExtra(NODE_ID, -1);

		if (this.node_id == -1)
			displayNewUserDialogs();

		// Restore filter state
		if (savedInstanceState != null) {
			String[] savedTags = savedInstanceState.getStringArray(STATE_FILTER_TAGS);
			boolean savedAndMode = savedInstanceState.getBoolean(STATE_FILTER_AND_MODE, false);
			if (savedTags != null && savedTags.length > 0) {
				tagFilter.setSelectedTags(savedTags);
				tagFilter.setAndMode(savedAndMode);
				tagFilter.rebuild(getContentResolver());
			}
		} else {
			String[] intentTags = intent.getStringArrayExtra("filter_tags");
			boolean intentAndMode = intent.getBooleanExtra("filter_and_mode", false);
			if (intentTags != null && intentTags.length > 0) {
				tagFilter.setSelectedTags(intentTags);
				tagFilter.setAndMode(intentAndMode);
				tagFilter.rebuild(getContentResolver());
			}
		}

		setupList();

		syncController = new OutlineSyncController(this, this::refreshDisplay, this::setupFilterBar);
		syncController.onCreate();

		timeclockController = new OutlineTimeclockController(this);
		timeclockController.onCreate();

		refreshDisplay();
	}

	@Override
	protected void onResume() {
		MobileOrgApplication.log("OutlineActivity.onResume()");
		super.onResume();
		String newTheme = PreferenceUtils.getThemeName();
		if (newTheme != null && !newTheme.equals(currentThemeName)) {
			currentThemeName = newTheme;
			recreate();
			return;
		}
		refreshTitle();
		setupFilterBar();
		invalidateOptionsMenu();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (tagFilter.isActive()) {
			outState.putStringArray(STATE_FILTER_TAGS, tagFilter.getSelectedTagsArray());
			outState.putBoolean(STATE_FILTER_AND_MODE, tagFilter.isAndMode());
		}
	}

	@Override
	protected void onDestroy() {
		MobileOrgApplication.log("OutlineActivity.onDestroy()");
		syncController.onDestroy();
		timeclockController.onDestroy();
		super.onDestroy();
	}

	private void setupList() {
		listView = (OutlineListView) findViewById(R.id.outline_list);
		listView.setActivity(this);
		listView.setEmptyView(findViewById(R.id.outline_list_empty));
		OutlineAdapter adapter = (OutlineAdapter) listView.getAdapter();
		adapter.setFilter(tagFilter);
		adapter.refresh();
	}

	private void displayNewUserDialogs() {
		if (!PreferenceUtils.isSyncConfigured())
			runShowWizard(null);

		if (PreferenceUtils.isUpgradedVersion())
			showUpgradePopup();
	}

    @Override
    protected void onNewIntent(Intent intent) {
        if (SYNC_FAILED.equals(intent.getAction())) {
            Bundle extrasBundle = intent.getExtras();
            String errorMsg = extrasBundle.getString("ERROR_MESSAGE");
            showSyncFailPopup(errorMsg);
        }
    }

	public void refreshDisplay() {
		this.listView.refresh();
		refreshTitle();
	}

	private void setupFilterBar() {
		ArrayList<String> tags = new OrgFileRepository(getContentResolver()).getTags();
		View filterBar = findViewById(R.id.tag_filter_bar);
		ChipGroup chipGroup = findViewById(R.id.tag_filter_chips);

		if (filterBar == null || chipGroup == null) {
			return;
		}

		if (tags.isEmpty()) {
			filterBar.setVisibility(View.GONE);
			return;
		}

		ToggleButton modeToggle = findViewById(R.id.tag_filter_mode);

		programmaticChipChange = true;
		chipGroup.removeAllViews();

		// Create "All" chip (stored as field for reliable reference)
		allFilterChip = new Chip(this);
		allFilterChip.setText("All");
		allFilterChip.setCheckable(true);
		allFilterChip.setCheckedIconVisible(true);
		allFilterChip.setChecked(!tagFilter.isActive());
		chipGroup.addView(allFilterChip);

		// Create tag chips with filter style
		HashSet<String> validTags = new HashSet<>(tags);
		for (String tag : tags) {
			Chip chip = new Chip(this);
			chip.setText(tag);
			chip.setCheckable(true);
			chip.setCheckedIconVisible(true);
			chip.setChecked(tagFilter.isActive() && containsTag(tagFilter.getSelectedTagsArray(), tag));
			chip.setTag(tag);
			chipGroup.addView(chip);
		}

		// Clean up stale tags: remove selected tags that no longer exist in Tags table
		if (tagFilter.isActive()) {
			String[] selected = tagFilter.getSelectedTagsArray();
			boolean anyRemoved = false;
			for (String sel : selected) {
				if (!validTags.contains(sel)) {
					tagFilter.setTagSelected(sel, false);
					anyRemoved = true;
				}
			}
			if (!tagFilter.isActive()) {
				allFilterChip.setChecked(true);
			}
		}

		modeToggle.setChecked(tagFilter.isAndMode());

		programmaticChipChange = false;
		allPreviouslyChecked = allFilterChip.isChecked();

		chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
			if (programmaticChipChange) return;
			handleChipChange();
		});

		modeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (programmaticChipChange) return;
			tagFilter.setAndMode(isChecked);
			applyFilter();
		});

		filterBar.setVisibility(View.VISIBLE);
		updateEmptyView();

		if (tagFilter.isActive()) {
			applyFilter();
		}
	}

	private boolean containsTag(String[] tags, String tag) {
		for (String t : tags) {
			if (t.equals(tag)) return true;
		}
		return false;
	}

	private void handleChipChange() {
		ChipGroup chipGroup = findViewById(R.id.tag_filter_chips);
		boolean allNowChecked = allFilterChip.isChecked();

		programmaticChipChange = true;

		if (allNowChecked && !allPreviouslyChecked) {
			// All was unchecked, now checked → user clicked All
			// Uncheck all tags, clear filter
			tagFilter.clearAll();
			for (int i = 1; i < chipGroup.getChildCount(); i++) {
				((Chip) chipGroup.getChildAt(i)).setChecked(false);
			}
		} else {
			// Tags were toggled
			if (allNowChecked && allPreviouslyChecked) {
				// All was already checked, user clicked a tag → uncheck All
				allFilterChip.setChecked(false);
			}

			tagFilter.clearAll();
			for (int i = 1; i < chipGroup.getChildCount(); i++) {
				Chip chip = (Chip) chipGroup.getChildAt(i);
				if (chip.isChecked()) {
					tagFilter.setTagSelected((String) chip.getTag(), true);
				}
			}

			if (!tagFilter.isActive()) {
				allFilterChip.setChecked(true);
			}
		}

		allPreviouslyChecked = allFilterChip.isChecked();
		programmaticChipChange = false;
		applyFilter();
	}

	private void applyFilter() {
		tagFilter.rebuild(getContentResolver());
		OutlineAdapter adapter = (OutlineAdapter) listView.getAdapter();
		adapter.setFilter(tagFilter);
		adapter.refresh();
		updateEmptyView();
	}

	private void updateEmptyView() {
		View emptyButtons = findViewById(R.id.outline_list_empty_buttons);
		TextView filterEmpty = findViewById(R.id.outline_list_filter_empty);

		if (tagFilter.isActive() && listView.getAdapter().getCount() == 0) {
			if (emptyButtons != null) emptyButtons.setVisibility(View.GONE);
			if (filterEmpty != null) filterEmpty.setVisibility(View.VISIBLE);
		} else {
			if (emptyButtons != null) emptyButtons.setVisibility(View.VISIBLE);
			if (filterEmpty != null) filterEmpty.setVisibility(View.GONE);
		}
	}


	private void refreshTitle() {
		this.getSupportActionBar().setTitle("MobileOrg " + getChangesString());
	}

    private String getChangesString() {
    	int changes = new OrgFileRepository(getContentResolver()).getChangesCount();
    	if(changes > 0)
    		return "[" + changes + "]";
    	else
    		return "";
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.outline_menu, menu);
	    syncController.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem pomoItem = menu.findItem(R.id.menu_pomodoro);
		if (pomoItem != null) {
			TimeclockService service = TimeclockService.getInstance();
			boolean running = service != null && service.isPomodoroRunning();
			pomoItem.setTitle(running ? getString(R.string.menu_pomodoro_stop) : getString(R.string.menu_pomodoro));
			pomoItem.setIcon(running ? R.drawable.ic_media_stop : R.drawable.ic_menu_pomodoro);
		}
		syncController.onPrepareOptionsMenu(menu);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			listView.collapseCurrent();
			return true;
		} else if (id == R.id.menu_sync) {
			syncController.runSynchronize();
			return true;
		} else if (id == R.id.menu_settings) {
			runShowSettings(null);
			return true;
		} else if (id == R.id.menu_outline) {
			runExpandableOutline(-1);
			return true;
		} else if (id == R.id.menu_agenda) {
			runAgenda();
			return true;
		} else if (id == R.id.menu_capturechild) {
			OutlineActionMode.runCaptureActivity(listView.getCheckedNodeId(), this);
			return true;
		} else if (id == R.id.menu_search) {
			return runSearch();
		} else if (id == R.id.menu_help) {
			runHelp(null);
			return true;
		} else if (id == R.id.menu_record) {
			long checkedNodeId = listView.getCheckedNodeId();
			if (checkedNodeId >= 0) {
				timeclockController.tryStartRecording(checkedNodeId);
			}
			return true;
		} else if (id == R.id.menu_pomodoro) {
			TimeclockService service = TimeclockService.getInstance();
			if (service != null && service.isPomodoroRunning()) {
				Intent intent = new Intent(this, TimeclockService.class);
				intent.setAction(TimeclockService.ACTION_POMODORO_STOP);
				Compat.startService(this, intent);
			} else {
				timeclockController.showPomodoroDurationPicker();
			}
			return true;
		} else if (id == R.id.menu_statistics) {
			startActivity(new Intent(this, StatisticsActivity.class));
			return true;
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		timeclockController.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	public void tryStartRecording(long nodeId) {
		timeclockController.tryStartRecording(nodeId);
	}

	public void runHelp(View view) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("https://github.com/matburt/mobileorg-android/wiki"));
    	startActivity(intent);
    }

    public void runSynchronize(View view) {
		syncController.runSynchronize();
    }

	public void runShowSettings(View view) {
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
	}

    public void runShowWizard(View view) {
        startActivity(new Intent(this, WizardActivity.class));
    }


    private void runExpandableOutline(long id) {
		Intent intent = new Intent(this, OutlineActivity.class);
		intent.putExtra(OutlineActivity.NODE_ID, id);
		if (tagFilter.isActive()) {
			intent.putExtra("filter_tags", tagFilter.getSelectedTagsArray());
			intent.putExtra("filter_and_mode", tagFilter.isAndMode());
		}
		startActivity(intent);
    }

    private void runAgenda() {
        startActivity(new Intent(this, AgendasActivity.class));
    }

	private boolean runSearch() {
		return onSearchRequested();
	}

	private void showUpgradePopup() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(OrgUtils.getStringFromResource(R.raw.upgrade, this));
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
		builder.create().show();
	}

    private void showSyncFailPopup(String errorMsg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(errorMsg);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
		builder.create().show();
	}
}

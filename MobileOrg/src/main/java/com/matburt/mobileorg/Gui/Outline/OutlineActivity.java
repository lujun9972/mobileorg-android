package com.matburt.mobileorg.Gui.Outline;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Agenda.AgendasActivity;
import com.matburt.mobileorg.Gui.Wizard.WizardActivity;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.Services.RecordingService;
import com.matburt.mobileorg.Services.SyncService;
import com.matburt.mobileorg.Settings.SettingsActivity;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class OutlineActivity extends AppCompatActivity {

	public final static String NODE_ID = "node_id";
	private final static String OUTLINE_NODES = "nodes";
	private final static String OUTLINE_CHECKED_POS = "selection";
	private final static String OUTLINE_SCROLL_POS = "scrollPosition";
	private static final int REQUEST_POST_NOTIFICATIONS = 1001;

    public final static String SYNC_FAILED = "com.matburt.mobileorg.SYNC_FAILED";

	private Long node_id;

	private OutlineListView listView;

	private SynchServiceReceiver syncReceiver;
	private MenuItem synchronizerMenuItem;
	private View recordingBar;
	private BroadcastReceiver recordingReceiver;
	private long pendingRecordNodeId = -1;

	private OutlineTagFilter tagFilter = new OutlineTagFilter();
	private boolean programmaticChipChange = false;
	private Chip allFilterChip;
	private static final String STATE_FILTER_TAGS = "filter_tags";
	private static final String STATE_FILTER_AND_MODE = "filter_and_mode";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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

		this.syncReceiver = new SynchServiceReceiver();
		IntentFilter syncFilter = new IntentFilter(Synchronizer.SYNC_UPDATE);
		if (Build.VERSION.SDK_INT >= 33) {
			registerReceiver(this.syncReceiver, syncFilter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(this.syncReceiver, syncFilter);
		}

		recordingReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (RecordingService.BROADCAST_UPDATE.equals(action)) {
					long elapsed = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_SECONDS, 0);
					showOrUpdateRecordingBar(elapsed);
				} else if (RecordingService.BROADCAST_STOPPED.equals(action)) {
					removeRecordingBar();
				}
			}
		};

		IntentFilter recordingFilter = new IntentFilter(RecordingService.BROADCAST_UPDATE);
		recordingFilter.addAction(RecordingService.BROADCAST_STOPPED);
		if (Build.VERSION.SDK_INT >= 33) {
			registerReceiver(recordingReceiver, recordingFilter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(recordingReceiver, recordingFilter);
		}

		refreshDisplay();
	}

	@Override
	protected void onResume() {
		MobileOrgApplication.log("OutlineActivity.onResume()");
		super.onResume();
		refreshTitle();
		setupFilterBar();
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
		unregisterReceiver(this.syncReceiver);
		if (recordingReceiver != null) {
			unregisterReceiver(recordingReceiver);
		}
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
		if (PreferenceUtils.isSyncConfigured() == false)
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
		ArrayList<String> tags = OrgProviderUtils.getTags(getContentResolver());
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

		boolean allChecked = allFilterChip.isChecked();

		if (allChecked) {
			programmaticChipChange = true;
			tagFilter.clearAll();
			for (int i = 1; i < chipGroup.getChildCount(); i++) {
				((Chip) chipGroup.getChildAt(i)).setChecked(false);
			}
			programmaticChipChange = false;
			applyFilter();
			return;
		}

		programmaticChipChange = true;
		tagFilter.clearAll();
		for (int i = 1; i < chipGroup.getChildCount(); i++) {
			Chip chip = (Chip) chipGroup.getChildAt(i);
			if (chip.isChecked()) {
				tagFilter.setTagSelected((String) chip.getTag(), true);
			}
		}

		if (!tagFilter.isActive()) {
			allFilterChip.setChecked(true);
			programmaticChipChange = false;
			return;
		}
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
    	int changes = OrgProviderUtils.getChangesCount(getContentResolver());
    	if(changes > 0)
    		return "[" + changes + "]";
    	else
    		return "";
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.outline_menu, menu);

	    synchronizerMenuItem = menu.findItem(R.id.menu_sync);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			listView.collapseCurrent();
			return true;
		} else if (id == R.id.menu_sync) {
			runSynchronize(null);
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
				tryStartRecording(checkedNodeId);
			}
			return true;
		}
		return false;
	}

	public void runHelp(View view) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("https://github.com/matburt/mobileorg-android/wiki"));
    	startActivity(intent);
    }

    public void runSynchronize(View view) {
		if (Build.VERSION.SDK_INT >= 33 && !Compat.hasNotificationPermission(this)) {
			ActivityCompat.requestPermissions(this,
					new String[]{"android.permission.POST_NOTIFICATIONS"},
					REQUEST_POST_NOTIFICATIONS);
		}
		Intent intent = new Intent(this, SyncService.class);
		Compat.startService(this, intent);
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


	private void showOrUpdateRecordingBar(long elapsedSeconds) {
		if (recordingBar == null) {
			recordingBar = getLayoutInflater().inflate(R.layout.recording_bar, null);
			LinearLayout rootLayout = findViewById(R.id.outline_root);
			rootLayout.addView(recordingBar, 0);

			ImageButton pauseBtn = recordingBar.findViewById(R.id.recording_pause_btn);
			pauseBtn.setOnClickListener(v -> {
				RecordingService instance = RecordingService.getInstance();
				if (instance != null) {
					Intent intent = new Intent(this, RecordingService.class);
					intent.putExtra(RecordingService.ACTION_NAME,
							instance.isPaused() ? RecordingService.ACTION_RESUME : RecordingService.ACTION_PAUSE);
					startService(intent);
				}
			});

			ImageButton stopBtn = recordingBar.findViewById(R.id.recording_stop_btn);
			stopBtn.setOnClickListener(v -> {
				Intent intent = new Intent(this, RecordingService.class);
				intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_STOP);
				startService(intent);
			});
		}

		TextView elapsedView = recordingBar.findViewById(R.id.recording_elapsed);
		long minutes = elapsedSeconds / 60;
		long seconds = elapsedSeconds % 60;
		elapsedView.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));

		ImageButton pauseBtn = recordingBar.findViewById(R.id.recording_pause_btn);
		RecordingService instance = RecordingService.getInstance();
		if (instance != null && instance.isPaused()) {
			pauseBtn.setImageResource(R.drawable.ic_media_play);
		} else {
			pauseBtn.setImageResource(R.drawable.ic_media_pause);
		}
	}

	private void removeRecordingBar() {
		if (recordingBar != null) {
			LinearLayout rootLayout = findViewById(R.id.outline_root);
			rootLayout.removeView(recordingBar);
			recordingBar = null;
		}
	}

	void tryStartRecording(long nodeId) {
		if (RecordingService.isRecording()) {
			return;
		}
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
				== PackageManager.PERMISSION_GRANTED) {
			startRecordingService(nodeId);
		} else {
			pendingRecordNodeId = nodeId;
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.RECORD_AUDIO}, 0);
		}
	}

	private void startRecordingService(long nodeId) {
		Intent intent = new Intent(this, RecordingService.class);
		intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_START);
		intent.putExtra(RecordingService.NODE_ID, nodeId);
		if (Build.VERSION.SDK_INT >= 26) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (pendingRecordNodeId != -1) {
				startRecordingService(pendingRecordNodeId);
				pendingRecordNodeId = -1;
			}
		}
	}

	private class SynchServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			boolean syncStart = intent.getBooleanExtra(Synchronizer.SYNC_START, false);
			boolean syncDone = intent.getBooleanExtra(Synchronizer.SYNC_DONE, false);
			boolean showToast = intent.getBooleanExtra(Synchronizer.SYNC_SHOW_TOAST, false);
			int progress = intent.getIntExtra(Synchronizer.SYNC_PROGRESS_UPDATE, -1);

			MobileOrgApplication.log("SyncReceiver: start=" + syncStart + " done=" + syncDone + " progress=" + progress);

			if(syncStart) {
				final ImageView refreshView = new ImageView(OutlineActivity.this);
				refreshView.setImageResource(R.drawable.ic_menu_refresh);
				synchronizerMenuItem.setActionView(refreshView);
				final Animation rotate = new android.view.animation.RotateAnimation(0, 360,
						android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
						android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
				rotate.setDuration(1000);
				rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
				rotate.setInterpolator(new android.view.animation.LinearInterpolator());
				refreshView.post(new Runnable() {
					@Override
					public void run() {
						refreshView.startAnimation(rotate);
					}
				});
			} else if (syncDone) {
				android.view.View actionView = synchronizerMenuItem.getActionView();
				if (actionView != null) {
					actionView.clearAnimation();
				}
				synchronizerMenuItem.setActionView(null);
				refreshDisplay();
				setupFilterBar();

				if (showToast)
					Toast.makeText(context,
							R.string.sync_successful,
							Toast.LENGTH_SHORT).show();
			} else if (progress >= 0 && progress <= 100) {
				refreshDisplay();
			}
		}
	}
}

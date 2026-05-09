package com.matburt.mobileorg.Gui.Outline;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Agenda.AgendasActivity;
import com.matburt.mobileorg.Gui.Wizard.WizardActivity;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.Services.SyncService;
import com.matburt.mobileorg.Settings.SettingsActivity;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MobileOrgApplication.log("OutlineActivity.onCreate() start");
		try {
			OrgUtils.setTheme(this);
			MobileOrgApplication.log("OutlineActivity.onCreate() setTheme done");
			super.onCreate(savedInstanceState);
			MobileOrgApplication.log("OutlineActivity.onCreate() super.onCreate done");
			setContentView(R.layout.outline);
			MobileOrgApplication.log("OutlineActivity.onCreate() setContentView done");

			Intent intent = getIntent();
			node_id = intent.getLongExtra(NODE_ID, -1);
			MobileOrgApplication.log("OutlineActivity.onCreate() node_id=" + node_id);

			if (this.node_id == -1)
				displayNewUserDialogs();
			MobileOrgApplication.log("OutlineActivity.onCreate() displayNewUserDialogs done");
			setupList();
			MobileOrgApplication.log("OutlineActivity.onCreate() setupList done");

			this.syncReceiver = new SynchServiceReceiver();
			IntentFilter syncFilter = new IntentFilter(Synchronizer.SYNC_UPDATE);
			if (Build.VERSION.SDK_INT >= 33) {
				registerReceiver(this.syncReceiver, syncFilter, Context.RECEIVER_NOT_EXPORTED);
			} else {
				registerReceiver(this.syncReceiver, syncFilter);
			}
			MobileOrgApplication.log("OutlineActivity.onCreate() registerReceiver done");

			refreshDisplay();
			MobileOrgApplication.log("OutlineActivity.onCreate() complete");
		} catch (Exception e) {
			MobileOrgApplication.log("OutlineActivity.onCreate() FATAL: " + Log.getStackTraceString(e));
		}
	}

	@Override
	protected void onResume() {
		MobileOrgApplication.log("OutlineActivity.onResume()");
		super.onResume();
		refreshTitle();
	}

	@Override
	protected void onDestroy() {
		MobileOrgApplication.log("OutlineActivity.onDestroy()");
		unregisterReceiver(this.syncReceiver);
		super.onDestroy();
	}

	private void setupList() {
		listView = (OutlineListView) findViewById(R.id.outline_list);
		listView.setActivity(this);
		listView.setEmptyView(findViewById(R.id.outline_list_empty));
	}

	private void displayNewUserDialogs() {
		if (PreferenceUtils.isSyncConfigured() == false)
			runShowWizard(null);

		if (PreferenceUtils.isUpgradedVersion())
			showUpgradePopup();
	}

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getAction().equals(SYNC_FAILED)) {
            Bundle extrasBundle = intent.getExtras();
            String errorMsg = extrasBundle.getString("ERROR_MESSAGE");
            showSyncFailPopup(errorMsg);
        }
    }

	public void refreshDisplay() {
		this.listView.refresh();
		refreshTitle();
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

	private class SynchServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			boolean syncStart = intent.getBooleanExtra(Synchronizer.SYNC_START, false);
			boolean syncDone = intent.getBooleanExtra(Synchronizer.SYNC_DONE, false);
			boolean showToast = intent.getBooleanExtra(Synchronizer.SYNC_SHOW_TOAST, false);
			int progress = intent.getIntExtra(Synchronizer.SYNC_PROGRESS_UPDATE, -1);

			MobileOrgApplication.log("SyncReceiver: start=" + syncStart + " done=" + syncDone + " progress=" + progress);

			if(syncStart) {
				ImageView refreshView = new ImageView(OutlineActivity.this);
				refreshView.setImageResource(R.drawable.ic_menu_refresh);
				refreshView.setPadding(0, 0, 0, 0);
				Animation rotate = new android.view.animation.RotateAnimation(0, 360,
						android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
						android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
				rotate.setDuration(1000);
				rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
				rotate.setInterpolator(new android.view.animation.LinearInterpolator());
				refreshView.startAnimation(rotate);
				synchronizerMenuItem.setActionView(refreshView);
			} else if (syncDone) {
				android.view.View actionView = synchronizerMenuItem.getActionView();
				if (actionView != null) {
					actionView.clearAnimation();
				}
				synchronizerMenuItem.setActionView(null);
				refreshDisplay();

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

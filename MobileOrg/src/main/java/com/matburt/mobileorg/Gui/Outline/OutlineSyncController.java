package com.matburt.mobileorg.Gui.Outline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Services.SyncService;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.util.Compat;

/**
 * Manages sync UI concerns in OutlineActivity: the sync menu item animation,
 * the sync broadcast receiver, and sync triggering with permission checks.
 */
public class OutlineSyncController {
    private static final int REQUEST_POST_NOTIFICATIONS = 1001;

    private final AppCompatActivity activity;
    private final Runnable onRefreshDisplay;
    private final Runnable onSetupFilterBar;

    private MenuItem synchronizerMenuItem;
    private SynchServiceReceiver syncReceiver;

    public OutlineSyncController(AppCompatActivity activity,
                                  Runnable onRefreshDisplay,
                                  Runnable onSetupFilterBar) {
        this.activity = activity;
        this.onRefreshDisplay = onRefreshDisplay;
        this.onSetupFilterBar = onSetupFilterBar;
    }

    public void onCreate() {
        syncReceiver = new SynchServiceReceiver();
        IntentFilter syncFilter = new IntentFilter(Synchronizer.SYNC_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(syncReceiver, syncFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(syncReceiver, syncFilter);
        }
        Log.d("MobileOrg", "[SyncUI] SynchServiceReceiver registered, isSyncRunning=" + SyncService.isSyncRunning);
    }

    public void onDestroy() {
        Log.d("MobileOrg", "[SyncUI] onDestroy: unregistering syncReceiver, isSyncRunning=" + SyncService.isSyncRunning);
        activity.unregisterReceiver(syncReceiver);
    }

    public void onCreateOptionsMenu(Menu menu) {
        synchronizerMenuItem = menu.findItem(R.id.menu_sync);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem syncItem = menu.findItem(R.id.menu_sync);
        if (syncItem == null) return;

        View actionView = syncItem.getActionView();
        if (SyncService.isSyncRunning) {
            if (actionView == null) {
                Log.d("MobileOrg", "[SyncUI] onPrepareOptionsMenu: sync running but no actionView, restoring animation");
                startSyncAnimation(syncItem);
            }
        } else {
            if (actionView != null) {
                Log.d("MobileOrg", "[SyncUI] onPrepareOptionsMenu: sync NOT running but actionView exists, clearing animation");
                actionView.clearAnimation();
            }
            syncItem.setActionView(null);
        }
    }

    public void runSynchronize() {
        if (Build.VERSION.SDK_INT >= 33 && !Compat.hasNotificationPermission(activity)) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQUEST_POST_NOTIFICATIONS);
        }
        Intent intent = new Intent(activity, SyncService.class);
        Compat.startService(activity, intent);
    }

    private void startSyncAnimation(MenuItem item) {
        final ImageView refreshView = new ImageView(activity);
        refreshView.setImageResource(R.drawable.ic_menu_refresh);
        item.setActionView(refreshView);
        final android.view.animation.RotateAnimation rotAnim = new android.view.animation.RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotAnim.setDuration(1000);
        rotAnim.setRepeatCount(Animation.INFINITE);
        rotAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        refreshView.post(new Runnable() {
            @Override
            public void run() {
                refreshView.startAnimation(rotAnim);
                Log.d("MobileOrg", "[SyncUI] rotation animation started");
            }
        });
    }

    private void stopSyncAnimation(MenuItem item) {
        if (item == null) return;
        View actionView = item.getActionView();
        if (actionView != null) {
            actionView.clearAnimation();
        }
        item.setActionView(null);
    }

    private class SynchServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean syncStart = intent.getBooleanExtra(Synchronizer.SYNC_START, false);
            boolean syncDone = intent.getBooleanExtra(Synchronizer.SYNC_DONE, false);
            boolean showToast = intent.getBooleanExtra(Synchronizer.SYNC_SHOW_TOAST, false);
            int progress = intent.getIntExtra(Synchronizer.SYNC_PROGRESS_UPDATE, -1);

            MobileOrgApplication.log("SyncReceiver: start=" + syncStart + " done=" + syncDone + " progress=" + progress);
            Log.d("MobileOrg", "[SyncUI] onReceive: start=" + syncStart + " done=" + syncDone + " progress=" + progress);

            if (syncStart) {
                Log.d("MobileOrg", "[SyncUI] SYNC_START: starting rotation animation on synchronizerMenuItem=" + synchronizerMenuItem);
                startSyncAnimation(synchronizerMenuItem);
            } else if (syncDone) {
                stopSyncAnimation(synchronizerMenuItem);
                onRefreshDisplay.run();
                onSetupFilterBar.run();

                if (showToast)
                    Toast.makeText(context,
                            R.string.sync_successful,
                            Toast.LENGTH_SHORT).show();
            } else if (progress >= 0 && progress <= 100) {
                onRefreshDisplay.run();
            }
        }
    }
}

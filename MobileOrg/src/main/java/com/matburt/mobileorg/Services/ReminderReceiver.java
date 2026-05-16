package com.matburt.mobileorg.Services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.matburt.mobileorg.Gui.ViewActivity;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import java.util.HashSet;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MobileOrg";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Compat.hasNotificationPermission(context)) return;

        long nodeId = intent.getLongExtra("nodeId", -1);
        String dateType = intent.getStringExtra("dateType");
        String dateString = intent.getStringExtra("dateString");

        if (nodeId == -1 || dateType == null) return;

        ContentResolver resolver = context.getContentResolver();

        // Re-check: skip if node is now in done state
        try {
            OrgNode node = new OrgNode(nodeId, resolver);
            HashSet<String> activeTodos = new HashSet<>(OrgProviderUtils.getActiveTodos(resolver));
            if (node.todo != null && !activeTodos.contains(node.todo)) {
                Log.d(TAG, "ReminderReceiver: node " + nodeId + " is done, skipping");
                return;
            }

            String label = dateType.equals("deadline") ? "DEADLINE" : "SCHEDULED";
            String title = node.name != null ? node.name : "Node " + nodeId;

            Intent viewIntent = new Intent(context, ViewActivity.class);
            viewIntent.putExtra(ViewActivity.NODE_ID, nodeId);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                context, (int) nodeId, viewIntent, Compat.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Compat.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(label + ": " + dateString)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

            int notifyId = (nodeId + dateType).hashCode();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(notifyId, builder.build());

            Log.d(TAG, "ReminderReceiver: notified node " + nodeId + " " + label);
        } catch (OrgNodeNotFoundException e) {
            Log.d(TAG, "ReminderReceiver: node " + nodeId + " not found, skipping");
        }
    }
}

package com.matburt.mobileorg.Plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.matburt.mobileorg.Services.SyncService;

public final class Synchronize extends BroadcastReceiver
{
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Intent syncIntent = new Intent(context, SyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(syncIntent);
        } else {
            context.startService(syncIntent);
        }
    }
}
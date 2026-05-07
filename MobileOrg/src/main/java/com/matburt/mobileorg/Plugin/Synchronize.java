package com.matburt.mobileorg.Plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.matburt.mobileorg.Services.SyncService;
import com.matburt.mobileorg.util.Compat;

public final class Synchronize extends BroadcastReceiver
{
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Intent syncIntent = new Intent(context, SyncService.class);
        Compat.startService(context, syncIntent);
    }
}

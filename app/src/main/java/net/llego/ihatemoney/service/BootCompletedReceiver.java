package net.llego.ihatemoney.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import net.llego.ihatemoney.R;


public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean backgroundSyncEnabled = prefs.getBoolean(context.getString(R.string.pref_key_periodical_sync), false);
        boolean autoStart = prefs.getBoolean(context.getString(R.string.pref_key_autostart), true);

        if (backgroundSyncEnabled && autoStart && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SyncWorker.submitWork(context);
        }
    }
}

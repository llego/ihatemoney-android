package net.llego.ihatemoney.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import net.llego.ihatemoney.R
import net.llego.ihatemoney.android.activity.BillsListViewActivity
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper
import net.llego.ihatemoney.persistence.MoneyBusterServerSyncHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class SyncWorker(
    val context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val NOTIFICATION_ID = 414243

        private fun getIntervalMins(context: Context): Long {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val intervalStr =
                prefs.getString(context.getString(R.string.pref_key_sync_interval), "60")
            val intervalMins = intervalStr?.toLongOrNull() ?: 60
            return intervalMins
        }

        @JvmStatic
        fun submitWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest =
                PeriodicWorkRequestBuilder<SyncWorker>(getIntervalMins(context), TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, syncWorkRequest)
        }

        @JvmStatic
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }

    private val DATE_FORMAT = SimpleDateFormat("HH:mm MM-dd", Locale.ROOT)

    override fun doWork(): Result {
        val db = MoneyBusterSQLiteOpenHelper.getInstance(context)
        Log.v(TAG, "requesting sync of all projects")

        for (proj in db.projects) {
            if (proj.isLocal) {
                Log.v(TAG, "project ${proj.remoteId} is local, not syncing")
                continue
            }
            if (isStopped) {
                Log.w(TAG, "Worker stopped, breaking")
                return Result.retry()
            }

            Log.v(TAG, "requesting sync of project ${proj.remoteId}")
            val syncTask = db.moneyBusterServerSyncHelper.scheduleSync(false, proj.id)
            // await task
            // FIXME: run all tasks in parallel?
            syncTask?.get()
            Log.v(TAG, "finished sync of project ${proj.remoteId}")
        }
        showNotification(context, db)
        return Result.success()
    }

    private fun showNotification(context: Context, db: MoneyBusterSQLiteOpenHelper) {
        MoneyBusterServerSyncHelper.createNotificationChannels(context, db)

        val channelId = MoneyBusterServerSyncHelper.MAIN_CHANNEL_ID.toString()
        val lastSyncDate = DATE_FORMAT.format(Date())
        val text = String.format(
            context.getString(R.string.sync_notification_message),
            lastSyncDate,
            getIntervalMins(context)
        )

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dollar_grey_24dp)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOnlyAlertOnce(true)

        val resultIntent = Intent(context, BillsListViewActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(BillsListViewActivity::class.java)
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        builder.setContentIntent(resultPendingIntent)
        val notification = builder.build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }
}
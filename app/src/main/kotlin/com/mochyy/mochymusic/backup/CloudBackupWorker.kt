// CloudBackupWorker.kt

package com.mochyy.mochymusic.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mochyy.mochymusic.constants.CloudBackupAutoKey
import com.mochyy.mochymusic.constants.CloudBackupEveryMinutesKey
import com.mochyy.mochymusic.di.CloudBackupEntryPoint
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** the automatic backup; workmanager runs it on the chosen schedule once there's a network, even with mochy closed */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val on = prefs[CloudBackupAutoKey] ?: false
        val everyMinutes = prefs[CloudBackupEveryMinutesKey] ?: CloudBackupSchedule.DefaultEveryMinutes
        // a restore can bring other settings than the ones this run was scheduled with, so it puts the schedule right itself
        if (!on || everyMinutes != inputData.getInt(EveryMinutesInput, everyMinutes)) {
            CloudBackupSchedule.apply(applicationContext, on, everyMinutes)
            if (!on) return Result.success()
        }
        val manager = EntryPointAccessors.fromApplication(applicationContext, CloudBackupEntryPoint::class.java).cloudBackupManager()
        return when (manager.backUpOnSchedule()) {
            BackupOutcome.Retry -> if (runAttemptCount < MaxRetries) Result.retry() else Result.success()
            // signed out or nothing to do: the next run comes around on its own, after the user signs in again
            BackupOutcome.Done, BackupOutcome.Skipped, BackupOutcome.Stopped -> Result.success()
        }
    }

    internal companion object {
        const val EveryMinutesInput = "everyMinutes"
        const val MaxRetries = 3
    }
}

/** turns the backup page's switch and slider into a workmanager schedule */
object CloudBackupSchedule {
    private const val WorkName = "cloud_backup"

    const val DefaultEveryMinutes = 24 * 60
    const val DefaultKeep = 5

    // 30 minutes up to a week, the steps the "how often" slider snaps to
    val EveryMinutesSteps = listOf(30, 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60, 3 * 24 * 60, 7 * 24 * 60)
    val KeepSteps = (1..10).toList()

    /** on starts or moves the schedule (the next run keeps its place where it can), off stops it */
    fun apply(context: Context, on: Boolean, everyMinutes: Int) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WorkName)
            return
        }
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(everyMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(CloudBackupWorker.EveryMinutesInput to everyMinutes))
            .build()
        work.enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

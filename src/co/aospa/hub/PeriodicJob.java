package co.aospa.hub;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.util.Log;

import java.util.Objects;

public class PeriodicJob extends JobService {
    private static final String TAG = "PeriodicJob";
    private static final int JOB_ID_PERIODIC = 1;
    private static final int JOB_ID_RETRY = 2;
    private static final long INTERVAL_MILLIS = 4 * 60 * 60 * 1000;
    private static final long MIN_LATENCY_MILLIS = 4 * 60 * 1000;

    static void schedule(final Context context, final boolean force) {
        final JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        final JobInfo jobInfo = scheduler.getPendingJob(JOB_ID_PERIODIC);
        if (!force && jobInfo != null &&
                jobInfo.getNetworkType() == JobInfo.NETWORK_TYPE_ANY &&
                jobInfo.isRequireBatteryNotLow() == true &&
                jobInfo.isPersisted() &&
                jobInfo.getIntervalMillis() == INTERVAL_MILLIS) {
            Log.d(TAG, "Periodic job already registered");
            return;
        }
        final ComponentName serviceName = new ComponentName(context, PeriodicJob.class);
        final int result = scheduler.schedule(new JobInfo.Builder(JOB_ID_PERIODIC, serviceName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .setPeriodic(INTERVAL_MILLIS)
            .build());
        if (result == JobScheduler.RESULT_FAILURE) {
            Log.d(TAG, "Periodic job schedule failed");
        }
    }

    static void schedule(final Context context) {
        schedule(context, false);
    }

    static void scheduleRetry(final Context context) {
        final JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        final ComponentName serviceName = new ComponentName(context, PeriodicJob.class);
        final int result = scheduler.schedule(new JobInfo.Builder(JOB_ID_RETRY, serviceName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setMinimumLatency(MIN_LATENCY_MILLIS)
            .build());
        if (result == JobScheduler.RESULT_FAILURE) {
            Log.d(TAG, "Retry job schedule failed");
        }
    }

    static void cancel(final Context context) {
        final JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        scheduler.cancel(JOB_ID_PERIODIC);
        scheduler.cancel(JOB_ID_RETRY);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "onStartJob id: " + params.getJobId());
        startForegroundService(new Intent(this, Service.class));
        return false;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        return false;
    }
}

package org.whispersystems.signalservice.api.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.whispersystems.signalservice.api.util.SleepTimer;

import java.util.concurrent.TimeUnit;

/**
 * A sleep timer that is based on elapsed realtime, so
 * that it works properly, even in low-power sleep modes.
 *
 */
public class RealtimeSleepTimer implements SleepTimer {
  private static final String TAG = RealtimeSleepTimer.class.getSimpleName();

  private final AlarmReceiver alarmReceiver;
  private final Context context;
  private boolean armed;

  public RealtimeSleepTimer(Context context) {
    this.context = context;
    alarmReceiver = new RealtimeSleepTimer.AlarmReceiver();
  }

  @Override
  public void sleep(long millis) throws InterruptedException {
    boolean arm;

    synchronized (this) {
      if (!armed) {
        armed = true;
        arm = true;
      } else {
        arm = false;
      }
    }

    if (arm) {
      context.registerReceiver(alarmReceiver,
                               new IntentFilter(AlarmReceiver.WAKE_UP_THREAD_ACTION));

      alarmReceiver.setAlarm(millis);
      synchronized (this) {
          wait(millis);
      }
    } else {
      synchronized (this) {
          wait();
      }
    }
  }

  private class AlarmReceiver extends BroadcastReceiver {
    private static final String WAKE_UP_THREAD_ACTION = "org.whispersystems.signalservice.api.util.RealtimeSleepTimer.AlarmReceiver.WAKE_UP_THREAD";

    private void setAlarm(long millis) {
      final Intent        intent        = new Intent(WAKE_UP_THREAD_ACTION);
      final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
      final AlarmManager  alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

      Log.w(TAG, "Setting alarm to wake up in " + millis + "ms.");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                               SystemClock.elapsedRealtime() + millis,
                                               pendingIntent);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                              SystemClock.elapsedRealtime() + millis,
                              pendingIntent);
      } else {
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                         SystemClock.elapsedRealtime() + millis,
                         pendingIntent);
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "Waking up.");

      synchronized (RealtimeSleepTimer.this) {
        RealtimeSleepTimer.this.context.unregisterReceiver(this);

        RealtimeSleepTimer.this.armed = false;
        RealtimeSleepTimer.this.notifyAll();
      }
    }
  }
}


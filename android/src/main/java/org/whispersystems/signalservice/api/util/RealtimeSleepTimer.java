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

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * A sleep timer that is based on elapsed realtime, so
 * that it works properly, even in low-power sleep modes.
 *
 */
public class RealtimeSleepTimer implements SleepTimer {

  private static final String TAG = RealtimeSleepTimer.class.getSimpleName();

  static {
    Log.w(TAG, "theBoatman: fixed parallel sleep");
  }


  private static ConcurrentSkipListSet<Integer> actionIdList = new ConcurrentSkipListSet<>();

  private final Context context;

  public RealtimeSleepTimer(Context context) {
    this.context = context;
  }

  @Override
  public void sleep(long millis) {
    final AlarmReceiver alarmReceiver = new RealtimeSleepTimer.AlarmReceiver();
    int actionId = 0;
    while (!actionIdList.add(actionId)){
      actionId++;
    }
    Log.w(TAG, "Sleep Action ID: "+ actionId +". Thread: " + Thread.currentThread().getName());
    try {
      context.registerReceiver(alarmReceiver,
              new IntentFilter(AlarmReceiver.WAKE_UP_THREAD_ACTION + "." + actionId));

      final long startTime = System.currentTimeMillis();
      alarmReceiver.setAlarm(millis, AlarmReceiver.WAKE_UP_THREAD_ACTION + "." + actionId);

      while (System.currentTimeMillis() - startTime < millis) {
        try {
          synchronized (this) {
            wait(millis - System.currentTimeMillis() + startTime);
          }
        } catch (InterruptedException e) {
          Log.w(TAG, e);
        }
      }
      context.unregisterReceiver(alarmReceiver);
    } catch(Exception e) {
      Log.w(TAG, "Exception during sleep ...",e);
    }finally {
      actionIdList.remove(actionId);
    }
  }

  private class AlarmReceiver extends BroadcastReceiver {
    private static final String WAKE_UP_THREAD_ACTION = "org.whispersystems.signalservice.api.util.RealtimeSleepTimer.AlarmReceiver.WAKE_UP_THREAD";

    private void setAlarm(long millis, String LABEL) {
      final Intent        intent        = new Intent(LABEL);
      final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
      final AlarmManager  alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

      Log.w(TAG, "Setting alarm to wake up in " + millis + "ms. Thread: " + Thread.currentThread().getName());

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
      Log.w(TAG, "Waking up. Thread: " + Thread.currentThread().getName());

      synchronized (RealtimeSleepTimer.this) {
        RealtimeSleepTimer.this.notifyAll();
      }
    }
  }
}


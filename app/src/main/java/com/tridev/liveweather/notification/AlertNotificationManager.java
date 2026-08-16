package com.tridev.liveweather.notification;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.tridev.liveweather.MainActivity;
import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.AlertPreferences;
import com.tridev.liveweather.domain.alert.WeatherAlert;

import java.util.List;

public final class AlertNotificationManager {

    private static final String CHANNEL_OFFICIAL = "official_weather_alerts";
    private static final String CHANNEL_SMART = "smart_weather_risks";

    private final Context context;
    private final NotificationManager manager;
    private final AlertPreferences preferences;

    public AlertNotificationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        manager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        preferences = new AlertPreferences(this.context);
        createChannels();
    }

    public void createChannels() {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel official = new NotificationChannel(
                CHANNEL_OFFICIAL,
                "Official weather alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        official.setDescription("Official IMD CAP weather warnings for the active location.");
        official.enableVibration(true);

        NotificationChannel smart = new NotificationChannel(
                CHANNEL_SMART,
                "Smart weather risks",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        smart.setDescription("High-confidence weather risks derived from the app forecast engine.");

        manager.createNotificationChannel(official);
        manager.createNotificationChannel(smart);
    }

    public void notifyNewAlerts(@NonNull List<WeatherAlert> alerts) {
        if (!preferences.isNotificationsEnabled() || manager == null || !canPostNotifications()) {
            return;
        }

        int sent = 0;
        for (WeatherAlert alert : alerts) {
            if (alert == null || !preferences.shouldNotify(alert) || !preferences.markIfNew(alert)) {
                continue;
            }
            post(alert);
            sent++;
            if (sent >= 3) break;
        }
    }

    private void post(@NonNull WeatherAlert alert) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_weather_alerts", true);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                Math.abs(alert.fingerprint().hashCode()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channel = alert.isOfficial() ? CHANNEL_OFFICIAL : CHANNEL_SMART;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(alert.getTitle())
                .setContentText(alert.getMessage())
                .setStyle(new Notification.BigTextStyle().bigText(alert.getMessage()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setWhen(alert.getIssuedAt() > 0L ? alert.getIssuedAt() : System.currentTimeMillis())
                .setShowWhen(true);

        if (alert.getSeverity() == WeatherAlert.Severity.RED) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        manager.notify(Math.abs(alert.fingerprint().hashCode()), builder.build());
    }

    public boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}

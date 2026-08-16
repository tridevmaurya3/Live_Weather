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

    public static final String EXTRA_OPEN_WEATHER_ALERTS = "open_weather_alerts";
    public static final String EXTRA_ALERT_FINGERPRINT = "weather_alert_fingerprint";

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
        smart.setDescription("High-confidence app-derived weather risk signals. Not official warnings.");

        manager.createNotificationChannel(official);
        manager.createNotificationChannel(smart);
    }

    public void notifyNewAlerts(@NonNull List<WeatherAlert> alerts) {
        if (!preferences.isNotificationsEnabled() || manager == null || !canPostNotifications()) {
            return;
        }

        int sent = 0;
        for (WeatherAlert alert : alerts) {
            if (alert == null || !preferences.shouldNotify(alert)) continue;
            if (!isChannelEnabledFor(alert) || !preferences.markIfNew(alert)) continue;
            post(alert);
            sent++;
            if (sent >= 3) break;
        }
    }

    private void post(@NonNull WeatherAlert alert) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_WEATHER_ALERTS, true)
                .putExtra(EXTRA_ALERT_FINGERPRINT, alert.fingerprint());
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                alert.fingerprint().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channel = alert.isOfficial() ? CHANNEL_OFFICIAL : CHANNEL_SMART;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);

        String sourcePrefix = alert.isOfficial() ? "IMD OFFICIAL" : "SMART RISK";
        String title = sourcePrefix + " · " + severityLabel(alert.getSeverity());
        String body = alert.getTitle() + " — " + alert.getMessage();

        builder.setSmallIcon(R.drawable.ic_nav_home)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(alert.getSeverity() == WeatherAlert.Severity.RED
                        ? Notification.CATEGORY_ALARM
                        : Notification.CATEGORY_STATUS)
                .setWhen(alert.getIssuedAt() > 0L ? alert.getIssuedAt() : System.currentTimeMillis())
                .setShowWhen(true);

        if (alert.getSeverity() == WeatherAlert.Severity.RED) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        manager.notify(alert.fingerprint().hashCode(), builder.build());
    }

    public boolean canPostNotifications() {
        if (manager == null) return false;
        boolean permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return permission && manager.areNotificationsEnabled();
    }

    public boolean isOfficialChannelEnabled() {
        return isChannelEnabled(CHANNEL_OFFICIAL);
    }

    public boolean isSmartChannelEnabled() {
        return isChannelEnabled(CHANNEL_SMART);
    }

    public boolean isChannelEnabledFor(@NonNull WeatherAlert alert) {
        return alert.isOfficial() ? isOfficialChannelEnabled() : isSmartChannelEnabled();
    }

    private boolean isChannelEnabled(@NonNull String channelId) {
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    @NonNull
    private String severityLabel(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case RED:
                return "WARNING";
            case ORANGE:
                return "ALERT";
            case YELLOW:
                return "WATCH";
            default:
                return "INFO";
        }
    }
}
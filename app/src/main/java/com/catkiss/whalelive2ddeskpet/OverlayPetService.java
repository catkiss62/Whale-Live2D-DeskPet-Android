package com.catkiss.whalelive2ddeskpet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public final class OverlayPetService extends Service {
    static final String ACTION_SHOW = "com.catkiss.whale.SHOW";
    static final String ACTION_STOP = "com.catkiss.whale.STOP";
    static final String ACTION_REACTION = "com.catkiss.whale.REACTION";
    static final String EXTRA_REACTION = "reaction_id";
    private static final String CHANNEL_ID = "whale_live2d_pet";
    private static final int NOTIFICATION_ID = 120;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private WhaleView whaleView;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureOverlay();
        if (ACTION_REACTION.equals(action) && whaleView != null) {
            String reaction = intent.getStringExtra(EXTRA_REACTION);
            if ("__reset__".equals(reaction)) whaleView.resetAll();
            else whaleView.applyReaction(reaction);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (whaleView != null) {
            whaleView.onHostPause();
            whaleView.release();
            if (windowManager != null) {
                try {
                    windowManager.removeView(whaleView);
                } catch (RuntimeException ignored) {
                    // The system may already have detached the overlay.
                }
            }
            whaleView = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureOverlay() {
        if (whaleView != null) return;
        File modelFile = WhaleModelImporter.findImportedModel(this);
        if (modelFile == null) {
            stopSelf();
            return;
        }
        try {
            WhaleCatalog catalog = WhaleCatalog.scan(modelFile);
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            whaleView = new WhaleView(this);
            int width = dp(320);
            int height = dp(430);
            layoutParams = new WindowManager.LayoutParams(
                    width, height,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            layoutParams.gravity = Gravity.BOTTOM | Gravity.END;
            layoutParams.x = dp(12);
            layoutParams.y = dp(42);
            installDragGesture();
            windowManager.addView(whaleView, layoutParams);
            whaleView.loadModel(catalog);
            whaleView.onHostResume();
        } catch (Exception error) {
            stopSelf();
        }
    }

    private void installDragGesture() {
        whaleView.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downRawX;
            float downRawY;
            int startX;
            int startY;
            float travel;

            @Override
            public boolean onTouch(android.view.View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = layoutParams.x;
                        startY = layoutParams.y;
                        travel = 0.0f;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        travel = Math.max(travel, Math.abs(dx) + Math.abs(dy));
                        layoutParams.x = startX - Math.round(dx);
                        layoutParams.y = startY - Math.round(dy);
                        windowManager.updateViewLayout(whaleView, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (travel < dp(10)) whaleView.applyReaction("playful");
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, OverlayPetService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Q版鲸鱼 Live2D 桌宠")
                .setContentText("点击桌宠会触发调皮反应；可拖动位置")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopIntent)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Q版鲸鱼桌宠", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持用户主动开启的 Live2D 桌宠悬浮层运行");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static void show(Context context) {
        Intent intent = new Intent(context, OverlayPetService.class).setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, OverlayPetService.class));
    }

    static void react(Context context, String id) {
        Intent intent = new Intent(context, OverlayPetService.class)
                .setAction(ACTION_REACTION)
                .putExtra(EXTRA_REACTION, id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }
}

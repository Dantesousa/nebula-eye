package com.nebula.eye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** Foreground service holding the MediaProjection + VirtualDisplay. */
public class CaptureService extends Service {
    public static final String CH = "eye";
    public static final int NID = 38177;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Nebula Eye",
                    NotificationManager.IMPORTANCE_LOW);
            try { nm.createNotificationChannel(ch); } catch (Exception ignored) {}
        }
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("Nebula Eye ativo")
                .setContentText("captura em http://127.0.0.1:" + EyeState.PORT)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NID, n,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NID, n);
            }
        } catch (Exception e) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (EyeState.projection == null && EyeState.pendingData != null) {
                MediaProjectionManager mpm = (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);
                MediaProjection mp = mpm.getMediaProjection(
                        EyeState.pendingResultCode, EyeState.pendingData);
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                DisplayMetrics dm = new DisplayMetrics();
                if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
                int w = dm.widthPixels > 0 ? dm.widthPixels : 1080;
                int h = dm.heightPixels > 0 ? dm.heightPixels : 2316;
                EyeState.width = w; EyeState.height = h; EyeState.density = dm.densityDpi;
                ImageReader reader = ImageReader.newInstance(w, h,
                        android.graphics.PixelFormat.RGBA_8888, 2);
                android.hardware.display.VirtualDisplay vd = mp.createVirtualDisplay(
                        "nebula-eye", w, h, dm.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);
                EyeState.projection = mp;
                EyeState.reader = reader;
                EyeState.display = vd;
                mp.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { teardown(); }
                }, null);
            }
        } catch (SecurityException se) {
            EyeState.lastError = "proj negada: " + se.getMessage();
            stopSelf();
        } catch (Exception e) {
            EyeState.lastError = "capture: " + e;
            stopSelf();
        }
        return START_STICKY;
    }

    public static void teardown() {
        try { if (EyeState.display != null) EyeState.display.release(); } catch (Exception ignored) {}
        try { if (EyeState.reader != null) EyeState.reader.close(); } catch (Exception ignored) {}
        try { if (EyeState.projection != null) EyeState.projection.stop(); } catch (Exception ignored) {}
        EyeState.display = null; EyeState.reader = null; EyeState.projection = null;
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

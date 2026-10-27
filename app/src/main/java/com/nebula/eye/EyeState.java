package com.nebula.eye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import android.graphics.Bitmap;

/** Holds shared state + screenshot capture. No external deps. */
public class EyeState {
    public static final int PORT = 38177;
    public static volatile String lastError = "";

    public static volatile int pendingResultCode = 0;
    public static volatile Intent pendingData = null;

    public static volatile MediaProjection projection = null;
    public static volatile ImageReader reader = null;
    public static volatile VirtualDisplay display = null;
    public static volatile int width = 0, height = 0, density = 1;

    public static volatile EyeService a11y = null;
    public static volatile EyeServer server = null;

    public static boolean hasProjection() { return projection != null && reader != null; }
    public static boolean hasA11y() { return a11y != null; }
    public static boolean serverOn() { return server != null && server.isAlive(); }

    /** Grab latest frame and encode JPEG. Returns null on failure. */
    public static synchronized byte[] screenshot() {
        ImageReader r = reader;
        if (r == null) return null;
        Image img = null;
        try {
            for (int i = 0; i < 25; i++) {
                try { img = r.acquireLatestImage(); } catch (Exception e) { img = null; }
                if (img != null) break;
                try { Thread.sleep(60); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            if (img == null) return null;
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buf = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            int w = img.getWidth(), h = img.getHeight();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            // copy row by row honoring stride
            byte[] row = new byte[rowStride];
            int[] pixels = new int[w];
            for (int y = 0; y < h; y++) {
                buf.position(y * rowStride);
                buf.get(row, 0, Math.min(row.length, rowStride));
                for (int x = 0; x < w; x++) {
                    int o = x * pixelStride;
                    int b = row[o] & 0xff, g = row[o + 1] & 0xff,
                        rr = row[o + 2] & 0xff, a = row[o + 3] & 0xff;
                    pixels[x] = (a << 24) | (rr << 16) | (g << 8) | b;
                }
                bmp.setPixels(pixels, 0, w, 0, y, w, 1);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(w * h);
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
            bmp.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (img != null) { try { img.close(); } catch (Exception ignored) {} }
        }
    }
}

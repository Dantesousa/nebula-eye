package com.nebula.eye;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One screen: grant projection, open a11y settings, toggle server, show status. */
public class MainActivity extends Activity {
    private static final int RC_PROJECTION = 38177;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Nebula Eye 0.1.3");
        title.setTextSize(22);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("olho + mão no localhost :38177\n1) permitir captura  2) ativar acessibilidade  3) ligar servidor");
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(14);
        root.addView(status);

        Button b1 = new Button(this);
        b1.setText("1. Permitir captura de tela");
        b1.setOnClickListener(v -> requestProjection());
        root.addView(b1);

        Button b2 = new Button(this);
        b2.setText("2. Ativar acessibilidade (Nebula Eye)");
        b2.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {}
        });
        root.addView(b2);

        Button b3 = new Button(this);
        b3.setText("3. Ligar / reiniciar servidor :38177");
        b3.setOnClickListener(v -> startEye());
        root.addView(b3);

        Button bx = new Button(this);
        bx.setText("4. Bateria: sem restrições");
        bx.setOnClickListener(v -> {
            try {
                Intent i = new Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:com.nebula.eye"));
                startActivity(i);
            } catch (Exception e) {
                if (status != null) status.setText("bateria: " + e);
            }
        });
        root.addView(bx);

        Button b4 = new Button(this);
        b4.setText("Parar servidor");
        b4.setOnClickListener(v -> {
            try { if (EyeState.server != null) { EyeState.server.halt(); EyeState.server = null; } } catch (Exception ignored) {}
            refresh();
        });
        root.addView(b4);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
            }
        }
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String s = "projection: " + (EyeState.hasProjection() ? "ON " + EyeState.width + "x" + EyeState.height : "off")
                + "\na11y: " + (EyeState.hasA11y() ? "ON" : "off")
                + "\nserver: " + (EyeState.serverOn() ? "ON http://127.0.0.1:" + EyeState.PORT : "off")
                + "\n\nendpoints:\n/state /screenshot /nodes\nPOST /tap /swipe /text";
        String err = EyeState.lastError;
        if (err != null && !err.isEmpty()) s += "\n\nerro: " + err;
        if (status != null) status.setText(s);
    }

    private void requestProjection() {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), RC_PROJECTION);
        } catch (Exception e) {
            if (status != null) status.setText("falha ao pedir captura: " + e);
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_PROJECTION && res == RESULT_OK && data != null) {
            EyeState.pendingResultCode = res;
            EyeState.pendingData = data;
            startEye();
        } else if (rc == RC_PROJECTION) {
            if (status != null) status.setText("captura negada. tente de novo.");
        }
    }

    private void startEye() {
        try {
            Intent i = new Intent(this, CaptureService.class);
            startForegroundService(i);
        } catch (Exception e) {
            if (status != null) status.setText("falha no servo: " + e);
        }
        if (EyeState.server == null || !EyeState.server.isAlive()) {
            EyeState.server = new EyeServer();
            EyeState.server.start();
        }
        status.postDelayed(this::refresh, 800);
    }
}

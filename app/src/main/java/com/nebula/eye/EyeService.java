package com.nebula.eye;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Accessibility side: node dump + gestures + text input. */
public class EyeService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        EyeState.a11y = this;
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (EyeState.a11y == this) EyeState.a11y = null;
        return super.onUnbind(intent);
    }

    // ---------- nodes ----------

    private static void esc(StringBuilder sb, String s) {
        if (s == null) { sb.append("null"); return; }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static int nodeCount = 0;

    private static void walk(AccessibilityNodeInfo n, StringBuilder sb, boolean[] first) {
        if (n == null || nodeCount > 800) return;
        nodeCount++;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        CharSequence t = n.getText(), d = n.getContentDescription();
        CharSequence id = null;
        try { id = n.getViewIdResourceName(); } catch (Exception ignored) {}
        boolean interesting = n.isClickable() || n.isEditable() || n.isFocusable()
                || t != null || d != null || id != null;
        List<AccessibilityNodeInfo> kids = new ArrayList<>();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = null;
            try { c = n.getChild(i); } catch (Exception ignored) {}
            if (c != null) kids.add(c);
        }
        if (interesting) {
            if (!first[0]) sb.append(',');
            first[0] = false;
            sb.append("{\"cls\":\"").append(n.getClassName()).append('"');
            sb.append(",\"b\":[").append(b.left).append(',').append(b.top)
              .append(',').append(b.right).append(',').append(b.bottom).append(']');
            sb.append(",\"c\":").append(n.isClickable());
            sb.append(",\"e\":").append(n.isEditable());
            sb.append(",\"f\":").append(n.isFocused());
            sb.append(",\"t\":"); esc(sb, t == null ? null : t.toString());
            sb.append(",\"d\":"); esc(sb, d == null ? null : d.toString());
            sb.append(",\"id\":"); esc(sb, id == null ? null : id.toString());
            sb.append('}');
        }
        for (AccessibilityNodeInfo c : kids) {
            try { walk(c, sb, first); } finally { try { c.recycle(); } catch (Exception ignored) {} }
        }
    }

    public static String dumpJson() {
        EyeService s = EyeState.a11y;
        if (s == null) return "{\"ok\":false,\"err\":\"a11y off\"}";
        AccessibilityNodeInfo root = null;
        try { root = s.getRootInActiveWindow(); } catch (Exception e) {
            return "{\"ok\":false,\"err\":\"no root\"}";
        }
        if (root == null) return "{\"ok\":false,\"err\":\"no root\"}";
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"nodes\":[");
        nodeCount = 0;
        boolean[] first = {true};
        try { walk(root, sb, first); } catch (Exception e) {
            return "{\"ok\":false,\"err\":\"walk fail\"}";
        } finally { try { root.recycle(); } catch (Exception ignored) {} }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- gestures ----------

    private static boolean gesture(Path p, long ms) {
        EyeService s = EyeState.a11y;
        if (s == null) return false;
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, ms))
                .build();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean res = new AtomicBoolean(false);
        boolean dispatched;
        try {
            dispatched = s.dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { res.set(true); latch.countDown(); }
                @Override public void onCancelled(GestureDescription d) { latch.countDown(); }
            }, null);
        } catch (Exception e) { return false; }
        if (!dispatched) return false;
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return res.get();
    }

    public static boolean tap(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        return gesture(p, 60);
    }

    public static boolean swipe(int x1, int y1, int x2, int y2, int ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return gesture(p, Math.max(80, Math.min(2000, ms)));
    }

    public static boolean typeText(String text) {
        EyeService s = EyeState.a11y;
        if (s == null || text == null) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = findEditableFocused(root);
            if (target == null) target = findEditable(root);
            if (target == null) { try { root.recycle(); } catch (Exception ignored) {} return false; }
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            try { target.recycle(); } catch (Exception ignored) {}
            try { root.recycle(); } catch (Exception ignored) {}
            return ok;
        } catch (Exception e) { return false; }
    }

    private static AccessibilityNodeInfo findEditableFocused(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && (n.isFocused() || n.isAccessibilityFocused())) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = null;
            try { c = n.getChild(i); } catch (Exception ignored) { continue; }
            AccessibilityNodeInfo f = findEditableFocused(c);
            if (f != null) { if (f != c) { try { c.recycle(); } catch (Exception ignored) {} } return f; }
            try { c.recycle(); } catch (Exception ignored) {}
        }
        return null;
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = null;
            try { c = n.getChild(i); } catch (Exception ignored) { continue; }
            AccessibilityNodeInfo f = findEditable(c);
            if (f != null) { if (f != c) { try { c.recycle(); } catch (Exception ignored) {} } return f; }
            try { c.recycle(); } catch (Exception ignored) {}
        }
        return null;
    }
}

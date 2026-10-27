package com.nebula.eye;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Tiny localhost HTTP server (no deps). Serves eye endpoints on 127.0.0.1:PORT. */
public class EyeServer extends Thread {
    private volatile ServerSocket ss;
    private volatile boolean running = false;

    @Override
    public void run() {
        try {
            ss = new ServerSocket(EyeState.PORT, 8,
                    java.net.InetAddress.getByName("127.0.0.1"));
            running = true;
            while (running) {
                try {
                    Socket c = ss.accept();
                    handle(c);
                } catch (Exception e) {
                    if (!running) break;
                }
            }
        } catch (Exception ignored) {}
        running = false;
    }

    public void halt() {
        running = false;
        try { if (ss != null) ss.close(); } catch (Exception ignored) {}
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(256);
        int c, prev = -1;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            prev = c;
            if (b.size() > 8192) break;
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private void handle(Socket c) {
        try {
            c.setSoTimeout(10000);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            String req = readLine(in);
            if (req == null || req.isEmpty()) { c.close(); return; }
            String[] parts = req.split(" ");
            if (parts.length < 2) { c.close(); return; }
            String method = parts[0], path = parts[1];
            int q = path.indexOf('?');
            String query = "";
            if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int ci = line.indexOf(':');
                if (ci > 0) headers.put(line.substring(0, ci).trim().toLowerCase(),
                        line.substring(ci + 1).trim());
            }
            String body = "";
            if ("POST".equalsIgnoreCase(method)) {
                int len = 0;
                try { len = Integer.parseInt(headers.getOrDefault("content-length", "0")); } catch (Exception ignored) {}
                len = Math.max(0, Math.min(len, 65536));
                if (len > 0) {
                    byte[] buf = new byte[len];
                    int off = 0;
                    while (off < len) {
                        int r = in.read(buf, off, len - off);
                        if (r < 0) break;
                        off += r;
                    }
                    body = new String(buf, 0, off, StandardCharsets.UTF_8);
                }
            }

            route(method, path, query, body, out);
            out.flush();
            c.close();
        } catch (Exception e) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    private static Map<String, String> params(String query, String body) {
        Map<String, String> m = new HashMap<>();
        for (String src : new String[]{query, body}) {
            if (src == null) continue;
            for (String kv : src.split("[&;]")) {
                int e = kv.indexOf('=');
                if (e <= 0) continue;
                try {
                    String k = java.net.URLDecoder.decode(kv.substring(0, e), "UTF-8");
                    String v = java.net.URLDecoder.decode(kv.substring(e + 1), "UTF-8");
                    // tiny JSON fallback: {"x":1,"y":2}
                    m.put(k.trim().replaceAll("[\"{} ]", ""), v.trim().replaceAll("[\"{} ]", ""));
                } catch (Exception ignored) {}
            }
        }
        // raw JSON body fallback
        if (body != null && body.trim().startsWith("{")) {
            for (String kv : body.trim().replaceAll("[{}\"]", "").split(",")) {
                int e = kv.indexOf(':');
                if (e > 0) m.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
            }
        }
        return m;
    }

    private static int pint(Map<String, String> m, String k, int def) {
        try { return Integer.parseInt(m.get(k)); } catch (Exception e) { return def; }
    }

    private void route(String method, String path, String query, String body, OutputStream out) throws Exception {
        if ("/".equals(path)) path = "/state";
        if ("GET".equalsIgnoreCase(method) && "/state".equals(path)) {
            json(out, 200, "{\"ok\":true,\"projection\":" + EyeState.hasProjection()
                    + ",\"a11y\":" + EyeState.hasA11y()
                    + ",\"w\":" + EyeState.width + ",\"h\":" + EyeState.height + "}");
            return;
        }
        if ("GET".equalsIgnoreCase(method) && "/nodes".equals(path)) {
            json(out, 200, EyeService.dumpJson());
            return;
        }
        if ("GET".equalsIgnoreCase(method) && "/screenshot".equals(path)) {
            byte[] jpg = EyeState.screenshot();
            if (jpg == null) { json(out, 503, "{\"ok\":false,\"err\":\"no frame\"}"); return; }
            head(out, 200, "image/jpeg", jpg.length);
            out.write(jpg);
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/tap".equals(path)) {
            Map<String, String> p = params(query, body);
            boolean ok = EyeService.tap(pint(p, "x", -1), pint(p, "y", -1));
            json(out, ok ? 200 : 503, "{\"ok\":" + ok + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/swipe".equals(path)) {
            Map<String, String> p = params(query, body);
            boolean ok = EyeService.swipe(pint(p, "x1", -1), pint(p, "y1", -1),
                    pint(p, "x2", -1), pint(p, "y2", -1), pint(p, "ms", 400));
            json(out, ok ? 200 : 503, "{\"ok\":" + ok + "}");
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/text".equals(path)) {
            Map<String, String> p = params(query, body);
            String t = p.get("text");
            if (t == null && body != null && !body.trim().startsWith("{")) t = body;
            boolean ok = EyeService.typeText(t == null ? "" : t);
            json(out, ok ? 200 : 503, "{\"ok\":" + ok + "}");
            return;
        }
        json(out, 404, "{\"ok\":false,\"err\":\"unknown\"}");
    }

    private static void json(OutputStream out, int code, String s) throws Exception {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        head(out, code, "application/json", b.length);
        out.write(b);
    }

    private static void head(OutputStream out, int code, String ct, int len) throws Exception {
        String h = "HTTP/1.1 " + code + (code == 200 ? " OK" : " ERR") + "\r\n"
                + "Content-Type: " + ct + "\r\n"
                + "Content-Length: " + len + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
    }
}

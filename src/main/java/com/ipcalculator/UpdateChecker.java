package com.ipcalculator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {

    private static final String REPO_OWNER = "Moore-Ivan";
    private static final String REPO_NAME = "IPCalculator";
    private static final String API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    private static final String SETTING_LAST_CHECK = "update.lastCheckEpoch";
    private static final String SETTING_AUTO_CHECK = "update.autoCheck";
    private static final long AUTO_CHECK_INTERVAL_SECONDS = 24 * 60 * 60L;

    private static final String USER_AGENT =
            "IPCalculator-Updater/" + VersionInfo.getVersion() + " (+" + RELEASES_PAGE_URL + ")";

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-checker");
        t.setDaemon(true);
        return t;
    });

    private UpdateChecker() {
    }

    public static final class UpdateInfo {
        public final String version;
        public final String releaseDate;
        public final String downloadUrl;
        public final String releaseNotes;
        public final String htmlUrl;

        UpdateInfo(String version, String releaseDate, String downloadUrl,
                   String releaseNotes, String htmlUrl) {
            this.version = version;
            this.releaseDate = releaseDate;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
            this.htmlUrl = htmlUrl;
        }
    }

    public static final class CheckResult {
        public final UpdateInfo update;
        public final String currentVersion;
        public final String latestVersion;
        public final String errorMessage;

        private CheckResult(UpdateInfo update, String currentVersion,
                            String latestVersion, String errorMessage) {
            this.update = update;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.errorMessage = errorMessage;
        }

        public boolean hasUpdate() {
            return update != null;
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        static CheckResult ok(UpdateInfo update, String current, String latest) {
            return new CheckResult(update, current, latest, null);
        }

        static CheckResult error(String current, String message) {
            return new CheckResult(null, current, null, message);
        }
    }

    public static boolean shouldAutoCheck() {
        if (!"true".equalsIgnoreCase(ConfigStore.getSetting(SETTING_AUTO_CHECK, "true"))) {
            return false;
        }
        long last;
        try {
            last = Long.parseLong(ConfigStore.getSetting(SETTING_LAST_CHECK, "0"));
        } catch (NumberFormatException e) {
            last = 0;
        }
        long now = System.currentTimeMillis() / 1000L;
        return (now - last) >= AUTO_CHECK_INTERVAL_SECONDS;
    }

    public static void checkAsync(Window owner, boolean manual) {
        CompletableFuture.supplyAsync(UpdateChecker::checkSync, POOL)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> handleResult(owner, result, manual)))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (manual) {
                            showError(owner, "检查更新时发生意外错误: " + ex.getMessage());
                        } else {
                            System.err.println("UpdateChecker: 自动检查异常 - " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    public static CheckResult checkSync() {
        String current = VersionInfo.getVersion();
        HttpURLConnection conn = null;
        try {
            conn = openConnection(API_URL);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            ConfigStore.setSetting(SETTING_LAST_CHECK, String.valueOf(System.currentTimeMillis() / 1000L));

            int code = conn.getResponseCode();
            if (code == 404) {
                return CheckResult.ok(null, current, current);
            }
            if (code != 200) {
                return CheckResult.error(current, "GitHub 接口返回状态码 " + code
                        + (code == 403 ? " (可能触发了速率限制，请稍后再试)" : ""));
            }

            String body = readResponse(conn);
            UpdateInfo info = parseRelease(body);
            if (info == null || info.downloadUrl == null) {
                return CheckResult.error(current, "未在最新 Release 中找到 .exe 安装包");
            }
            boolean newer = isNewer(info.version, current);
            return CheckResult.ok(newer ? info : null, current, info.version);
        } catch (Exception e) {
            return CheckResult.error(current, "网络请求失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void handleResult(Window owner, CheckResult result, boolean manual) {
        if (result.hasError()) {
            if (manual) {
                showError(owner, result.errorMessage);
            } else {
                System.err.println("UpdateChecker: 自动检查失败 - " + result.errorMessage);
            }
            return;
        }
        if (result.hasUpdate()) {
            showUpdateDialog(owner, result.update, result.currentVersion);
        } else if (manual) {
            JOptionPane.showMessageDialog(owner,
                    "当前已是最新版本\n\n当前版本: v" + result.currentVersion
                            + (result.latestVersion != null ? "\n最新版本: v" + result.latestVersion : ""),
                    "已是最新版本", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void showError(Window owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "检查更新失败", JOptionPane.ERROR_MESSAGE);
    }

    private static void showUpdateDialog(Window owner, UpdateInfo info, String currentVersion) {
        String date = formatDate(info.releaseDate);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='width:420px'>");
        sb.append("<b>发现新版本！</b><br><br>");
        sb.append("<b>当前版本:</b> v").append(escape(currentVersion)).append("<br>");
        sb.append("<b>最新版本:</b> v").append(escape(info.version)).append("<br>");
        if (!date.isEmpty()) {
            sb.append("<b>发布日期:</b> ").append(escape(date)).append("<br>");
        }
        sb.append("<br><b>更新内容:</b><br>");
        String notes = info.releaseNotes == null ? "" : info.releaseNotes.trim();
        if (notes.isEmpty()) {
            sb.append("<i>（作者未填写更新日志）</i>");
        } else {
            String html = notes
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\r\n", "\n").replace("\n", "<br>");
            sb.append(html);
        }
        sb.append("</div></html>");

        String[] options = {"立即更新", "稍后再说", "查看发布页"};
        int choice = JOptionPane.showOptionDialog(
                owner, sb.toString(), "发现新版本",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                options, options[0]);

        if (choice == 0) {
            downloadAndInstall(owner, info);
        } else if (choice == 2) {
            openInBrowser(info.htmlUrl);
        }
    }

    private static void downloadAndInstall(Window owner, UpdateInfo info) {
        JDialog dialog = new JDialog((Frame) null, "正在下载更新", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("正在连接服务器...");

        JLabel statusLabel = new JLabel("下载新版本 v" + info.version + " 中...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        dialog.setContentPane(panel);
        dialog.setSize(460, 130);
        dialog.setLocationRelativeTo(owner);

        POOL.execute(() -> {
            Path tempFile = null;
            HttpURLConnection conn = null;
            try {
                tempFile = Files.createTempFile("ipcalc_update_", ".exe");
                final Path target = tempFile;

                conn = openConnection(info.downloadUrl);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(60_000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new IOException("下载失败，HTTP 状态码: " + code);
                }

                String clHeader = conn.getHeaderField("Content-Length");
                final long contentLength;
                long tmpLen = -1L;
                if (clHeader != null) {
                    try {
                        tmpLen = Long.parseLong(clHeader.trim());
                    } catch (NumberFormatException ignored) {}
                }
                contentLength = tmpLen;

                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(target,
                             java.nio.file.StandardOpenOption.CREATE,
                             java.nio.file.StandardOpenOption.WRITE,
                             java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        total += n;
                        final long done = total;
                        SwingUtilities.invokeLater(() -> {
                            if (contentLength > 0) {
                                int pct = (int) Math.min(100, done * 100 / contentLength);
                                progressBar.setValue(pct);
                                progressBar.setString(pct + "%  (" + formatSize(done) + " / " + formatSize(contentLength) + ")");
                            } else {
                                progressBar.setIndeterminate(true);
                                progressBar.setString("已下载 " + formatSize(done));
                            }
                        });
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("下载完成，正在启动安装程序...");
                    statusLabel.setText("即将启动安装程序，请稍候...");
                });

                Thread.sleep(600);
                launchInstaller(target);

                SwingUtilities.invokeLater(() -> {
                    dialog.setVisible(false);
                    dialog.dispose();
                    exitApp();
                });
            } catch (Exception ex) {
                Path toDelete = tempFile;
                SwingUtilities.invokeLater(() -> {
                    dialog.setVisible(false);
                    dialog.dispose();
                    showError(owner, "下载更新失败: " + ex.getMessage());
                });
                if (toDelete != null) {
                    try { Files.deleteIfExists(toDelete); } catch (IOException ignored) {}
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        dialog.setVisible(true);
    }

    private static void launchInstaller(Path installer) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("windows")) {
            pb = new ProcessBuilder(installer.toAbsolutePath().toString(), "/passive");
        } else {
            pb = new ProcessBuilder(installer.toAbsolutePath().toString());
        }
        pb.redirectErrorStream(true);
        pb.start();
    }

    private static void exitApp() {
        try {
            CalculationWorker.cancelAllTasks();
        } catch (Throwable ignored) {}
        System.exit(0);
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static UpdateInfo parseRelease(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

            if (obj.has("prerelease") && obj.get("prerelease").getAsBoolean()) return null;
            if (obj.has("draft") && obj.get("draft").getAsBoolean()) return null;

            String tag = getAsString(obj, "tag_name");
            if (tag == null) return null;
            String version = tag.startsWith("v") || tag.startsWith("V")
                    ? tag.substring(1) : tag;

            String publishedAt = getAsString(obj, "published_at");
            String htmlUrl = getAsString(obj, "html_url");
            String notes = getAsString(obj, "body");

            String downloadUrl = null;
            if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                JsonArray assets = obj.getAsJsonArray("assets");
                String fallback = null;
                for (JsonElement e : assets) {
                    JsonObject a = e.getAsJsonObject();
                    String name = getAsString(a, "name");
                    if (name == null) continue;
                    String lower = name.toLowerCase();
                    if (!lower.endsWith(".exe")) continue;
                    String url = getAsString(a, "browser_download_url");
                    if (url == null) continue;
                    if (lower.contains("ipcalculator") || name.contains("子网计算器")) {
                        downloadUrl = url;
                        break;
                    }
                    if (fallback == null) {
                        fallback = url;
                    }
                }
                if (downloadUrl == null) {
                    downloadUrl = fallback;
                }
            }

            return new UpdateInfo(version, publishedAt == null ? "" : publishedAt,
                    downloadUrl, notes == null ? "" : notes,
                    htmlUrl == null ? RELEASES_PAGE_URL : htmlUrl);
        } catch (Exception e) {
            System.err.println("UpdateChecker: 解析 Release JSON 失败: " + e.getMessage());
            return null;
        }
    }

    private static String getAsString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement e = obj.get(key);
        return e.isJsonNull() ? null : e.getAsString();
    }

    public static boolean isNewer(String remote, String current) {
        try {
            String[] r = remote.trim().split("\\.");
            String[] c = current.trim().split("\\.");
            int len = Math.max(r.length, c.length);
            for (int i = 0; i < len; i++) {
                int ri = i < r.length ? safeParseInt(r[i]) : 0;
                int ci = i < c.length ? safeParseInt(c[i]) : 0;
                if (ri > ci) return true;
                if (ri < ci) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int safeParseInt(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            } else {
                break;
            }
        }
        if (sb.length() == 0) return 0;
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        int t = iso.indexOf('T');
        return t > 0 ? iso.substring(0, t) : iso;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("windows")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException ignored) {
        }
    }
}
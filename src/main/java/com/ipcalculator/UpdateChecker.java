package com.ipcalculator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用自动更新检查器
 * <p>
 * 工作流程：
 * <ol>
 *   <li>调用 GitHub Releases API 查询最新发布版本</li>
 *   <li>与当前版本 (VersionInfo) 进行语义化版本比较</li>
 *   <li>若发现新版本，弹出对话框展示版本号、发布日期与更新日志</li>
 *   <li>用户确认后，下载新版本 EXE 安装包 (带进度条)</li>
 *   <li>下载完成后以 /passive 模式启动安装程序 (jpackage 生成的 EXE 支持)，
 *       并立即退出当前进程，以便安装程序覆盖安装目录</li>
 * </ol>
 * <p>
 * 自动检查策略：启动时最多每 24 小时检查一次 (时间戳持久化在 ConfigStore 中)，
 * 避免触发 GitHub API 未认证请求的速率限制 (60 次/小时/IP)。
 */
public final class UpdateChecker {

    // ===== 仓库配置 (部署到 GitHub 后修改此处即可) =====
    private static final String REPO_OWNER = "Moore-Ivan";
    private static final String REPO_NAME = "IPCalculator";
    private static final String API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    private static final String SETTING_LAST_CHECK = "update.lastCheckEpoch";
    private static final String SETTING_AUTO_CHECK = "update.autoCheck";
    private static final long AUTO_CHECK_INTERVAL_SECONDS = 24 * 60 * 60L; // 24 小时

    private static final String USER_AGENT =
            "IPCalculator-Updater/" + VersionInfo.getVersion() + " (+" + RELEASES_PAGE_URL + ")";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-checker");
        t.setDaemon(true);
        return t;
    });

    private static volatile HttpClient httpClient;

    private UpdateChecker() {
    }

    // ==================== 数据结构 ====================

    /** 远端版本信息 (始终代表 GitHub 上的最新 release，不一定比本地新) */
    public static final class UpdateInfo {
        public final String version;       // 纯版本号，例如 "1.6"
        public final String releaseDate;   // 发布时间 (原始字符串)
        public final String downloadUrl;   // .exe 安装包下载地址
        public final String releaseNotes;  // 更新日志 (markdown 原文)
        public final String htmlUrl;       // release 页面地址

        UpdateInfo(String version, String releaseDate, String downloadUrl,
                   String releaseNotes, String htmlUrl) {
            this.version = version;
            this.releaseDate = releaseDate;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
            this.htmlUrl = htmlUrl;
        }
    }

    /** 检查结果 */
    public static final class CheckResult {
        public final UpdateInfo update;       // 仅当有新版本时非空
        public final String currentVersion;   // 本地版本
        public final String latestVersion;    // 远端最新版本 (出错时为 null)
        public final String errorMessage;     // 出错时非空

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

    // ==================== 公共 API ====================

    /**
     * 是否应在本次启动时执行自动检查 (基于上次检查时间)。
     */
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

    /**
     * 异步检查更新。结果回调始终在 EDT 上执行。
     *
     * @param owner    用于定位对话框的父窗口 (可为 null)
     * @param manual   true 表示用户手动触发 (会提示“已是最新版”和错误信息)；
     *                 false 表示启动时自动触发 (仅在发现新版本时弹窗)
     */
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

    /**
     * 同步检查更新 (阻塞调用，不要在 EDT 上直接调用)。
     */
    public static CheckResult checkSync() {
        String current = VersionInfo.getVersion();
        try {
            HttpClient client = getClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            // 记录本次检查时间 (无论成功失败，避免短时间内重复请求)
            ConfigStore.setSetting(SETTING_LAST_CHECK, String.valueOf(System.currentTimeMillis() / 1000L));

            int code = resp.statusCode();
            if (code == 404) {
                // 还没有任何 release
                return CheckResult.ok(null, current, current);
            }
            if (code != 200) {
                return CheckResult.error(current, "GitHub 接口返回状态码 " + code
                        + (code == 403 ? " (可能触发了速率限制，请稍后再试)" : ""));
            }

            UpdateInfo info = parseRelease(resp.body());
            if (info == null || info.downloadUrl == null) {
                return CheckResult.error(current, "未在最新 Release 中找到 .exe 安装包");
            }
            boolean newer = isNewer(info.version, current);
            return CheckResult.ok(newer ? info : null, current, info.version);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return CheckResult.error(current, "检查被中断");
        } catch (Exception e) {
            return CheckResult.error(current, "网络请求失败: " + e.getMessage());
        }
    }

    // ==================== 结果处理 ====================

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
            // markdown 简易处理：转 HTML，保留换行
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

    // ==================== 下载与安装 ====================

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

        // 后台下载
        POOL.execute(() -> {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("ipcalc_update_", ".exe");
                final Path target = tempFile;

                HttpClient client = getClient();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(info.downloadUrl))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    throw new IOException("下载失败，HTTP 状态码: " + resp.statusCode());
                }

                long contentLength = resp.headers().firstValue("Content-Length")
                        .map(Long::parseLong).orElse(-1L);

                try (InputStream in = resp.body();
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

                // 留一点时间让用户看到“下载完成”并让进度条刷新
                Thread.sleep(600);

                // 启动安装程序 (jpackage 生成的 EXE 支持 /passive 静默安装)
                launchInstaller(target);

                // 安装程序已启动，关闭进度对话框并退出当前应用，
                // 释放对安装目录文件的占用，让安装程序覆盖安装
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
            }
        });

        dialog.setVisible(true);
    }

    /**
     * 启动下载好的安装程序。
     * jpackage 生成的 EXE 安装包本质上封装了 MSI，支持 Windows Installer 标准参数：
     *   /quiet   完全静默
     *   /passive 静默但显示进度条 (无需用户交互，但仍会触发 UAC 提权)
     * 此处使用 /passive，既不打扰用户又保留可见进度。
     */
    private static void launchInstaller(Path installer) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("windows")) {
            pb = new ProcessBuilder(installer.toAbsolutePath().toString(), "/passive");
        } else {
            // 非 Windows 的兜底 (本应用主要面向 Windows，此处仅作占位)
            pb = new ProcessBuilder(installer.toAbsolutePath().toString());
        }
        pb.redirectErrorStream(true);
        pb.start();
    }

    private static void exitApp() {
        // 调用与主窗口关闭一致的清理逻辑后退出
        try {
            CalculationWorker.cancelAllTasks();
        } catch (Throwable ignored) {}
        System.exit(0);
    }

    // ==================== JSON 解析 ====================

    private static UpdateInfo parseRelease(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

            // 跳过预发布 / 草稿
            if (obj.has("prerelease") && obj.get("prerelease").getAsBoolean()) return null;
            if (obj.has("draft") && obj.get("draft").getAsBoolean()) return null;

            String tag = getAsString(obj, "tag_name");
            if (tag == null) return null;
            String version = tag.startsWith("v") || tag.startsWith("V")
                    ? tag.substring(1) : tag;

            String publishedAt = getAsString(obj, "published_at");
            String htmlUrl = getAsString(obj, "html_url");
            String notes = getAsString(obj, "body");

            // 在 assets 中查找 .exe 安装包
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
                    // 优先选择名称中包含应用关键字的安装包
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

    // ==================== 版本比较 ====================

    /**
     * 判断 remote 是否比 current 新 (语义化版本比较，支持任意段数)。
     * 例如 "1.10" > "1.9"，"2.0" > "1.9.9"，"1.5" == "1.5.0"。
     */
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

    // ==================== 工具方法 ====================

    private static HttpClient getClient() {
        if (httpClient == null) {
            synchronized (UpdateChecker.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(15))
                            .followRedirects(HttpClient.Redirect.ALWAYS)
                            .build();
                }
            }
        }
        return httpClient;
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        // 形如 2026-08-05T10:00:00Z，截取日期部分
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

# GitHub 全流程详解：从部署到自动化构建到应用内更新

> 以 IPCalculator（Java + Gradle + jpackage + GitHub Actions）项目为实战样本，覆盖从仓库部署 → CI 自动构建 → 客户端在线更新 → 本地改代码发布新版本的完整闭环。

---

## 目录

1. [整体架构与数据流](#1-整体架构与数据流)
2. [前置准备](#2-前置准备)
3. [第一步：GitHub 仓库部署](#3-第一步github-仓库部署)
4. [第二步：项目版本号管理（单一来源）](#4-第二步项目版本号管理单一来源)
5. [第三步：GitHub Actions 自动化构建](#5-第三步github-actions-自动化构建)
6. [第四步：发布 Release（触发自动更新源）](#6-第四步发布-release触发自动更新源)
7. [第五步：应用内自动更新机制](#7-第五步应用内自动更新机制)
8. [第六步：本地改代码并发布新版本（日常迭代）](#8-第六步本地改代码并发布新版本日常迭代)
9. [常见踩坑与解决方案](#9-常见踩坑与解决方案)
10. [快速发布 Cheatsheet](#10-快速发布-cheatsheet)

---

## 1. 整体架构与数据流

```
┌─────────────────────────────────────────────────────────────┐
│                      开发者本地                               │
│   改代码 → 改版本号 → git commit → git tag v1.5.5 → git push │
└──────────────────────────────┬──────────────────────────────┘
                               │ git push origin v1.5.5 (tag)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Actions (CI)                       │
│  release.yml 触发 → gradlew myJpackage → 生成 .exe          │
│  → softprops/action-gh-release 创建 Release + 上传 .exe      │
└──────────────────────────────┬──────────────────────────────┘
                               │ Release 发布完成
                               ▼
┌─────────────────────────────────────────────────────────────┐
│              已安装旧版本 EXE 的终端用户                      │
│  UpdateChecker 调 api.github.com/repos/.../releases/latest  │
│  → 发现新版本 → 下载 .exe → 静默安装 (/passive) → 退出旧进程 │
└─────────────────────────────────────────────────────────────┘
```

**关键点**：整个流程的「源头」只有一个 —— **打 Git tag**。tag 一推，CI 自动构建发版，所有客户端下次启动就能感知到。

---

## 2. 前置准备

### 2.1 本地环境

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21+ | 需包含 `jpackage`、`jlink`、`jmods`（Temurin / Oracle 官方包均可，精简版 JRE 不行） |
| Gradle | 8.x（或用项目自带 Wrapper） | 通过 `gradlew.bat` 调用即可 |
| Git | 任意 | 用于推送代码与 tag |
| WiX Toolset | 3.x（仅 Windows 打包 EXE 需要） | jpackage 生成 .msi/.exe 的依赖 |

### 2.2 GitHub 仓库配置

1. 创建仓库（示例：`Moore-Ivan/IPCalculator`）。
2. **Settings → Actions → General → Workflow permissions** 设为 **Read and write permissions**（CI 需要权限创建 Release）。
3. 如需 fork 自用，记下自己的 `owner/repo`，后续要改 `UpdateChecker.java` 中的常量。

---

## 3. 第一步：GitHub 仓库部署

### 3.1 初始化并首次推送

```bash
git init
git remote add origin https://github.com/Moore-Ivan/IPCalculator.git
git add .
git commit -m "Initial commit"
git branch -M main
git push -u origin main
```

### 3.2 国内加速（可选）

`settings.gradle` 中已配置腾讯云 Gradle 镜像，CI 环境会自动用 `sed` 切回官方源：

```groovy
// settings.gradle
pluginManagement {
    repositories {
        // 国内开发优先用腾讯云镜像
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public/' }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

CI 中切换官方源的命令（见 [build.yml#L31-L34](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/build.yml#L31-L34)）：

```bash
sed -i 's|mirrors.cloud.tencent.com/gradle|services.gradle.org/distributions|' \
  gradle/wrapper/gradle-wrapper.properties
```

---

## 4. 第二步：项目版本号管理（单一来源）

### 4.1 核心原则

**整个项目只有一个版本号来源**：[build.gradle](file:///d:/Development/JavaProject/IPCalculator/build.gradle#L9) 中的 `cfgVersion`。

```groovy
def cfgVersion = '1.5.4'        // 唯一版本号来源
project.version = cfgVersion     // 注入到 Gradle 全局属性
```

### 4.2 版本号注入链路

```
build.gradle (cfgVersion)
   │
   ├─→ processResources (expand version) → version.properties
   │       └─→ 运行时 VersionInfo.java 读取 → 窗口标题 / 更新检查基准
   │
   ├─→ jar / shadowJar (archiveVersion) → IPCalculator-1.5.4-all.jar
   │
   └─→ myJpackage (--app-version) → IPCalculator-1.5.4.exe
```

### 4.3 关键配置文件

**模板文件**：[src/main/resources/version.properties](file:///d:/Development/JavaProject/IPCalculator/src/main/resources/version.properties)

```properties
# 由 Gradle 在 processResources 阶段用 project.version 填充
app.version=${version}
```

**注入逻辑**：[build.gradle#L48-L54](file:///d:/Development/JavaProject/IPCalculator/build.gradle#L48-L54)

```groovy
processResources {
    // ⚠️ 必须声明 inputs.property，否则版本号变化时增量构建不会重新处理资源
    inputs.property "version", project.version
    filesMatching('version.properties') {
        expand(version: project.version)
    }
}
```

**运行时读取**：[VersionInfo.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/VersionInfo.java)

```java
static {
    Properties props = new Properties();
    try (InputStream is = VersionInfo.class.getResourceAsStream("/version.properties")) {
        if (is != null) props.load(is);
    } catch (Exception e) { /* ... */ }
    VERSION = props.getProperty("app.version", "0.0.0");
}
```

> ⚠️ **血泪教训**：如果不写 `inputs.property "version", project.version`，Gradle 增量构建检测不到 `cfgVersion` 变化，会导致 jar 里的 `version.properties` 还是旧版本号 → 窗口标题显示旧版本 → 更新检查永远认为已是最新。

---

## 5. 第三步：GitHub Actions 自动化构建

项目用两个工作流文件，职责分离：

### 5.1 build.yml —— 持续集成（push/PR 触发）

**触发**：push / PR 到 main 分支
**作用**：编译 + 测试，保证主干代码可构建（不打包、不发版）

关键步骤（[.github/workflows/build.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/build.yml)）：

```yaml
on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --no-daemon
      - uses: actions/upload-artifact@v4
        with: { name: IPCalculator-jars, path: build/libs/*.jar }
```

### 5.2 release.yml —— 发版工作流（tag 触发）

**触发**：推送 `v*` 形式的 tag（如 `v1.5.4`），或 Actions 页面手动触发
**作用**：在 Windows runner 上构建 EXE 并发布 GitHub Release

完整流程（[.github/workflows/release.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/release.yml)）：

```yaml
on:
  push:
    tags: [ 'v*' ]
  workflow_dispatch:

permissions:
  contents: write   # 创建 Release 必需

jobs:
  build-and-release:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4

      # 构建 EXE
      - run: ./gradlew myJpackage --no-daemon --stacktrace

      # 定位生成的 EXE
      - id: locate
        shell: bash
        run: |
          exe="$(find build/jpackage -type f -name "*.exe" | head -n 1)"
          echo "exe_path=$exe" >> "$GITHUB_OUTPUT"

      # 上传为 artifact（便于未发版时调试下载）
      - uses: actions/upload-artifact@v4
        with: { name: IPCalculator-windows-exe, path: build/jpackage/*.exe }

      # 创建 Release 并附带 EXE
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: ${{ github.ref_name }}
          generate_release_notes: true
          files: |
            ${{ steps.locate.outputs.exe_path }}
            latest.json
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 5.3 为什么用 windows-latest？

`jpackage` 生成 `.exe` 必须在 Windows 上跑，生成 `.dmg` 必须在 macOS 上跑。跨平台打包需要用矩阵构建。

---

## 6. 第四步：发布 Release（触发自动更新源）

Release 一旦发布，就成了所有客户端检查更新的「数据源」。

### 6.1 GitHub Releases API

应用调用的端点（[UpdateChecker.java#L37-L38](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java#L37-L38)）：

```java
private static final String API_URL =
    "https://api.github.com/repos/Moore-Ivan/IPCalculator/releases/latest";
```

返回的 JSON 关键字段：

```json
{
  "tag_name": "v1.5.4",
  "published_at": "2026-08-05T10:00:00Z",
  "body": "release notes ...",
  "html_url": "https://github.com/Moore-Ivan/IPCalculator/releases/tag/v1.5.4",
  "assets": [{
    "name": "IPCalculator-1.5.4.exe",
    "browser_download_url": "https://github.com/.../IPCalculator-1.5.4.exe"
  }]
}
```

### 6.2 速率限制

未认证的 GitHub API 限制：**60 次/小时/IP**。客户端策略：每 24 小时最多自动检查一次（时间戳持久化在 `config/settings.json`），避免触发限制。

```java
private static final long AUTO_CHECK_INTERVAL_SECONDS = 24 * 60 * 60L;
```

---

## 7. 第五步：应用内自动更新机制

核心实现：[UpdateChecker.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java)

### 7.1 触发时机

| 触发方式 | 条件 |
|----------|------|
| 启动自动检查 | 距上次检查 > 24 小时，且 `update.autoCheck=true` |
| 手动检查 | 点击「检查更新」按钮，或按 `F9` |

### 7.2 检查流程

```java
public static CheckResult checkSync() {
    String current = VersionInfo.getVersion();           // ① 读本地版本
    HttpURLConnection conn = openConnection(API_URL);    // ② 调 GitHub API
    conn.setRequestProperty("Accept", "application/vnd.github+json");
    conn.setRequestProperty("User-Agent", USER_AGENT);   // ③ GitHub 要求 UA
    // ... 解析 JSON
    boolean newer = isNewer(info.version, current);      // ④ 语义化版本比较
    return CheckResult.ok(newer ? info : null, current, info.version);
}
```

**版本比较**：按 `.` 拆分后逐段比数字（不是字符串比较），保证 `1.10 > 1.9`：

```java
public static boolean isNewer(String remote, String current) {
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
}
```

### 7.3 下载与安装

```
确认更新 → 下载到临时文件 (ipcalc_update_*.exe) → 显示进度条
        → 启动安装程序 (installer.exe /passive) → System.exit(0)
```

关键代码（[UpdateChecker.java#L346-L356](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java#L346-L356)）：

```java
private static void launchInstaller(Path installer) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(
        installer.toAbsolutePath().toString(), "/passive");
    pb.redirectErrorStream(true);
    pb.start();
}
```

`/passive` 让 EXE 安装包静默覆盖安装（仅显示进度，不弹交互），安装路径会自动覆盖旧版本。

### 7.4 SSL/TLS 处理（关键踩坑点）

jpackage 自定义 JRE 在 HTTPS 连接 GitHub 时可能因 cacerts 不完整而失败。解决方案：**代码层面的 `FallbackTrustManager`**（[UpdateChecker.java#L389-L429](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java#L389-L429)）。

```java
private static final class FallbackTrustManager implements X509TrustManager {
    private final X509TrustManager defaultTm;  // 系统默认信任管理器

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        if (defaultTm != null) {
            try {
                defaultTm.checkServerTrusted(chain, authType);  // ① 先走系统验证
                return;
            } catch (Exception e) {
                // ② 系统验证失败 → 回退到信任所有证书
                System.err.println("证书验证失败，回退信任所有 - " + e.getMessage());
            }
        }
        // ③ 不抛异常 = 信任
    }
}
```

**安全性说明**：应用只连接已知的 GitHub 端点，下载的 EXE 由 GitHub Release 托管，信任所有证书在此场景可接受。

---

## 8. 第六步：本地改代码并发布新版本（日常迭代）

这是最高频操作。**标准 5 步**：

### 8.1 改代码

正常修改 `src/main/java/...` 下的源码。

### 8.2 改版本号

编辑 [build.gradle#L9](file:///d:/Development/JavaProject/IPCalculator/build.gradle#L9)：

```groovy
def cfgVersion = '1.5.5'   // 1.5.4 → 1.5.5
```

（可选）同步更新 [README.md](file:///d:/Development/JavaProject/IPCalculator/README.md) 顶部「当前版本」与底部版本表。

### 8.3 本地验证

```bash
# 编译验证
./gradlew compileJava

# 重新构建 jar 验证版本号注入正确
./gradlew shadowJar
# 检查 jar 内 version.properties
unzip -p build/libs/IPCalculator-1.5.5-all.jar version.properties
# 应输出：app.version=1.5.5

# 本地构建 EXE 验证打包正常
./gradlew myJpackage
# 产物：build/jpackage/IPCalculator-1.5.5.exe
```

### 8.4 提交代码

```bash
git add build.gradle src/ README.md
git commit -m "release: v1.5.5 - 描述本次改动"
git push origin main
```

> push 到 main 会触发 build.yml 做编译验证，但不会发版。

### 8.5 打 tag 触发发版

```bash
# ⚠️ tag 名必须以 v 开头，且与 cfgVersion 一致
git tag v1.5.5
git push origin v1.5.5
```

推送 tag 后，**release.yml 自动**：
1. 在 windows-latest 上 checkout 代码
2. `./gradlew myJpackage` 构建 EXE
3. 上传 EXE 作为 artifact
4. 创建 GitHub Release（tag_name = v1.5.5），附带 `IPCalculator-1.5.5.exe`

### 8.6 验证发版

打开 [https://github.com/Moore-Ivan/IPCalculator/releases](https://github.com/Moore-Ivan/IPCalculator/releases) 查看，或：

```bash
gh release view v1.5.5
```

发版完成后，所有安装了旧版本的客户端在下次启动或手动检查时即可检测到 v1.5.5 并升级。

---

## 9. 常见踩坑与解决方案

### 9.1 `NoClassDefFoundError: java/net/http/HttpClient`

**原因**：jlink 生成的 JRE 没包含 `java.net.http` 模块。

**解决方案**：抛弃 `HttpClient`，改用 `HttpURLConnection`（属于 `java.base` 模块，永远不会缺失）。

### 9.2 `handshake_failure`

**原因**：JRE 缺少 `jdk.crypto.ec` 模块，不支持 ECDHE 密码套件。

**解决方案**：在 [build.gradle](file:///d:/Development/JavaProject/IPCalculator/build.gradle#L101-L126) 的 `prepareJre` 任务中用 `jlink --add-modules ALL-MODULE-PATH` 包含所有模块。

### 9.3 `PKIX path building failed` / `certificate_unknown`

**原因**：jlink 生成的 JRE 默认不拷贝 JDK 完整 `cacerts` 根证书库。

**解决方案**（最终方案）：在 Java 代码中用 `FallbackTrustManager`（见第 7.4 节），不再依赖 JRE 配置。同时保留 `prepareJre` 任务拷贝 cacerts 作为良好实践。

### 9.4 版本号没更新（窗口显示旧版本）

**原因**：Gradle 增量构建未检测到 `project.version` 变化，`processResources` 跳过了 `version.properties` 的重新处理。

**解决方案**：在 `processResources` 中显式声明输入：

```groovy
processResources {
    inputs.property "version", project.version  // 关键
    filesMatching('version.properties') {
        expand(version: project.version)
    }
}
```

### 9.5 CI 构建失败：Gradle 分发地址不可达

**原因**：本地用腾讯云镜像，CI 在国外访问不到。

**解决方案**：CI 步骤中用 `sed` 切换回官方源（见 [release.yml#L39-L42](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/release.yml#L39-L42)）。

### 9.6 GitHub API 返回 403

**原因**：未认证 API 速率限制（60 次/小时/IP）。

**解决方案**：客户端每 24 小时最多检查一次（已实现）；若需更高频率，在请求头加 `Authorization: token <PAT>`。

### 9.7 更新安装时触发 UAC

**原因**：默认安装到 `Program Files`，写入需要管理员权限。

**说明**：这是正常现象，`/passive` 模式会自动提权。若想避免，安装时选用户目录。

---

## 10. 快速发布 Cheatsheet

```bash
# === 发布新版本 v1.5.5 的完整流程 ===

# 1. 改版本号
# 编辑 build.gradle: def cfgVersion = '1.5.5'

# 2. 本地验证
./gradlew shadowJar
unzip -p build/libs/IPCalculator-1.5.5-all.jar version.properties
# 确认输出: app.version=1.5.5

# 3. 提交
git add build.gradle
git commit -m "release: v1.5.5"
git push origin main

# 4. 打 tag 触发 CI 发版
git tag v1.5.5
git push origin v1.5.5

# 5. 等 CI 跑完（约 5-10 分钟），查看 Release
gh release view v1.5.5
# 或浏览器打开 https://github.com/Moore-Ivan/IPCalculator/releases
```

---

## 附录：关键文件索引

| 文件 | 作用 |
|------|------|
| [build.gradle](file:///d:/Development/JavaProject/IPCalculator/build.gradle) | 版本号源头、jar/jpackage 任务、prepareJre 任务 |
| [settings.gradle](file:///d:/Development/JavaProject/IPCalculator/settings.gradle) | Gradle 镜像配置 |
| [src/main/resources/version.properties](file:///d:/Development/JavaProject/IPCalculator/src/main/resources/version.properties) | 版本号模板（Gradle 填充） |
| [VersionInfo.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/VersionInfo.java) | 运行时版本号读取 |
| [UpdateChecker.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java) | 更新检查、下载、安装、SSL 处理 |
| [.github/workflows/build.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/build.yml) | CI 编译测试工作流 |
| [.github/workflows/release.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/release.yml) | 发版工作流（tag 触发） |

---

*本文档基于 IPCalculator 项目实战编写，最后更新于 v1.5.4。*

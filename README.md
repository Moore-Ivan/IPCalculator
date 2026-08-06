# IP子网计算器 (IPCalculator)

> 基于 Java Swing 开发的桌面级网络计算工具，同时支持 IPv4 与 IPv6 双栈环境，面向网络工程师、系统管理员及运维人员设计，提供从基础地址解析到复杂网络规划的一站式计算能力。

当前版本：**v1.5.6**

## ✨ 功能特性

应用采用多标签页 (Tabbed) 布局，按功能维度划分为 IPv4 与 IPv6 两大体系，覆盖基础解析、子网划分与高级工具三大场景。

### IPv4 功能

**基础工具**

| 功能           | 说明                                      |
| ------------ | --------------------------------------- |
| 网段详情         | 输入 CIDR，输出网络地址、广播地址、掩码、反掩码、可用主机范围、主机数量等 |
| IP 地址解析      | 解析单个 IP 地址所属网段、地址类别（A/B/C）等信息           |
| IP 范围 → CIDR | 给定起止 IP，自动合并为最优 CIDR 列表                 |

**子网划分**

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 等长子网划分      | 按子网数量或每子网主机数，等长划分主网段         |
| VLSM 变长子网划分 | 按各部门主机需求列表进行可变长子网划分，最大化地址利用率 |
| 超网拆分        | 将大网段拆分为指定前缀的子网，支持分页加载海量结果    |

**高级工具**

| 功能     | 说明                   |
| ------ | -------------------- |
| 路由汇总   | 多个离散网段自动汇总为最精简路由     |
| 冲突检测   | 检测多个网段之间是否存在地址重叠     |
| 网络规划向导 | 按部门数量与每部门主机数自动规划子网方案 |

### IPv6 功能

**基础工具**

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 网段详情        | 输入 IPv6 CIDR，输出网络地址、范围、地址数量等 |
| 地址解析        | 解析 IPv6 地址，支持压缩、全展开等多种表示形式   |
| 地址范围 → CIDR | 给定起止 IPv6，自动转换为 CIDR 列表      |

**子网划分**

| 功能        | 说明                             |
| --------- | ------------------------------ |
| 子网划分      | 按前缀或数量划分 IPv6 子网，支持大数据量分页与流式加载 |
| VLSM 变长子网 | IPv6 环境下的可变长子网划分               |

**高级工具**

| 功能           | 说明                     |
| ------------ | ---------------------- |
| 路由汇总         | IPv6 多网段汇总             |
| EUI-64 地址生成  | 基于 MAC 地址生成 IPv6 接口标识符 |
| 多播地址解析       | 解析 IPv6 多播地址的作用域与组信息   |
| DHCPv6 前缀委派  | 计算 DHCPv6 PD 委派前缀      |
| IPv4 映射 IPv6 | IPv4 与 IPv6 映射地址互转     |

### 通用辅助工具

- 🔄 **进制转换器** —— 二/八/十/十六进制互转
- 🎭 **掩码转换器** —— CIDR 前缀 ↔ 点分十进制掩码 ↔ 反掩码互转
- 📋 **输入历史记录** —— 自动保存近期输入，可一键清除
- 📊 **内存监控** —— 实时显示 JVM 内存占用
- 🌗 **明暗双主题** —— 基于 FlatLaf 的亮色 / 暗色主题切换
- 🖥️ **全屏模式** —— 支持一键切换全屏
- 🔄 **在线更新** —— 自动检查 GitHub Release 新版本，一键下载并静默覆盖安装

## ⌨️ 快捷键

| 快捷键        | 功能          |
| ---------- | ----------- |
| `F1`       | 打开快捷键说明     |
| `F2`       | 切换暗色 / 亮色模式 |
| `F3`       | 打开进制转换器     |
| `F4`       | 打开掩码转换器     |
| `F9`       | 检查更新        |
| `F10`      | 联系作者        |
| `F11`      | 切换全屏模式      |
| `Ctrl + L` | 清空结果区域      |
| `Ctrl + C` | 复制结果到剪贴板    |
| `Enter`    | 在输入框中执行计算   |

## 🛠️ 技术栈

- **语言**：Java 21
- **GUI 框架**：Java Swing + [FlatLaf 3.5](https://www.formdev.com/flatlaf/)（现代主题）
- **构建工具**：Gradle (with [Shadow Plugin](https://github.com/GradleUp/shadow))
- **序列化**：Gson 2.10.1（配置持久化）
- **测试框架**：JUnit 4
- **打包工具**：jpackage（生成原生安装包）

## 📦 项目结构

```
IPCalculator/
├── build.gradle                  # 构建脚本（含 jar / shadowJar / jpackage 配置）
├── settings.gradle               # Gradle 设置（含国内镜像源）
├── gradlew / gradlew.bat         # Gradle Wrapper（CI 构建使用）
├── .github/workflows/            # GitHub Actions：build.yml / release.yml
├── custom-jre/                   # 可选：自定义 JRE 运行时镜像
└── src/
    ├── main/
    │   ├── java/com/ipcalculator/
    │   │   ├── SubnetGUI.java            # 主入口 & 主窗口
    │   │   ├── SubnetController.java     # 控制器（UI 与服务层桥梁）
    │   │   ├── SubnetCalculator.java     # IPv4 核心计算引擎
    │   │   ├── IPv6Calculator.java       # IPv6 核心计算引擎
    │   │   ├── CidrValidator.java        # IPv4 输入校验
    │   │   ├── IPv6Validator.java        # IPv6 输入校验
    │   │   ├── CalculationWorker.java    # SwingWorker 异步计算封装
    │   │   ├── ConfigStore.java          # 配置与历史持久化
    │   │   ├── VersionInfo.java          # 版本号 (由 build.gradle 注入)
    │   │   ├── UpdateChecker.java        # 在线更新检查器 (GitHub Releases)
    │   │   ├── *Panel.java               # 各功能标签页面板
    │   │   ├── NumberBaseConverter.java  # 进制转换器
    │   │   ├── MaskConverter.java        # 掩码转换器
    │   │   └── service/
    │   │       ├── IPv4SubnetService.java
    │   │       └── IPv6SubnetService.java
    │   └── resources/
    │       ├── IP子网计算器.ico          # 应用图标
    │       └── version.properties        # 版本号模板 (Gradle 填充)
    └── test/
        └── java/com/ipcalculator/
            └── VlsmTest.java             # 单元测试
```

## 🚀 快速开始

### 环境要求

- JDK 21 或以上
- （可选）Gradle 8.x，或直接使用项目自带的 Gradle Wrapper

### 运行

```bash
# 方式一：使用 Gradle application 插件直接运行
gradle run

# 方式二：构建后运行 jar
gradle jar
java -jar build/libs/IPCalculator-1.5.jar
```

### 构建

```bash
# 1. 构建 Thin Jar（需自行提供依赖）
gradle jar

# 2. 构建 Fat Jar（包含所有依赖，可直接运行）
gradle shadowJar
# 产物：build/libs/IPCalculator-1.5-all.jar

# 3. 生成本地安装包（Windows 生成 .exe，macOS 生成 .dmg）
gradle myJpackage
# 产物：build/jpackage/
```

> 💡 **关于 jpackage**：打包任务依赖于 `installDist`，会自动准备 `lib` 目录。若项目根目录下存在 `custom-jre/` 目录，将作为自定义 JRE 运行时镜像打包，从而显著减小安装包体积。

## 🔄 在线更新

应用内置基于 GitHub Releases 的自动更新机制，安装后无需手动重新下载即可升级到新版本。

**工作流程**：

1. **启动自动检查** —— 程序启动时自动查询 GitHub Releases 最新版本，每 24 小时最多检查一次（时间戳持久化在 `config/settings.json` 中，避免触发 API 速率限制）。仅在发现新版本时弹窗，无新版本不打扰用户。
2. **手动检查** —— 点击底部「检查更新」按钮，或按 `F9`。
3. **版本比较** —— 采用语义化版本比较（`1.10` > `1.9`），与本地 `VersionInfo` 读取的版本号对比。
4. **下载安装** —— 确认更新后，下载新版 EXE 安装包（带进度提示），完成后以 `/passive` 模式启动安装程序并退出当前进程，由安装程序静默覆盖安装。
5. **发布页跳转** —— 也可选择「查看发布页」在浏览器中打开 Release 说明。

> ⚠️ 安装目录位于 `Program Files` 等 system 目录时，更新会触发 UAC 提权，属正常现象。

**配置项**（`config/settings.json`）：

| 键                       | 默认值    | 说明                |
| ----------------------- | ------ | ----------------- |
| `update.autoCheck`      | `true` | 是否在启动时自动检查更新      |
| `update.lastCheckEpoch` | `0`    | 上次检查的 Unix 时间戳（秒） |

**仓库地址**：更新检查器硬编码指向 `Moore-Ivan/IPCalculator`，如需 fork 后自用，修改 [UpdateChecker.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java) 顶部的 `REPO_OWNER` / `REPO_NAME` 常量。

## 🏗️ 架构概览

应用遵循经典的分层架构：

```
┌─────────────────────────────────────────┐
│              View (Swing Panel)         │  各 *Panel.java 负责输入与展示
├─────────────────────────────────────────┤
│         Controller (SubnetController)   │  统一调度，回调驱动 UI 更新
├─────────────────────────────────────────┤
│  Service (IPv4/IPv6SubnetService)       │  业务编排 + 输入校验
├─────────────────────────────────────────┤
│  Core (SubnetCalculator / IPv6Calculator)│ 纯算法层，无 UI 依赖
└─────────────────────────────────────────┘
```

- **核心计算层**（`SubnetCalculator` / `IPv6Calculator`）为纯静态方法，不依赖 Swing，可独立复用或单元测试。
- **Service 层**封装校验与结果转换，IPv6 大数据量场景统一使用分页 / 流式 / 迭代器接口，避免 OOM。
- **异步计算**通过 `CalculationWorker`（基于 `SwingWorker`）执行，保证 UI 响应性，并支持任务取消。

## 🧪 测试

```bash
gradle test
```

现有测试覆盖 VLSM 子网划分等核心算法，位于 [VlsmTest.java](file:///d:/Development/JavaProject/IPCalculator/src/test/java/com/ipcalculator/VlsmTest.java)。

## � 发布与部署

项目通过 GitHub Actions 自动构建 Windows EXE 安装包并发布 Release，客户端的在线更新依赖于此 Release。

### 一次性配置

1. 在 GitHub 创建仓库 `Moore-Ivan/IPCalculator`（或你自己的仓库）。
2. 推送代码到仓库（见下文「首次推送」）。
3. 若仓库名 / 所有者不同，需同步修改 [UpdateChecker.java](file:///d:/Development/JavaProject/IPCalculator/src/main/java/com/ipcalculator/UpdateChecker.java) 顶部的 `REPO_OWNER` / `REPO_NAME`。
4. 确认仓库 **Settings → Actions → General → Workflow permissions** 已授予 **Read and write permissions**（用于创建 Release）。

### 首次推送

```bash
git init
git remote add origin https://github.com/Moore-Ivan/IPCalculator.git
git add .
git commit -m "Initial commit"
git branch -M main
git push -u origin main
```

### 发布新版本（触发自动更新）

发布流程：**改版本号 → 提交 → 打标签 → 推送标签**，CI 会自动构建并发布 Release。

```bash
# 1. 修改 build.gradle 中的 cfgVersion（项目唯一的版本号来源）
#    def cfgVersion = '1.6'

# 2. 提交改动
git add build.gradle
git commit -m "release: v1.6"

# 3. 打标签 (必须以 v 开头，与 cfgVersion 保持一致)
git tag v1.6

# 4. 推送标签，触发 Release 工作流
git push origin v1.6
```

推送 `v*` 标签后，[release.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/release.yml) 会：

- 在 windows-latest 上用 JDK 21 调用 `./gradlew myJpackage` 构建 EXE；
- 在 [Releases 页面](https://github.com/Moore-Ivan/IPCalculator/releases) 创建新 Release，附带 `IPCalculator-<版本>.exe` 安装包与 `latest.json`。

发布完成后，已安装旧版本的用户在启动应用或点击「检查更新」时即可检测到新版本并一键升级。

> 💡 **版本号一致性**：`build.gradle` 的 `cfgVersion` 是项目唯一的版本号来源，构建时会注入到 `version.properties`，供窗口标题、联系作者页面、更新检查器统一读取。打标签时务必让标签名（去掉 `v`）与 `cfgVersion` 一致。

### 工作流说明

| 工作流                                                                                          | 触发条件             | 作用                        |
| -------------------------------------------------------------------------------------------- | ---------------- | ------------------------- |
| [build.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/build.yml)     | push / PR 到 main | 编译 + 测试，保证主干可构建           |
| [release.yml](file:///d:/Development/JavaProject/IPCalculator/.github/workflows/release.yml) | 推送 `v*` 标签 / 手动  | 构建 EXE 并发布 GitHub Release |

## 📖 版本

| 版本     | 说明                                                                        |
| ------ | ------------------------------------------------------------------------- |
| v1.5.4 | 彻底修复 SSL 问题：FallbackTrustManager（系统验证失败自动回退信任所有证书）                        |
| v1.5.3 | 修复 PKIX/certificate\_unknown：预构建 JRE (jlink ALL-MODULE-PATH + 完整 cacerts) |
| v1.5.2 | 修复 SSL handshake\_failure：jlink 加密模块补全 + TLSv1.3 显式上下文                    |
| v1.5.1 | 修复 NoClassDefFoundError(HttpClient)，改用 HttpURLConnection 消除模块化依赖          |

## 📄 许可

Copyright © Ivan. All rights reserved.

## 👤 联系作者

应用内点击「联系作者」按钮，或按 `F10`。

# IP子网计算器 (IPCalculator)

> 基于 Java Swing 开发的桌面级网络计算工具，同时支持 IPv4 与 IPv6 双栈环境，面向网络工程师、系统管理员及运维人员设计，提供从基础地址解析到复杂网络规划的一站式计算能力。

当前版本：**v1.4**

## ✨ 功能特性

应用采用多标签页 (Tabbed) 布局，按功能维度划分为 IPv4 与 IPv6 两大体系，覆盖基础解析、子网划分与高级工具三大场景。

### IPv4 功能

**基础工具**

| 功能 | 说明 |
|------|------|
| 网段详情 | 输入 CIDR，输出网络地址、广播地址、掩码、反掩码、可用主机范围、主机数量等 |
| IP 地址解析 | 解析单个 IP 地址所属网段、地址类别（A/B/C）等信息 |
| IP 范围 → CIDR | 给定起止 IP，自动合并为最优 CIDR 列表 |

**子网划分**

| 功能 | 说明 |
|------|------|
| 等长子网划分 | 按子网数量或每子网主机数，等长划分主网段 |
| VLSM 变长子网划分 | 按各部门主机需求列表进行可变长子网划分，最大化地址利用率 |
| 超网拆分 | 将大网段拆分为指定前缀的子网，支持分页加载海量结果 |

**高级工具**

| 功能 | 说明 |
|------|------|
| 路由汇总 | 多个离散网段自动汇总为最精简路由 |
| 冲突检测 | 检测多个网段之间是否存在地址重叠 |
| 网络规划向导 | 按部门数量与每部门主机数自动规划子网方案 |

### IPv6 功能

**基础工具**

| 功能 | 说明 |
|------|------|
| 网段详情 | 输入 IPv6 CIDR，输出网络地址、范围、地址数量等 |
| 地址解析 | 解析 IPv6 地址，支持压缩、全展开等多种表示形式 |
| 地址范围 → CIDR | 给定起止 IPv6，自动转换为 CIDR 列表 |

**子网划分**

| 功能 | 说明 |
|------|------|
| 子网划分 | 按前缀或数量划分 IPv6 子网，支持大数据量分页与流式加载 |
| VLSM 变长子网 | IPv6 环境下的可变长子网划分 |

**高级工具**

| 功能 | 说明 |
|------|------|
| 路由汇总 | IPv6 多网段汇总 |
| EUI-64 地址生成 | 基于 MAC 地址生成 IPv6 接口标识符 |
| 多播地址解析 | 解析 IPv6 多播地址的作用域与组信息 |
| DHCPv6 前缀委派 | 计算 DHCPv6 PD 委派前缀 |
| IPv4 映射 IPv6 | IPv4 与 IPv6 映射地址互转 |

### 通用辅助工具

- 🔄 **进制转换器** —— 二/八/十/十六进制互转
- 🎭 **掩码转换器** —— CIDR 前缀 ↔ 点分十进制掩码 ↔ 反掩码互转
- 📋 **输入历史记录** —— 自动保存近期输入，可一键清除
- 📊 **内存监控** —— 实时显示 JVM 内存占用
- 🌗 **明暗双主题** —— 基于 FlatLaf 的亮色 / 暗色主题切换
- 🖥️ **全屏模式** —— 支持一键切换全屏

## ⌨️ 快捷键

| 快捷键 | 功能 |
|--------|------|
| `F1` | 打开快捷键说明 |
| `F2` | 切换暗色 / 亮色模式 |
| `F3` | 打开进制转换器 |
| `F4` | 打开掩码转换器 |
| `F10` | 联系作者 |
| `F11` | 切换全屏模式 |
| `Ctrl + L` | 清空结果区域 |
| `Ctrl + C` | 复制结果到剪贴板 |
| `Enter` | 在输入框中执行计算 |

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
    │   │   ├── *Panel.java               # 各功能标签页面板
    │   │   ├── NumberBaseConverter.java  # 进制转换器
    │   │   ├── MaskConverter.java        # 掩码转换器
    │   │   └── service/
    │   │       ├── IPv4SubnetService.java
    │   │       └── IPv6SubnetService.java
    │   └── resources/
    │       └── IP子网计算器.ico          # 应用图标
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
java -jar build/libs/IPCalculator-1.4.jar
```

### 构建

```bash
# 1. 构建 Thin Jar（需自行提供依赖）
gradle jar

# 2. 构建 Fat Jar（包含所有依赖，可直接运行）
gradle shadowJar
# 产物：build/libs/IPCalculator-1.4-all.jar

# 3. 生成本地安装包（Windows 生成 .exe，macOS 生成 .dmg）
gradle myJpackage
# 产物：build/jpackage/
```

> 💡 **关于 jpackage**：打包任务依赖于 `installDist`，会自动准备 `lib` 目录。若项目根目录下存在 `custom-jre/` 目录，将作为自定义 JRE 运行时镜像打包，从而显著减小安装包体积。

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

## 📌 版本

| 版本 | 说明 |
|------|------|
| v1.4 | 当前版本，IPv4 + IPv6 双栈支持，FlatLaf 主题，jpackage 原生打包 |

## 📄 许可

Copyright © Ivan. All rights reserved.

## 👤 联系作者

应用内点击「联系作者」按钮，或按 `F10`。

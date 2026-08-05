package com.ipcalculator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;

public class SubnetGUI extends JFrame {

    // 输出组件
    private ResultTablePanel resultTablePanel;
    private SubnetTablePanel tablePanel;
    private CardLayout cardLayout;
    private JPanel outputPanel;
    private JLabel statusLabel;
    private JLabel memoryLabel;
    private javax.swing.Timer memoryTimer;
    private boolean isFullscreen = false;
    private boolean isDarkTheme = false;
    private JButton themeBtn;
    private GraphicsDevice graphicsDevice;
    private volatile boolean isClosing = false;
    private Rectangle savedBounds;

    public SubnetGUI() {
        setTitle("IP子网计算器 v1.4 (IPv4 + IPv6)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 580);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                isClosing = true;
                CalculationWorker.cancelAllTasks();
                cleanup();
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        
        // ========== IPv4 基础 ==========
        JTabbedPane ipv4BasicTab = new JTabbedPane();
        ipv4BasicTab.addTab("网段详情", new DetailPanel(this::setResult, this::setStatus));
        ipv4BasicTab.addTab("IP 地址解析", new IPParsePanel(this::setResult, this::setStatus));
        ipv4BasicTab.addTab("IP 范围→CIDR", new RangeToCidrPanel(this::setTableResult, this::setStatus));
        tabbedPane.addTab("IPv4 基础", ipv4BasicTab);
        
        // ========== IPv4 子网划分 ==========
        JTabbedPane ipv4SubnetTab = new JTabbedPane();
        ipv4SubnetTab.addTab("等长子网", new EqualSubnetPanel(this::setTableResult, this::setStatus));
        ipv4SubnetTab.addTab("VLSM 变长子网", new VlsmPanel(this::setTableResult, this::setStatus));
        ipv4SubnetTab.addTab("超网拆分", new SupernetSplitPanel(this::setTableResult, this::setPagedTableResult, this::setStatus));
        tabbedPane.addTab("IPv4 子网划分", ipv4SubnetTab);
        
        // ========== IPv4 高级工具 ==========
        JTabbedPane ipv4AdvancedTab = new JTabbedPane();
        ipv4AdvancedTab.addTab("路由汇总", new SummaryPanel(this::setResult, this::setStatus));
        ipv4AdvancedTab.addTab("冲突检测", new OverlapPanel(this::setResult, this::setStatus));
        ipv4AdvancedTab.addTab("网络规划向导", new PlanPanel(this::setTableResult, this::setStatus));
        tabbedPane.addTab("IPv4 高级工具", ipv4AdvancedTab);
        
        // ========== IPv6 基础 ==========
        JTabbedPane ipv6BasicTab = new JTabbedPane();
        ipv6BasicTab.addTab("网段详情", new IPv6DetailPanel(this::setResult, this::setStatus));
        ipv6BasicTab.addTab("地址解析", new IPv6ParsePanel(this::setResult, this::setStatus));
        ipv6BasicTab.addTab("地址范围→CIDR", new IPv6RangePanel(this::setTableResult, this::setStatus));
        tabbedPane.addTab("IPv6 基础", ipv6BasicTab);
        
        // ========== IPv6 子网划分 ==========
        JTabbedPane ipv6SubnetTab = new JTabbedPane();
        ipv6SubnetTab.addTab("子网划分", new IPv6SubnetPanel(this::setTableResult, this::setPagedTableResult, this::setStatus));
        ipv6SubnetTab.addTab("VLSM 变长子网", new IPv6VlsmPanel(this::setTableResult, this::setStatus));
        tabbedPane.addTab("IPv6 子网划分", ipv6SubnetTab);
        
        // ========== IPv6 高级工具 ==========
        JTabbedPane ipv6AdvancedTab = new JTabbedPane();
        ipv6AdvancedTab.addTab("路由汇总", new IPv6SummaryPanel(this::setResult, this::setStatus));
        ipv6AdvancedTab.addTab("EUI-64 地址生成", new IPv6EUI64Panel(this::setResult, this::setStatus));
        ipv6AdvancedTab.addTab("多播地址解析", new IPv6MulticastPanel(this::setResult, this::setStatus));
        ipv6AdvancedTab.addTab("DHCPv6 前缀委派", new IPv6PrefixDelegationPanel(this::setResult, this::setStatus));
        ipv6AdvancedTab.addTab("IPv4 映射 IPv6", new IPv4MappedPanel(this::setResult, this::setStatus));
        tabbedPane.addTab("IPv6 高级工具", ipv6AdvancedTab);

        // 输出区域
        resultTablePanel = new ResultTablePanel();
        
        tablePanel = new SubnetTablePanel();
        tablePanel.setStatusCallback(this::setStatus);

        cardLayout = new CardLayout();
        outputPanel = new JPanel(cardLayout);
        outputPanel.add(resultTablePanel, "TEXT");
        outputPanel.add(tablePanel, "TABLE");

        // 底部按钮栏 + 状态栏
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("清空结果");
        clearBtn.setToolTipText("清空结果区域 (Ctrl+L)");
        clearBtn.setMnemonic(KeyEvent.VK_L);
        clearBtn.addActionListener(e -> {
            resultTablePanel.clearData();
            tablePanel.clearData();
        });

        JButton copyBtn = new JButton("复制结果");
        copyBtn.setToolTipText("复制结果到剪贴板 (Ctrl+C)");
        copyBtn.setMnemonic(KeyEvent.VK_C);
        copyBtn.addActionListener(e -> copyResults());
        
        JButton contactBtn = new JButton("联系作者");
        contactBtn.setToolTipText("点击查看作者联系方式");
        contactBtn.addActionListener(e -> {
            boolean opened = ContactAuthor.showContactInfo();
            if (opened) {
                setStatus("已打开联系作者页面");
            } else {
                setStatus("无法打开联系作者页面", true);
            }
        });

        JButton shortcutBtn = new JButton("快捷键说明");
        shortcutBtn.setToolTipText("查看所有快捷键说明 (F1)");
        shortcutBtn.addActionListener(e -> ShortcutHelp.showShortcuts(this));

        JButton clearHistoryBtn = new JButton("清除历史");
        clearHistoryBtn.setToolTipText("清除所有输入框历史记录");
        clearHistoryBtn.addActionListener(e -> {
            ConfigStore.clearAllInputHistory();
            setStatus("已清除所有输入历史");
        });

        JButton baseConvertBtn = new JButton("进制转换");
        baseConvertBtn.setToolTipText("打开进制转换器 (F3)");
        baseConvertBtn.addActionListener(e -> {
            setStatus("已打开进制转换器");
            NumberBaseConverter.showConverter(this);
        });

        JButton maskConvertBtn = new JButton("掩码转换");
        maskConvertBtn.setToolTipText("打开掩码转换器 (F4)");
        maskConvertBtn.addActionListener(e -> {
            setStatus("已打开掩码转换器");
            MaskConverter.showConverter(this);
        });

        themeBtn = new JButton("暗色主题");
        themeBtn.setToolTipText("切换暗色/亮色主题");
        themeBtn.addActionListener(e -> toggleTheme());

        buttonPanel.add(contactBtn);
        buttonPanel.add(shortcutBtn);
        buttonPanel.add(themeBtn);
        buttonPanel.add(clearHistoryBtn);
        buttonPanel.add(baseConvertBtn);
        buttonPanel.add(maskConvertBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        statusLabel = new JLabel(" 就绪");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        
        memoryLabel = new JLabel();
        memoryLabel.setBorder(BorderFactory.createEtchedBorder());
        updateMemoryUsage();
        
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(memoryLabel, BorderLayout.EAST);
        
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        memoryTimer = new javax.swing.Timer(1000, e -> updateMemoryUsage());
        memoryTimer.start();

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        mainPanel.add(tabbedPane, BorderLayout.NORTH);
        mainPanel.add(outputPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        setupGlobalShortcuts();
        updateThemeButtonText();
        cardLayout.show(outputPanel, "TEXT");
    }

    // ========== 全局快捷键 ==========

    private void setupGlobalShortcuts() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        KeyStroke ctrlL = KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(ctrlL, "clearResults");
        actionMap.put("clearResults", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultTablePanel.clearData();
                tablePanel.clearData();
            }
        });

        KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(ctrlC, "copyResults");
        actionMap.put("copyResults", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyResults();
            }
        });

        KeyStroke f11 = KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0);
        inputMap.put(f11, "toggleFullscreen");
        actionMap.put("toggleFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullscreen();
            }
        });

        KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        inputMap.put(f1, "showShortcuts");
        actionMap.put("showShortcuts", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ShortcutHelp.showShortcuts(SubnetGUI.this);
            }
        });

        KeyStroke f2Key = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        inputMap.put(f2Key, "toggleTheme");
        actionMap.put("toggleTheme", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleTheme();
            }
        });

        KeyStroke f3Key = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);
        inputMap.put(f3Key, "showBaseConverter");
        actionMap.put("showBaseConverter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setStatus("已打开进制转换器");
                NumberBaseConverter.showConverter(SubnetGUI.this);
            }
        });

        KeyStroke f4Key = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0);
        inputMap.put(f4Key, "showMaskConverter");
        actionMap.put("showMaskConverter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setStatus("已打开掩码转换器");
                MaskConverter.showConverter(SubnetGUI.this);
            }
        });

        KeyStroke f10Key = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);
        inputMap.put(f10Key, "showContactAuthor");
        actionMap.put("showContactAuthor", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean opened = ContactAuthor.showContactInfo();
                if (opened) {
                    setStatus("已打开联系作者页面");
                } else {
                    setStatus("无法打开联系作者页面", true);
                }
            }
        });
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            // 退出全屏模式：恢复进入全屏前的窗口尺寸和位置
            graphicsDevice.setFullScreenWindow(null);
            setUndecorated(false);
            if (savedBounds != null) {
                setBounds(savedBounds);
            } else {
                pack();
            }
        } else {
            // 进入全屏模式：保存当前窗口尺寸和位置
            savedBounds = getBounds();
            setUndecorated(true);
            graphicsDevice.setFullScreenWindow(this);
        }
        isFullscreen = !isFullscreen;
    }

    private void cleanup() {
        if (memoryTimer != null) {
            memoryTimer.stop();
            memoryTimer = null;
        }
    }

    private void loadTheme() {
        isDarkTheme = Boolean.parseBoolean(ConfigStore.getSetting("theme.dark", "false"));
        try {
            if (isDarkTheme) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
        } catch (Exception e) {
            System.err.println("加载主题失败: " + e.getMessage());
        }
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        ConfigStore.setSetting("theme.dark", String.valueOf(isDarkTheme));
        try {
            if (isDarkTheme) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
            updateThemeButtonText();
            setStatus(isDarkTheme ? "已切换到暗色主题" : "已切换到亮色主题");
        } catch (Exception e) {
            setStatus("切换主题失败: " + e.getMessage(), true);
            isDarkTheme = !isDarkTheme;
        }
    }

    private void updateThemeButtonText() {
        if (themeBtn != null) {
            themeBtn.setText(isDarkTheme ? "亮色模式" : "暗色模式");
        }
    }
    
    // ========== 辅助方法 ==========

    private void setResult(String text) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            resultTablePanel.setText(text);
            cardLayout.show(outputPanel, "TEXT");
            setStatus("计算完成");
        });
    }

    private void setTableResult(List<SubnetTablePanel.SubnetRow> data) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            tablePanel.setData(data);
            cardLayout.show(outputPanel, "TABLE");
            setStatus("计算完成");
        });
    }

    private void setPagedTableResult(long totalCount, SubnetTablePanel.PagedDataProvider provider) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            tablePanel.setPagedData(totalCount, provider);
            cardLayout.show(outputPanel, "TABLE");
            setStatus("计算完成");
        });
    }

    private void setStatus(String msg) {
        setStatus(msg, false);
    }

    private void setStatus(String msg, boolean isError) {
        if (isClosing || !isDisplayable()) return;
        statusLabel.setText(" " + msg);
        statusLabel.setForeground(isError ? Color.RED : UIManager.getColor("Label.foreground"));
    }
    
    private void copyResults() {
        Component visibleComponent = null;
        for (Component c : outputPanel.getComponents()) {
            if (c.isVisible()) {
                visibleComponent = c;
                break;
            }
        }
        if (visibleComponent == tablePanel) {
            String tableData = tablePanel.getAllDataAsText();
            if (tableData.isEmpty()) {
                setStatus("表格为空，无内容可复制", true);
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(tableData), null);
            setStatus("表格数据已复制到剪贴板");
        } else {
            String tableData = resultTablePanel.getAllDataAsText();
            if (tableData.isEmpty()) {
                setStatus("结果为空，无内容可复制", true);
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(tableData), null);
            setStatus("结果已复制到剪贴板");
        }
    }
    
    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        String usedStr = formatMemory(usedMemory);
        String maxStr = formatMemory(maxMemory);
        
        memoryLabel.setText(" 内存: " + usedStr + " / " + maxStr);
    }
    
    private String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    // ========== Main ==========

    public static void main(String[] args) {
        try {
            boolean isDark = Boolean.parseBoolean(ConfigStore.getSetting("theme.dark", "false"));
            if (isDark) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
        SwingUtilities.invokeLater(() -> new SubnetGUI().setVisible(true));
    }
}

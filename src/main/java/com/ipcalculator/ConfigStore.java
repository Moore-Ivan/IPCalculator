package com.ipcalculator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置存储工具类
 * 使用 JSON 格式存储配置数据，保证版本兼容性
 * 配置文件存储在程序目录的 config 子目录中
 */
public class ConfigStore {

    private static final String HISTORY_FILE = "history.json";
    private static final String OLD_HISTORY_FILE = "history.dat";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String OLD_SETTINGS_FILE = "settings.dat";
    private static final int MAX_HISTORY_SIZE = 10;

    private static String configDirPath;
    private static Properties settings = new Properties();
    // 使用 ConcurrentHashMap，value 使用 CopyOnWriteArrayList 保证线程安全
    private static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> historyMap = new ConcurrentHashMap<>();
    private static volatile boolean loadFailed = false;
    // 统一锁对象，保护所有 historyMap 操作的原子性
    private static final Object historyLock = new Object();
    
    // Gson 实例，配置为美化输出便于人工阅读
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    static {
        initConfigDir();
        loadAll();
    }

    /**
     * 初始化配置目录，使用应用程序目录下的 config 文件夹
     */
    private static void initConfigDir() {
        configDirPath = "config";
        File dir = new File(configDirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static String getConfigFile(String filename) {
        return configDirPath + File.separator + filename;
    }

    private static void loadAll() {
        loadSettings();
        loadHistory();
    }

    private static void loadSettings() {
        File jsonFile = new File(getConfigFile(SETTINGS_FILE));
        File oldFile = new File(getConfigFile(OLD_SETTINGS_FILE));
        
        // 优先尝试加载 JSON 文件
        if (jsonFile.exists()) {
            loadSettingsFromJson(jsonFile);
        } else if (oldFile.exists()) {
            // 如果没有 JSON 文件但有旧的 .dat 文件，尝试迁移
            loadSettingsFromOldFormat(oldFile);
            // 迁移后保存为 JSON 格式
            saveSettings();
            // 删除旧文件
            oldFile.delete();
        }
    }

    private static void loadSettingsFromJson(File file) {
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                settings.clear();
                settings.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("ConfigStore: 加载设置失败: " + e.getMessage());
            loadFailed = true;
        }
    }

    private static void loadSettingsFromOldFormat(File file) {
        try (InputStream is = new FileInputStream(file)) {
            settings.load(is);
        } catch (IOException e) {
            System.err.println("ConfigStore: 加载旧格式设置失败: " + e.getMessage());
            loadFailed = true;
        }
    }

    private static void loadHistory() {
        File jsonFile = new File(getConfigFile(HISTORY_FILE));
        File oldFile = new File(getConfigFile(OLD_HISTORY_FILE));
        
        // 优先尝试加载 JSON 文件
        if (jsonFile.exists()) {
            loadHistoryFromJson(jsonFile);
        } else if (oldFile.exists()) {
            // 如果没有 JSON 文件但有旧的 .dat 文件，尝试迁移
            loadHistoryFromOldFormat(oldFile);
            // 迁移后保存为 JSON 格式
            saveHistory();
            // 删除旧文件
            oldFile.delete();
        }
    }

    private static void loadHistoryFromJson(File file) {
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                historyMap.clear();
                for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
                    historyMap.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
                }
            }
        } catch (IOException e) {
            System.err.println("ConfigStore: 加载历史失败: " + e.getMessage());
            loadFailed = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadHistoryFromOldFormat(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, List<String>> loaded = (Map<String, List<String>>) ois.readObject();
            historyMap.clear();
            for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
                historyMap.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("ConfigStore: 加载旧格式历史失败: " + e.getMessage());
            loadFailed = true;
        }
    }

    private static void saveSettings() {
        try (Writer writer = new FileWriter(getConfigFile(SETTINGS_FILE))) {
            Map<String, String> settingsMap = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> entry : settings.entrySet()) {
                settingsMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
            gson.toJson(settingsMap, writer);
        } catch (IOException e) {
            System.err.println("ConfigStore: 保存设置失败: " + e.getMessage());
        }
    }

    private static void saveHistory() {
        try (Writer writer = new FileWriter(getConfigFile(HISTORY_FILE))) {
            synchronized (historyLock) {
                Map<String, List<String>> copy = new LinkedHashMap<>();
                for (Map.Entry<String, CopyOnWriteArrayList<String>> entry : historyMap.entrySet()) {
                    copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                gson.toJson(copy, writer);
            }
        } catch (IOException e) {
            System.err.println("ConfigStore: 保存历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置目录路径（用于调试）
     */
    public static String getConfigDirPath() {
        return configDirPath;
    }
    
    // ========== 设置操作 ==========
    
    public static void setSetting(String key, String value) {
        settings.setProperty(key, value);
        saveSettings();
    }
    
    public static String getSetting(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }
    
    public static int getSetting(String key, int defaultValue) {
        String value = settings.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static void setSetting(String key, int value) {
        settings.setProperty(key, String.valueOf(value));
        saveSettings();
    }
    
    // ========== 历史记录操作 ==========

    public static void addHistory(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        value = value.trim();
        synchronized (historyLock) {
            CopyOnWriteArrayList<String> history = historyMap.computeIfAbsent(fieldName, k -> new CopyOnWriteArrayList<>());

            if (!history.isEmpty() && history.get(0).equals(value)) {
                return;
            }

            history.remove(value);
            history.add(0, value);
            if (history.size() > MAX_HISTORY_SIZE) {
                CopyOnWriteArrayList<String> trimmed = new CopyOnWriteArrayList<>(history.subList(0, MAX_HISTORY_SIZE));
                historyMap.put(fieldName, trimmed);
            }
        }
        saveHistory();
    }
    
    public static List<String> getHistory(String fieldName) {
        CopyOnWriteArrayList<String> list = historyMap.get(fieldName);
        return list != null ? list : new CopyOnWriteArrayList<>();
    }
    
    public static void clearHistory(String fieldName) {
        synchronized (historyLock) {
            historyMap.remove(fieldName);
        }
        saveHistory();
    }
    
    public static void clearAllHistory() {
        synchronized (historyLock) {
            historyMap.clear();
        }
        saveHistory();
    }

    /**
     * 清除所有输入历史（不清除设置）
     */
    public static void clearAllInputHistory() {
        synchronized (historyLock) {
            historyMap.clear();
        }
        saveHistory();
    }
    
    // ========== 列宽保存 ==========
    
    public static void saveColumnWidths(String tableName, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(widths[i]);
        }
        setSetting("columnWidths." + tableName, sb.toString());
    }
    
    public static int[] loadColumnWidths(String tableName, int[] defaults) {
        String value = getSetting("columnWidths." + tableName, null);
        if (value == null) {
            return defaults;
        }
        String[] parts = value.split(",");
        int[] widths = new int[Math.min(parts.length, defaults.length)];
        for (int i = 0; i < widths.length; i++) {
            try {
                widths[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                widths[i] = defaults[i];
            }
        }
        return widths;
    }
}

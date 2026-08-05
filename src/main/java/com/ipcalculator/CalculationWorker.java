package com.ipcalculator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CalculationWorker {

    private static final List<SwingWorker<?, ?>> activeWorkers = new CopyOnWriteArrayList<>();
    // 使用 WeakHashMap 避免内存泄漏，Worker 被 GC 时自动清理
    private static final WeakHashMap<SwingWorker<?, ?>, String> workerMessages = new WeakHashMap<>();

    public interface CalculationCallback<T> {
        default void onStart() {}
        void onProgress(int progress, String message);
        void onComplete(T result);
        void onError(Exception e);
    }

    public static <T> void execute(CallableWithProgress<T> task, CalculationCallback<T> callback) {
        final SwingWorker<T, Object> worker = new SwingWorker<T, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                task.setProgressHandler((progress, message) -> {
                    if (!isCancelled()) {
                        setProgress(progress);
                        workerMessages.put(this, message != null ? message : "");
                    }
                });

                // 在调用任务前检查是否已取消
                if (isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Task cancelled");
                }

                return task.call();
            }

            @Override
            protected void done() {
                activeWorkers.remove(this);
                workerMessages.remove(this);
                if (isCancelled()) {
                    return;
                }
                try {
                    T result = get();
                    if (callback != null) {
                        callback.onComplete(result);
                    }
                } catch (java.util.concurrent.CancellationException e) {
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    // 检测是否为取消操作引起的异常
                    if (cause instanceof RuntimeException && 
                        (cause.getMessage() != null && cause.getMessage().contains("已取消"))) {
                        // 用户取消操作，静默处理
                        return;
                    }
                    if (callback != null) {
                        callback.onError(cause instanceof Exception ? (Exception) cause : e);
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e);
                    }
                }
            }
        };

        if (callback != null) {
            worker.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress = (Integer) evt.getNewValue();
                        String message = workerMessages.getOrDefault(worker, "");
                        callback.onProgress(progress, message);
                    }
                }
            });
        }

        activeWorkers.add(worker);
        worker.execute();
    }

    public static void cancelAllTasks() {
        for (SwingWorker<?, ?> worker : activeWorkers) {
            if (!worker.isDone()) {
                worker.cancel(true);
            }
        }
        activeWorkers.clear();
        workerMessages.clear();
    }

    public static int getActiveTaskCount() {
        return activeWorkers.size();
    }

    public interface CallableWithProgress<T> {
        void setProgressHandler(ProgressHandler handler);
        T call() throws Exception;
    }

    @FunctionalInterface
    public interface ProgressHandler {
        void onProgress(int progress, String message);
    }

    public static <T> void executeWithProgress(Component parent, String title,
            CallableWithProgress<T> task, CalculationCallback<T> callback) {

        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(parentWindow, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setSize(320, 140);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        JLabel messageLabel = new JLabel("正在计算...");

        JButton cancelBtn = new JButton("取消");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(cancelBtn);

        panel.add(messageLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.pack();

        final SwingWorker<T, ?>[] workerRef = new SwingWorker[1];

        CalculationCallback<T> wrappedCallback = new CalculationCallback<T>() {
            @Override
            public void onProgress(int progress, String message) {
                if (messageLabel != null) {
                    messageLabel.setText(message);
                }
                if (progressBar != null && progress >= 0 && progress <= 100) {
                    progressBar.setValue(progress);
                }
                if (callback != null) {
                    callback.onProgress(progress, message);
                }
            }

            @Override
            public void onComplete(T result) {
                dialog.dispose();
                if (callback != null) {
                    callback.onComplete(result);
                }
            }

            @Override
            public void onError(Exception e) {
                dialog.dispose();
                if (callback != null) {
                    callback.onError(e);
                }
            }
        };

        cancelBtn.addActionListener(e -> {
            if (workerRef[0] != null) {
                workerRef[0].cancel(true);
            }
            dialog.dispose();
        });

        final SwingWorker<T, Object> worker = new SwingWorker<T, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                task.setProgressHandler((progress, message) -> {
                    if (!isCancelled()) {
                        setProgress(progress);
                        workerMessages.put(this, message != null ? message : "");
                    }
                });

                // 在调用任务前检查是否已取消
                if (isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Task cancelled");
                }

                return task.call();
            }

            @Override
            protected void done() {
                activeWorkers.remove(this);
                workerMessages.remove(this);
                if (isCancelled()) {
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                    });
                    return;
                }
                try {
                    T result = get();
                    wrappedCallback.onComplete(result);
                } catch (java.util.concurrent.CancellationException e) {
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                    });
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    // 检测是否为取消操作引起的异常
                    if (cause instanceof RuntimeException && 
                        (cause.getMessage() != null && cause.getMessage().contains("已取消"))) {
                        // 用户取消操作，静默关闭对话框
                        SwingUtilities.invokeLater(() -> {
                            dialog.dispose();
                        });
                        return;
                    }
                    wrappedCallback.onError(cause instanceof Exception ? (Exception) cause : e);
                } catch (Exception e) {
                    wrappedCallback.onError(e);
                }
            }
        };

        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    int progress = (Integer) evt.getNewValue();
                    String message = workerMessages.getOrDefault(worker, "");
                    wrappedCallback.onProgress(progress, message);
                }
            }
        });

        if (callback != null) {
            callback.onStart();
        }
        workerRef[0] = worker;
        activeWorkers.add(worker);
        worker.execute();

        dialog.setVisible(true);
    }
}
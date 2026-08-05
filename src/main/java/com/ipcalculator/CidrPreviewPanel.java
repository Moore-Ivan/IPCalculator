package com.ipcalculator;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class CidrPreviewPanel extends JPanel {
    
    private JLabel networkLabel;
    private JLabel maskLabel;
    private JLabel wildcardLabel;
    private JLabel hostCountLabel;
    private JPanel previewArea;
    private Border normalBorder;
    private Border errorBorder;
    private JTextField boundTextField;
    private KeyAdapter boundKeyListener;
    private java.awt.event.FocusAdapter boundFocusListener;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 100;
    
    public CidrPreviewPanel() {
        setLayout(new BorderLayout());
        
        previewArea = new JPanel(new GridLayout(4, 2, 8, 4));
        previewArea.setBorder(BorderFactory.createTitledBorder("实时预览"));
        previewArea.setEnabled(false);
        previewArea.setOpaque(true);
        
        normalBorder = previewArea.getBorder();
        errorBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(220, 53, 69), 2), 
            "实时预览", 
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            null,
            new Color(220, 53, 69)
        );
        
        networkLabel = new JLabel("---");
        maskLabel = new JLabel("---");
        wildcardLabel = new JLabel("---");
        hostCountLabel = new JLabel("---");
        
        previewArea.add(new JLabel("网络地址:"));
        previewArea.add(networkLabel);
        previewArea.add(new JLabel("子网掩码:"));
        previewArea.add(maskLabel);
        previewArea.add(new JLabel("反掩码:"));
        previewArea.add(wildcardLabel);
        previewArea.add(new JLabel("可用主机数:"));
        previewArea.add(hostCountLabel);
        
        add(previewArea, BorderLayout.CENTER);
        
        debounceTimer = new Timer(DEBOUNCE_DELAY, e -> {
            if (boundTextField != null) {
                updatePreview(boundTextField.getText());
            }
        });
        debounceTimer.setRepeats(false);
    }
    
    public void bindToTextField(JTextField textField) {
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.removeKeyListener(boundKeyListener);
            boundTextField.removeFocusListener(boundFocusListener);
        }
        
        this.boundTextField = textField;
        
        boundKeyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                debounceTimer.restart();
            }
        };
        boundFocusListener = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                debounceTimer.stop();
                updatePreview(textField.getText());
            }
        };
        
        textField.addKeyListener(boundKeyListener);
        textField.addFocusListener(boundFocusListener);
    }
    
    private void updatePreview(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            clearPreview();
            return;
        }
        
        try {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                showError("格式错误，应为 x.x.x.x/yy");
                return;
            }
            
            String ip = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            
            if (prefix < 0 || prefix > 32) {
                showError("前缀必须在 0-32 之间");
                return;
            }
            
            long ipLong = SubnetCalculator.ipToLong(ip);
            long network = SubnetCalculator.getNetworkAddress(ipLong, prefix);
            long mask = SubnetCalculator.getSubnetMask(prefix);
            long wildcard = SubnetCalculator.getWildcardMask(prefix);
            long hosts = SubnetCalculator.getUsableHostCount(prefix);
            
            networkLabel.setText(SubnetCalculator.longToIp(network) + "/" + prefix);
            maskLabel.setText(SubnetCalculator.longToIp(mask));
            wildcardLabel.setText(SubnetCalculator.longToIp(wildcard));
            hostCountLabel.setText(String.valueOf(hosts));
            
            previewArea.setBorder(normalBorder);
            previewArea.setBackground(UIManager.getColor("Panel.background"));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }
    
    private void showError(String message) {
        networkLabel.setText("<html><span style='color:red;'>" + message + "</span></html>");
        maskLabel.setText("---");
        wildcardLabel.setText("---");
        hostCountLabel.setText("---");
        previewArea.setBorder(errorBorder);
        previewArea.setBackground(UIManager.getColor("OptionPane.errorBackground")
                != null ? UIManager.getColor("OptionPane.errorBackground") : new Color(255, 240, 240));
    }
    
    public void refresh() {
        if (boundTextField != null) {
            debounceTimer.stop();
            updatePreview(boundTextField.getText());
        }
    }

    public void clearPreview() {
        networkLabel.setText("---");
        maskLabel.setText("---");
        wildcardLabel.setText("---");
        hostCountLabel.setText("---");
        previewArea.setBorder(normalBorder);
        previewArea.setBackground(UIManager.getColor("Panel.background"));
    }
    
    @Override
    public void addNotify() {
        super.addNotify();
        // 重新绑定监听器（当面板重新显示时）
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.addKeyListener(boundKeyListener);
            boundTextField.addFocusListener(boundFocusListener);
        }
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        if (debounceTimer != null) {
            debounceTimer.stop();
            debounceTimer = null;
        }
        // 清理绑定的监听器，防止内存泄漏
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.removeKeyListener(boundKeyListener);
            boundTextField.removeFocusListener(boundFocusListener);
        }
    }
}

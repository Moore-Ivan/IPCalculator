package com.ipcalculator;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigInteger;

/**
 * IPv6 实时预览面板，显示地址详情
 */
public class IPv6PreviewPanel extends JPanel {

    private JLabel networkLabel;
    private JLabel prefixLabel;
    private JLabel maskLabel;
    private JLabel firstAddrLabel;
    private JLabel lastAddrLabel;
    private JLabel hostCountLabel;
    private JLabel addrTypeLabel;
    private JLabel anycastTypeLabel;
    private JPanel previewArea;
    private Border normalBorder;
    private Border errorBorder;
    private JTextField boundTextField;
    private KeyAdapter boundKeyListener;
    private java.awt.event.FocusAdapter boundFocusListener;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 150;

    public IPv6PreviewPanel() {
        setLayout(new BorderLayout());

        previewArea = new JPanel(new GridLayout(6, 2, 8, 4));
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
        prefixLabel = new JLabel("---");
        maskLabel = new JLabel("---");
        firstAddrLabel = new JLabel("---");
        lastAddrLabel = new JLabel("---");
        hostCountLabel = new JLabel("---");
        addrTypeLabel = new JLabel("---");
        anycastTypeLabel = new JLabel("---");

        previewArea.add(new JLabel("网络地址:"));
        previewArea.add(networkLabel);
        previewArea.add(new JLabel("前缀长度:"));
        previewArea.add(prefixLabel);
        previewArea.add(new JLabel("子网掩码:"));
        previewArea.add(maskLabel);
        previewArea.add(new JLabel("首个可用地址:"));
        previewArea.add(firstAddrLabel);
        previewArea.add(new JLabel("最后可用地址:"));
        previewArea.add(lastAddrLabel);
        previewArea.add(new JLabel("可用主机数:"));
        previewArea.add(hostCountLabel);
        previewArea.add(new JLabel("地址类型:"));
        previewArea.add(addrTypeLabel);
        previewArea.add(new JLabel("任播类型:"));
        previewArea.add(anycastTypeLabel);

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
                showError("格式错误，应为 x:x:x:x:x:x:x:x/yy");
                return;
            }

            String ip = parts[0];
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                showError("前缀必须是数字");
                return;
            }

            if (prefix < 0 || prefix > 128) {
                showError("前缀必须在 0-128 之间");
                return;
            }

            BigInteger addr = IPv6Calculator.parseIPv6(ip);
            BigInteger network = IPv6Calculator.getNetworkAddress(addr, prefix);
            BigInteger mask = IPv6Calculator.getSubnetMask(prefix);
            BigInteger last = IPv6Calculator.getLastAddress(addr, prefix);
            BigInteger hosts = IPv6Calculator.getUsableHostCount(prefix);
            String addrType = IPv6Calculator.getAddressType(addr);
            String anycastType = IPv6Calculator.getAnycastType(addr, prefix);

            networkLabel.setText(IPv6Calculator.formatCompressed(network) + "/" + prefix);
            prefixLabel.setText("/" + prefix);
            maskLabel.setText(truncateMiddle(IPv6Calculator.formatFull(mask), 30));
            firstAddrLabel.setText(prefix < 128 ? IPv6Calculator.formatCompressed(network.add(BigInteger.ONE)) : IPv6Calculator.formatCompressed(network));
            lastAddrLabel.setText(prefix < 128 ? IPv6Calculator.formatCompressed(last) : "N/A");
            hostCountLabel.setText(truncateNumber(hosts));
            addrTypeLabel.setText(addrType);
            anycastTypeLabel.setText(anycastType != null ? anycastType : "---");

            previewArea.setBorder(normalBorder);
            previewArea.setBackground(UIManager.getColor("Panel.background"));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private String truncateMiddle(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        int half = (maxLen - 3) / 2;
        return text.substring(0, half) + "..." + text.substring(text.length() - half);
    }

    private String truncateNumber(BigInteger num) {
        String s = num.toString();
        if (s.length() <= 12) return s;
        // 对于大数字，使用科学计数法（保留6位有效数字），避免 doubleValue() 丢失精度
        String mantissa = s.substring(0, 6);
        String formatted = mantissa.charAt(0) + "." + mantissa.substring(1);
        return formatted + "×10^" + (s.length() - 1);
    }

    private void showError(String message) {
        networkLabel.setText("<html><span style='color:red;'>" + message + "</span></html>");
        prefixLabel.setText("---");
        maskLabel.setText("---");
        firstAddrLabel.setText("---");
        lastAddrLabel.setText("---");
        hostCountLabel.setText("---");
        addrTypeLabel.setText("---");
        anycastTypeLabel.setText("---");
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
        prefixLabel.setText("---");
        maskLabel.setText("---");
        firstAddrLabel.setText("---");
        lastAddrLabel.setText("---");
        hostCountLabel.setText("---");
        addrTypeLabel.setText("---");
        anycastTypeLabel.setText("---");
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
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.removeKeyListener(boundKeyListener);
            boundTextField.removeFocusListener(boundFocusListener);
        }
    }
}

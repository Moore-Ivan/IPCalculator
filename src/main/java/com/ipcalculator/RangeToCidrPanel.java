package com.ipcalculator;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RangeToCidrPanel extends BaseOperationPanel {
    public interface TableResultCallback {
        void onResult(List<SubnetTablePanel.SubnetRow> result);
    }

    private final TableResultCallback tableResultCallback;

    public RangeToCidrPanel(TableResultCallback tableResultCallback, Consumer<String> statusCallback) {
        super(statusCallback);
        this.tableResultCallback = tableResultCallback;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("IP 范围 → CIDR 列表");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtStart = new HistoryComboBox("ip_start_range", 200);
        HistoryComboBox txtEnd = new HistoryComboBox("ip_end_range", 200);
        JButton btnConvert = new JButton("转换");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator startError = new ErrorIndicator();
        ErrorIndicator endError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("起始 IP:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtStart, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(startError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("结束 IP:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtEnd, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(endError, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnConvert);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtStart.setText("192.168.1.0");
            txtStart.save();
            txtEnd.setText("192.168.1.255");
            txtEnd.save();
        });

        clearBtn.addActionListener(e -> {
            txtStart.setText("");
            txtEnd.setText("");
            clearErrors(startError, endError);
        });

        btnConvert.addActionListener(e -> {
            txtStart.save();
            txtEnd.save();
            String startIp = txtStart.getText().trim();
            String endIp = txtEnd.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在转换为 CIDR...",
                new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                        CidrValidator.validateIpRange(startIp, endIp);
                        List<String> cidrs = SubnetCalculator.ipRangeToCidr(startIp, endIp);
                        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
                        for (String cidr : cidrs) {
                            String[] p = cidr.split("/");
                            int prefix = Integer.parseInt(p[1]);
                            long network = SubnetCalculator.getNetworkAddress(
                                    SubnetCalculator.ipToLong(p[0]), prefix);
                            long broadcast = SubnetCalculator.getBroadcastAddress(network, prefix);
                            long mask = SubnetCalculator.getSubnetMask(prefix);
                            rows.add(new SubnetTablePanel.SubnetRow(
                                    cidr,
                                    prefix,
                                    String.valueOf(SubnetCalculator.getUsableHostCount(prefix)),
                                    SubnetCalculator.longToIp(mask),
                                    SubnetCalculator.longToIp(broadcast)
                            ));
                        }
                        return rows;
                    }
                },
                new CalculationWorker.CalculationCallback<List<SubnetTablePanel.SubnetRow>>() {
                    @Override
                    public void onStart() {
                        tableResultCallback.onResult(java.util.Collections.emptyList());
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(List<SubnetTablePanel.SubnetRow> result) {
                        tableResultCallback.onResult(result);
                        clearErrors(startError, endError);
                    }
                    @Override
                    public void onError(Exception ex) {
                        handleError(ex, startError, txtStart);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtEnd.getEditor().getEditorComponent(), btnConvert);
    }
}
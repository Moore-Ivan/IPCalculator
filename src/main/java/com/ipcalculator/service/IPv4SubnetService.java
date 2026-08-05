package com.ipcalculator.service;

import com.ipcalculator.SubnetCalculator;
import com.ipcalculator.SubnetTablePanel;
import com.ipcalculator.CidrValidator;
import com.ipcalculator.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class IPv4SubnetService {

    public List<SubnetTablePanel.SubnetRow> subnetByCount(String majorCidr, int count) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateSubnetCount(String.valueOf(count));
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.subnetByCount(majorCidr, count);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> subnetByHosts(String majorCidr, int hosts) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateHostCount(String.valueOf(hosts));
        SubnetCalculator.SubnetByHostsResult result = SubnetCalculator.subnetByHosts(majorCidr, hosts);
        return toRows(result.subnets);
    }

    /**
     * 按每子网所需主机数划分，返回完整结果（含截断信息）
     */
    public SubnetByHostsResultEx subnetByHostsWithInfo(String majorCidr, int hosts) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateHostCount(String.valueOf(hosts));
        SubnetCalculator.SubnetByHostsResult result = SubnetCalculator.subnetByHosts(majorCidr, hosts);
        return new SubnetByHostsResultEx(toRows(result.subnets), result.totalCount, result.truncated, result.prefix);
    }

    /**
     * 子网划分结果扩展类（含截断信息）
     */
    public static class SubnetByHostsResultEx {
        public final List<SubnetTablePanel.SubnetRow> rows;
        public final long totalCount;
        public final boolean truncated;
        public final int prefix;

        public SubnetByHostsResultEx(List<SubnetTablePanel.SubnetRow> rows, long totalCount, boolean truncated, int prefix) {
            this.rows = rows;
            this.totalCount = totalCount;
            this.truncated = truncated;
            this.prefix = prefix;
        }
    }

    public List<SubnetTablePanel.SubnetRow> vlsm(String majorCidr, int[] hosts) {
        CidrValidator.validateCidr(majorCidr);
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.vlsm(majorCidr, hosts);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> splitSupernet(String cidr, int newPrefix) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.splitSupernet(cidr, newPrefix);
        return toRows(subnets);
    }

    public long getSplitSupernetCount(String cidr, int newPrefix) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        return SubnetCalculator.getSubnetCount(cidr, newPrefix);
    }

    public List<SubnetTablePanel.SubnetRow> splitSupernetPaged(String cidr, int newPrefix, int pageNum, int pageSize) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        if (pageNum < 1) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 10000) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "每页大小必须在 1-10000 之间");
        }
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.splitSupernetPaged(cidr, newPrefix, pageNum, pageSize);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> planNetwork(String majorCidr, int deptCount, int hostsPerDept) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateDeptCount(String.valueOf(deptCount));
        CidrValidator.validateHostCount(String.valueOf(hostsPerDept));
        int[] hostRequirements = new int[deptCount];
        for (int i = 0; i < deptCount; i++) {
            hostRequirements[i] = hostsPerDept;
        }
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.vlsm(majorCidr, hostRequirements);
        return toRows(subnets);
    }

    public String summarize(String... cidrs) {
        if (cidrs.length == 0) {
            throw new ValidationError(ValidationError.Field.CIDR, "请至少输入一个 CIDR");
        }
        return SubnetCalculator.summarize(cidrs);
    }

    public String getSubnetDetails(String cidr) {
        CidrValidator.validateCidr(cidr);
        return SubnetCalculator.getSubnetDetails(cidr);
    }

    public String parseIPDetails(String ip) {
        String trimmed = ip.trim();
        if (trimmed.contains("/")) {
            trimmed = trimmed.split("/")[0];
        }
        CidrValidator.validateIp(trimmed);
        return SubnetCalculator.parseIPDetails(trimmed);
    }

    public String detectOverlaps(String... cidrs) {
        if (cidrs.length < 2) {
            throw new ValidationError(ValidationError.Field.CIDR, "请至少输入两个 CIDR");
        }
        return SubnetCalculator.detectOverlaps(cidrs);
    }

    public List<SubnetTablePanel.SubnetRow> ipRangeToCidr(String startIp, String endIp) {
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

    private List<SubnetTablePanel.SubnetRow> toRows(List<SubnetCalculator.AddressBlock> blocks) {
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (SubnetCalculator.AddressBlock b : blocks) {
            rows.add(new SubnetTablePanel.SubnetRow(
                    b.toString(),
                    b.prefix,
                    String.valueOf(b.usableHosts()),
                    SubnetCalculator.longToIp(b.mask),
                    SubnetCalculator.longToIp(b.broadcast)
            ));
        }
        return rows;
    }
}

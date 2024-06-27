package org.lgdcloudsim.interscheduler.SegmentTree;

public class DcInfo implements Comparable<DcInfo> {
    public Integer dcId;
    public double cost;
    public double delay;
    public double bw;

    public DcInfo(Integer dcId, double cost, double delay) {
        this.dcId = dcId;
        this.cost = cost;
        this.delay = delay;
    }

    public DcInfo(Integer dcId, double cost, double delay, double bw) {
        this.dcId = dcId;
        this.cost = cost;
        this.delay = delay;
        this.bw = bw;
    }

    public DcInfo(DcInfo dc) {
        this.dcId = dc.dcId;
        this.cost = dc.cost;
        this.delay = dc.delay;
        this.bw = dc.bw;
    }

    // 重载排序算法，先根据delay从小到大排序，再根据cost从小到大排序，再根据bw从大到小排序
    public int compareTo(DcInfo dc) {
        if (this.delay == dc.delay) {
            if(this.cost == dc.cost) {
                return this.bw > dc.bw ? -1 : 1;
            } else {
                return this.cost < dc.cost ? -1 : 1;
            }
        } else {
            return this.delay < dc.delay ? -1 : 1;
        }
    }
}

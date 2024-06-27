package org.lgdcloudsim.interscheduler.SegmentTree;

import java.util.ArrayList;
import java.util.List;

public class Node {
    int left, right;    //左右区间的值

    double maxDelay;    //延迟
    double minBw;       //带宽
    double minDelay;    //延迟（用于减少遍历次数）
    double maxBw;       //带宽（用于减少遍历次数）
    List<DcInfo> minCost;   //成本
    
    Node leftChild;
    Node rightChild;

    Node(int left, int right) {
        this.left = left;
        this.right = right;

        maxDelay = Double.MAX_VALUE;
        minBw = 0;
        minDelay = 0;
        maxBw = Double.MAX_VALUE;
        minCost = null;
    }
}

package org.lgdcloudsim.interscheduler.SegmentTree;

import java.util.ArrayList;
import java.util.List;

public class SegmentTree {
    Node root;
    int MAX_NUM;

    List<DcInfo> dcList;

    public SegmentTree(int left, int right, List<DcInfo> dcList) {
        this.dcList = dcList;
        MAX_NUM = dcList.size();
        build(left, right);
    }

    public void build(int left, int right) {
        root = new Node(left, right);
        build(root);
    }

    private void pushUp(Node node) {
        node.maxDelay = Math.max(node.leftChild.maxDelay, node.rightChild.maxDelay);
        node.minBw = Math.min(node.leftChild.minBw, node.rightChild.minBw);
        if(node.minCost == null) {
            node.minCost = mergeMinCost(node.leftChild.minCost, node.rightChild.minCost);
        }
        // 用于减小遍历次数
        node.minDelay = Math.min(node.leftChild.minDelay, node.rightChild.minDelay);
        node.maxBw = Math.max(node.leftChild.maxBw, node.rightChild.maxBw);
    }

    private List<DcInfo>  mergeMinCost(List<DcInfo> leftMinCost, List<DcInfo> rightMinCost) {
        return mergeMinCost(leftMinCost, rightMinCost, MAX_NUM);
    }
    
    private List<DcInfo> mergeMinCost(List<DcInfo> leftMinCost, List<DcInfo> rightMinCost, int DC_NUM) {
        if ((leftMinCost == null || leftMinCost.isEmpty())
            && (rightMinCost == null || rightMinCost.isEmpty())) {
            return null;
        }

        List<DcInfo> minCost = new ArrayList<>();
        if (leftMinCost == null || leftMinCost.isEmpty()) {
            for (int i=0; i<DC_NUM && i<rightMinCost.size(); i++) {
                minCost.add(rightMinCost.get(i));
            }
            return minCost;
        }
        if (rightMinCost == null || rightMinCost.isEmpty()) {
            for (int i=0; i<DC_NUM && i<leftMinCost.size(); i++) {
                minCost.add(leftMinCost.get(i));
            }
            return minCost;
        }

        int pl=0, pr=0;
        for (int i=0; i<DC_NUM; i++) {
            if(pl<leftMinCost.size() && pr<rightMinCost.size()) {
                if(leftMinCost.get(pl).cost < rightMinCost.get(pr).cost) {
                    minCost.add(leftMinCost.get(pl));
                    pl++;
                } else {
                    minCost.add(rightMinCost.get(pr));
                    pr++;
                }
            } else if(pl<leftMinCost.size()) {
                minCost.add(leftMinCost.get(pl));
                pl++;
            } else if(pr<rightMinCost.size()) {
                minCost.add(rightMinCost.get(pr));
                pr++;
            } else {
                break;
            }
        }
        return minCost;
    }

    private void build(Node node) {
        if (node.left > node.right) {
            return;
        }
        if (node.left == node.right) {
            node.maxDelay = dcList.get(node.left-1).delay;
            node.minBw = dcList.get(node.left-1).bw;
            node.minCost = new ArrayList<>();
            node.minCost.add(new DcInfo(dcList.get(node.left-1)));
            // 用于减小遍历次数
            node.minDelay = node.maxDelay;
            node.maxBw = node.minBw;
            
            return;
        }
        int mid = (node.left + node.right) >> 1; // 取中间值
        node.leftChild = new Node(node.left, mid);
        node.rightChild = new Node(mid + 1, node.right);
        
        // 递归构建左右子树
        build(node.leftChild);
        build(node.rightChild);
        pushUp(node);
    }

    public List<DcInfo> query(double delay) {
        return query(delay, MAX_NUM);
    }

    public List<DcInfo> query(double delay, int DC_NUM) {
        return query(root, 1, dcList.size(), delay, DC_NUM);
    }

    public List<DcInfo> query(int left, int right, double delay) {
        return query(left, right, delay, MAX_NUM);
    }

    public List<DcInfo> query(int left, int right, double delay, int DC_NUM) {
        return query(root, left, right, delay, DC_NUM);
    }

    private List<DcInfo> query(Node node, int left, int right, double delay, int DC_NUM) {
        if (left <= node.left && right >= node.right) {
            if (node.maxDelay <= delay) {
                return node.minCost;
            } else if (node.minDelay > delay) {
                return null;
            }
        }
        if (node.left == node.right) {
            return null;
        }
        int mid = (node.left + node.right) >> 1;
        List<DcInfo> leftRes = null;
        List<DcInfo> rightRes = null;
        if (left <= mid) {
            leftRes = query(node.leftChild, left, right, delay, DC_NUM);
        }
        if (right > mid) {
            rightRes = query(node.rightChild, left, right, delay, DC_NUM);
        }
        List<DcInfo> res = mergeMinCost(leftRes, rightRes, DC_NUM);
        return res;
    }

    public List<DcInfo> queryWithBw(double delay, double bw) {
        return queryWithBw(delay, bw, MAX_NUM);
    }

    public List<DcInfo> queryWithBw(double delay, double bw, int DC_NUM) {
        return queryWithBw(root, 1, dcList.size(), delay, bw, DC_NUM);
    }

    public List<DcInfo> queryWithBw(int left, int right, double delay, double bw) {
        return queryWithBw(left, right, delay, bw, MAX_NUM);
    }

    public List<DcInfo> queryWithBw(int left, int right, double delay, double bw, int DC_NUM) {
        return queryWithBw(root, left, right, delay, bw, DC_NUM);
    }

    private List<DcInfo> queryWithBw(Node node, int left, int right, double delay, double bw, int DC_NUM) {
        if (left <= node.left && right >= node.right) {
            if (node.maxDelay <= delay && node.minBw >= bw) {
                return node.minCost;
            } else if(node.minDelay > delay || node.maxBw < bw) {
                return null;
            }
        }
        if (node.left == node.right) {
            return null;
        }
        int mid = (node.left + node.right) >> 1;
        List<DcInfo> leftRes = null;
        List<DcInfo> rightRes = null;
        if (left <= mid) {
            leftRes = queryWithBw(node.leftChild, left, right, delay, bw, DC_NUM);
        }
        if (right > mid) {
            rightRes = queryWithBw(node.rightChild, left, right, delay, bw, DC_NUM);
        }
        List<DcInfo> res = mergeMinCost(leftRes, rightRes, DC_NUM);
        return res;
    }

    public void Add(int index, double delta_bw) {
        Add(root, index, index, delta_bw);
    }

    private void Add(Node node, int left, int right, double delta_bw) {
        if(node.left == node.right) {
            node.minBw += delta_bw;
            return;
        }
        int mid = (node.left + node.right) >> 1;
        if (left <= mid) {
            Add(node.leftChild, left, right, delta_bw);
        }
        if (right > mid) {
            Add(node.rightChild, left, right, delta_bw);
        }
        pushUp(node);
    }

    public double getBw(int index) {
        return getBw(root, index);
    }

    private double getBw(Node node, int index) {
        if (node.left == node.right) {
            return node.minBw;
        }
        int mid = (node.left + node.right) >> 1;
        if (index <= mid) {
            return getBw(node.leftChild, index);
        } else {
            return getBw(node.rightChild, index);
        }
    }
}

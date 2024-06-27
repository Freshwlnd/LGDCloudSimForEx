package org.lgdcloudsim.interscheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.Datacenter;
import org.lgdcloudsim.interscheduler.SegmentTree.SegmentTree;
import org.lgdcloudsim.interscheduler.SegmentTree.DcInfo;
import org.lgdcloudsim.network.NetworkTopology;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.request.InstanceGroupEdge;
import org.lgdcloudsim.statemanager.SimpleStateEasyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

public class PMQSF {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PMQSF.class.getSimpleName());
    
    @Getter
    @Setter
    static private double cpuALPHA = 1.0;

    @Getter
    @Setter
    static private double memBETA = 0.5;

    Integer dcNum;

    Map<Integer,SegmentTree> segmentTrees;

    private int isOne(int num, int p) {
        return num>>p&1;
    }

    /*
     * 构造函数
     * @param allDatacenters 所有数据中心
     * @param simulation 仿真器
     */
    PMQSF(List<Datacenter> allDatacenters, Simulation simulation) {
        NetworkTopology networkTopology = simulation.getNetworkTopology();
        segmentTrees = new HashMap<>();
        dcNum = allDatacenters.size();

        Collections.sort(allDatacenters, (a, b) -> a.getId() - b.getId());
        for(int i=0; i<dcNum; i++) {
            if (allDatacenters.get(i).getId() != i+1) {
                LOGGER.error("Datacenter id is not continuous from 1 to n");
                LOGGER.error("the error {}-th Datacenter id is {}", i+1, allDatacenters.get(i).getId());
            }
        }

        // 生成二进制下长度为dcNum的整数，其中第i位为1表示对第i个数据中心有网络限制
        for (int pattern=0; pattern<(2<<dcNum); pattern++) {
            List<Integer> needCalculateDC = new ArrayList<>();
            for (int j=0; j<dcNum; j++) {
                if ( isOne(pattern, j) == 1 ) {
                    needCalculateDC.add(j);
                }
            }

            List<DcInfo> dcList = new ArrayList<>();
            for (Datacenter datacenter: allDatacenters) {
                Integer dcId = datacenter.getId();
                double cost = cpuALPHA * (datacenter.getPricePerCpu() + datacenter.getPricePerCpuPerSec()) + memBETA * (datacenter.getPricePerRam() + datacenter.getPricePerRamPerSec());
                double delay = 0;
                double bw = Double.MAX_VALUE;
                for (Integer j: needCalculateDC) {
                    delay = Math.max(delay, networkTopology.getDelay(datacenter, allDatacenters.get(j)));
                    bw = Math.min(bw, networkTopology.getBw(datacenter, allDatacenters.get(j)));
                }
                dcList.add(new DcInfo(dcId, cost, delay, bw));
            }
            Collections.sort(dcList);

            segmentTrees.put(pattern, new SegmentTree(1, dcNum, dcList));
        }
    }

    /*
     * 更新所有数据中心的带宽信息
     * @param datacenters 所有需更新带宽信息的数据中心
     * @param simulation 仿真器
     */
    public void updateBWInfo(List<Datacenter> datacenters, Simulation simulation) {
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        for (int pattern=0; pattern<(2<<dcNum); pattern++) {
            List<Integer> needCalculateDC = new ArrayList<>();
            for (int j=0; j<dcNum; j++) {
                if ( isOne(pattern, j) == 1 ) {
                    needCalculateDC.add(j);
                }
            }

            for (Datacenter datacenter: datacenters) {
                Integer dcId = datacenter.getId();
                double bw = Double.MAX_VALUE;
                for (Integer j: needCalculateDC) {
                    bw = Math.min(bw, networkTopology.getBw(dcId, j+1));
                }
                double rawBw = segmentTrees.get(pattern).getBw(dcId);
                segmentTrees.get(pattern).Add(dcId, bw-rawBw);
            }
        }
    }


    /*
     * 筛选符合需求的数据中心（输入为 InstanceGroup）
     * @param instanceGroup 需要筛选的实例组
     * @param allDatacenters 所有数据中心
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 简单的资源抽样信息
     * @return Map<InstanceGroup, List<Datacenter>> 符合需求的数据中心
     */
    public Map<InstanceGroup, List<Datacenter>> filtering(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap, InterSchedulerResult interSchedulerResult) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();

        NetworkTopology networkTopology = simulation.getNetworkTopology();
        List<Datacenter> availableDatacenters = new ArrayList<>();
    
        // Pair<Integer,Double> patternAndDelay = getPatternAndDelay(instanceGroup, allDatacenters, simulation);
        // int pattern = patternAndDelay.getLeft();
        // double delay = patternAndDelay.getRight();
        Triple<Integer,Double,Double> patternAndDelayAndBw = getPatternAndDelayAndBw(instanceGroup, allDatacenters, simulation, interSchedulerResult);
        int pattern = patternAndDelayAndBw.getLeft();
        double delay = patternAndDelayAndBw.getMiddle();
        double bw = patternAndDelayAndBw.getRight();
        
        List<DcInfo> dcList = segmentTrees.get(pattern).queryWithBw(delay,bw);

        if(dcList != null) {
            for (DcInfo dcInfo: dcList) {
                availableDatacenters.add(allDatacenters.get(dcInfo.dcId-1));
            }
        }
        //根据接入时延要求得到可调度的数据中心
        filterDatacentersByAccessLatency(instanceGroup, availableDatacenters, networkTopology);
        //根据简单的资源抽样信息得到可调度的数据中心
        filterDatacentersByResourceSample(instanceGroup, availableDatacenters, interScheduleSimpleStateMap);
        instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 筛选符合需求的数据中心（输入为 InstanceGroup 列表）
     * @param instanceGroups 需要筛选的实例组列表
     * @param allDatacenters 所有数据中心
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 简单的资源抽样信息
     * @return Map<InstanceGroup, List<Datacenter>> 符合需求的数据中心
     */
    public Map<InstanceGroup, List<Datacenter>> filtering(List<InstanceGroup> instanceGroups, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap, InterSchedulerResult interSchedulerResult) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
            
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        for (InstanceGroup instanceGroup : instanceGroups) {
            List<Datacenter> availableDatacenters = new ArrayList<>();
        
            // Pair<Integer,Double> patternAndDelay = getPatternAndDelay(instanceGroup, allDatacenters, simulation);
            // int pattern = patternAndDelay.getLeft();
            // double delay = patternAndDelay.getRight();
            Triple<Integer,Double,Double> patternAndDelayAndBw = getPatternAndDelayAndBw(instanceGroup, allDatacenters, simulation, interSchedulerResult);
            int pattern = patternAndDelayAndBw.getLeft();
            double delay = patternAndDelayAndBw.getMiddle();
            double bw = patternAndDelayAndBw.getRight();
            int queryDCNum = (int)(Math.random() * allDatacenters.size()) + 1;
            // List<DcInfo> dcList = segmentTrees.get(pattern).query(delay,queryDCNum);
            List<DcInfo> dcList = segmentTrees.get(pattern).queryWithBw(delay,bw,queryDCNum);
            for (DcInfo dcInfo: dcList) {
                availableDatacenters.add(allDatacenters.get(dcInfo.dcId));
            }
            instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
        
        }
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 获取复杂拓扑对应的模式和延迟约束
     * @param instanceGroup 需要获取模式和延迟约束的实例组
     * @param allDatacenters 所有数据中心
     * @param simulation 仿真器
     * @return Pair<Integer,Double> 模式和延迟约束
     */
    private Pair<Integer,Double> getPatternAndDelay(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Simulation simulation, InterSchedulerResult interSchedulerResult) {
        Set<Integer> needCalculateDC = new HashSet<>();
        int pattern = 0;
        double delay = 0;
        
        for (InstanceGroup dstInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getDstList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(dstInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                delay = Math.max(delay, instanceGroup.getUserRequest().getInstanceGroupGraph().getDelay(instanceGroup, dstInstanceGroup));
                needCalculateDC.add(scheduledDatacenter.getId());
            }
        }

        for (Integer dcId: needCalculateDC) {
            pattern += 1<<(dcId-1);
        }
        
        return Pair.of(pattern, delay);
    }

    /*
     * 获取复杂拓扑对应的模式和延迟约束和带宽需求
     * @param instanceGroup 需要获取模式和延迟约束和带宽需求的实例组
     * @param allDatacenters 所有数据中心
     * @param simulation 仿真器
     * @return Triple<Integer,Double,Double> 模式和延迟约束和带宽需求
     */
    private Triple<Integer,Double,Double> getPatternAndDelayAndBw(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Simulation simulation, InterSchedulerResult interSchedulerResult) {
        Set<Integer> needCalculateDC = new HashSet<>();
        int pattern = 0;
        double delay = Double.MAX_VALUE;
        double bw = 0;
        
        // 获取各实例组所在数据中心情况、到各个数据中心的最严格时延与带宽需求约束
        for (InstanceGroup dstInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getDstList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(dstInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                delay = Math.min(delay, instanceGroup.getUserRequest().getInstanceGroupGraph().getDelay(instanceGroup, dstInstanceGroup));
                bw = Math.max(bw, instanceGroup.getUserRequest().getInstanceGroupGraph().getBw(instanceGroup, dstInstanceGroup));
                needCalculateDC.add(scheduledDatacenter.getId());
            }
        }

        for (Integer dcId: needCalculateDC) {
            pattern += 1<<(dcId-1);
        }
        
        return Triple.of(pattern, delay, bw);
    }

    /*
     * 根据接入时延要求得到可调度的数据中心
     * @param instanceGroup 需要筛选的实例组
     * @param availableDatacenters 可调度的数据中心
     * @param networkTopology 网络拓扑
     */
    private static void filterDatacentersByAccessLatency(InstanceGroup instanceGroup, List<Datacenter> availableDatacenters, NetworkTopology networkTopology) {
        // Filter based on access latency
        availableDatacenters.removeIf(
                datacenter -> instanceGroup.getAccessLatency() < networkTopology.getAccessLatency(instanceGroup.getUserRequest(), datacenter));
    }

    /*
     * 根据简单的资源抽样信息得到可调度的数据中心
     * @param instanceGroup 需要筛选的实例组
     * @param availableDatacenters 可调度的数据中心
     * @param interScheduleSimpleStateMap 简单的资源抽样信息
     */
    private static void filterDatacentersByResourceSample(InstanceGroup instanceGroup, List<Datacenter> availableDatacenters, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        //粗粒度地筛选总量是否满足
        availableDatacenters.removeIf(
                datacenter -> {
                    SimpleStateEasyObject simpleStateEasyObject = (SimpleStateEasyObject) interScheduleSimpleStateMap.get(datacenter);
                    return simpleStateEasyObject.getCpuAvailableSum() < instanceGroup.getCpuSum()
                            || simpleStateEasyObject.getRamAvailableSum() < instanceGroup.getRamSum()
                            || simpleStateEasyObject.getStorageAvailableSum() < instanceGroup.getStorageSum()
                            || simpleStateEasyObject.getBwAvailableSum() < instanceGroup.getBwSum();
                }
        );
    }
    
    /**
     * Get the possible scheduled data center from the previous scheduling result.
     * @param instanceGroup the instance group.
     * @param interSchedulerResult the result of the scheduling.
     * @return the possible scheduled data center.
     */
    private static Datacenter getPossibleScheduledDatacenter(InstanceGroup instanceGroup, InterSchedulerResult interSchedulerResult) {
        if (instanceGroup.getReceiveDatacenter() != Datacenter.NULL) {
            return instanceGroup.getReceiveDatacenter();
        } else {
            return interSchedulerResult.getScheduledDatacenter(instanceGroup);
        }
    }

}

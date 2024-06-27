package org.lgdcloudsim.interscheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.util.Pair;
import org.lgdcloudsim.core.CloudInformationService;
import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.Datacenter;
import org.lgdcloudsim.network.NetworkTopology;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.statemanager.SimpleStateEasyObject;
import org.lgdcloudsim.statemanager.SimpleStateWithCompeteEasyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

public class RandomAndHeuristicAlgorithm {

    @Getter
    @Setter
    static private double cpuALPHA = 1.0;

    @Getter
    @Setter
    static private double memBETA = 0.5;

    @Getter
    @Setter
    static private double competeGAMMA = 0.1;

    // 确保每一ms运行的两次代码不会相同
    private static Random random = new Random();

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomAndHeuristicAlgorithm.class.getSimpleName());


    public RandomAndHeuristicAlgorithm() {
    }

    /*
     * 随机筛选出RandomRate比例的数据中心
     * @param instanceGroup 需要调度的InstanceGroup
     * @param allDatacenters 所有数据中心列表
     * @param RandomRate 随机筛选比例
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 随机筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> randomFiltering(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Double RandomRate) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
        // 获取最终随机筛选出的数据中心数量，最小为1
        Integer RandomNum = (int) Math.max(Math.ceil(RandomRate * (double)allDatacenters.size()), 1);
            
        List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);

        // 对availableDatacenters列表进行洗牌（随机排序）
        Collections.shuffle(availableDatacenters, random);

        // 随机筛选出RandomNum个数据中心
        availableDatacenters = availableDatacenters.subList(0, RandomNum);

        instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 随机筛选出RandomRate比例的数据中心
     * @param instanceGroups 需要调度的InstanceGroup列表
     * @param allDatacenters 所有数据中心列表
     * @param RandomRate 随机筛选比例
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 随机筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> randomFiltering(List<InstanceGroup> instanceGroups, List<Datacenter> allDatacenters, Double RandomRate) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
        // 获取最终随机筛选出的数据中心数量，最小为1
        Integer RandomNum = (int) Math.max(Math.ceil(RandomRate * (double)allDatacenters.size()), 1);
            
        for (InstanceGroup instanceGroup : instanceGroups) {
            List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);

            // 对availableDatacenters列表进行洗牌（随机排序）
            Collections.shuffle(availableDatacenters, random);

            // 随机筛选出RandomNum个数据中心
            availableDatacenters = availableDatacenters.subList(0, RandomNum);

            instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
            
        }
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 启发式筛选数据中心
     * @param instanceGroup 需要调度的InstanceGroup
     * @param allDatacenters 所有数据中心列表
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 数据中心状态信息
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 启发式筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> heuristicFiltering(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap, InterSchedulerResult interSchedulerResult) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
            
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);
    
        //根据带宽时延要求得到可调度的数据中心
        filterAvailableDatacenterByEdgeDelayLimit(instanceGroup, availableDatacenters, networkTopology, interSchedulerResult);
        filterAvailableDatacenterByEdgeBwLimit(instanceGroup, availableDatacenters, networkTopology, interSchedulerResult);
        //根据接入时延要求得到可调度的数据中心
        filterDatacentersByAccessLatency(instanceGroup, availableDatacenters, networkTopology);
        //根据简单的资源抽样信息得到可调度的数据中心
        filterDatacentersByResourceSample(instanceGroup, availableDatacenters, interScheduleSimpleStateMap);
        instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 启发式筛选数据中心
     * @param instanceGroups 需要调度的InstanceGroup列表
     * @param allDatacenters 所有数据中心列表
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 数据中心状态信息
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 启发式筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> heuristicFiltering(List<InstanceGroup> instanceGroups, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap, InterSchedulerResult interSchedulerResult) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
            
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        for (InstanceGroup instanceGroup : instanceGroups) {
            List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);
        
            //根据带宽时延要求得到可调度的数据中心
            filterAvailableDatacenterByEdgeDelayLimit(instanceGroup, availableDatacenters, networkTopology, interSchedulerResult);
            filterAvailableDatacenterByEdgeBwLimit(instanceGroup, availableDatacenters, networkTopology, interSchedulerResult);
            //根据接入时延要求得到可调度的数据中心
            filterDatacentersByAccessLatency(instanceGroup, availableDatacenters, networkTopology);
            //根据简单的资源抽样信息得到可调度的数据中心
            filterDatacentersByResourceSample(instanceGroup, availableDatacenters, interScheduleSimpleStateMap);
            instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);

        }
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 延迟优先筛选数据中心
     * @param instanceGroup 需要调度的InstanceGroup
     * @param allDatacenters 所有数据中心列表
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 数据中心状态信息
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 延迟优先筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> delayFirstFiltering(InstanceGroup instanceGroup, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
            
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);
    
        // queryDCNum 在1到allDatacenters.size()之间随机非零整数
        Integer queryDCNum = (int)(Math.random() * allDatacenters.size()) + 1;
        // 对availableDatacenters列表进行洗牌（随机排序）
        Collections.shuffle(availableDatacenters, random);

        // 随机筛选出queryDCNum个数据中心
        availableDatacenters = availableDatacenters.subList(0, queryDCNum);

        //根据接入时延要求得到可调度的数据中心
        filterDatacentersByAccessLatency(instanceGroup, availableDatacenters, networkTopology);
        //根据简单的资源抽样信息得到可调度的数据中心
        filterDatacentersByResourceSample(instanceGroup, availableDatacenters, interScheduleSimpleStateMap);
        //根据带宽时延要求得到可调度的数据中心
        instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);
        
        return instanceGroupAvailableDatacenters;
    }

    /*
     * 延迟优先筛选数据中心
     * @param instanceGroups 需要调度的InstanceGroup列表
     * @param allDatacenters 所有数据中心列表
     * @param simulation 仿真器
     * @param interScheduleSimpleStateMap 数据中心状态信息
     * @return Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters 延迟优先筛选出的数据中心
     */
    public static Map<InstanceGroup, List<Datacenter>> delayFirstFiltering(List<InstanceGroup> instanceGroups, List<Datacenter> allDatacenters, Simulation simulation, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();
            
        NetworkTopology networkTopology = simulation.getNetworkTopology();

        for (InstanceGroup instanceGroup : instanceGroups) {
            List<Datacenter> availableDatacenters = new ArrayList<>(allDatacenters);
        
            // queryDCNum 在1到allDatacenters.size()之间随机非零整数
            Integer queryDCNum = (int)(Math.random() * allDatacenters.size()) + 1;
            // 对availableDatacenters列表进行洗牌（随机排序）
            Collections.shuffle(availableDatacenters, random);

            // 随机筛选出queryDCNum个数据中心
            availableDatacenters = availableDatacenters.subList(0, queryDCNum);

            //根据接入时延要求得到可调度的数据中心
            filterDatacentersByAccessLatency(instanceGroup, availableDatacenters, networkTopology);
            //根据简单的资源抽样信息得到可调度的数据中心
            filterDatacentersByResourceSample(instanceGroup, availableDatacenters, interScheduleSimpleStateMap);
            instanceGroupAvailableDatacenters.put(instanceGroup, availableDatacenters);

        }
        
        return instanceGroupAvailableDatacenters;
    }

    
    private static void filterDatacentersByAccessLatency(InstanceGroup instanceGroup, List<Datacenter> availableDatacenters, NetworkTopology networkTopology) {
        // Filter based on access latency
        availableDatacenters.removeIf(
                datacenter -> instanceGroup.getAccessLatency() < networkTopology.getAccessLatency(instanceGroup.getUserRequest(), datacenter));
    }

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

    private static void filterAvailableDatacenterByEdgeDelayLimit(InstanceGroup instanceGroup, List<Datacenter> availableDatacenters, NetworkTopology networkTopology, InterSchedulerResult interSchedulerResult) {
        // // 获取所有与当前instanceGroup有Edge的instanceGroup
        for (InstanceGroup dstInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getDstList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(dstInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                availableDatacenters.removeIf(dc -> networkTopology.getDelay(dc, scheduledDatacenter) > instanceGroup.getUserRequest().getInstanceGroupGraph().getDelay(instanceGroup, dstInstanceGroup));
            }
        }
        for (InstanceGroup srcInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getSrcList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(srcInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                availableDatacenters.removeIf(dc -> networkTopology.getDelay(scheduledDatacenter, dc) > instanceGroup.getUserRequest().getInstanceGroupGraph().getDelay(srcInstanceGroup, instanceGroup));
            }
        }
    }

    private static void filterAvailableDatacenterByEdgeBwLimit(InstanceGroup instanceGroup, List<Datacenter> availableDatacenters, NetworkTopology networkTopology, InterSchedulerResult interSchedulerResult) {
        // 获取所有与当前instanceGroup有Edge的instanceGroup
        for (InstanceGroup dstInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getDstList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(dstInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                availableDatacenters.removeIf(dc -> networkTopology.getBw(dc, scheduledDatacenter) < instanceGroup.getUserRequest().getInstanceGroupGraph().getBw(instanceGroup, dstInstanceGroup));
            }
        }
        for (InstanceGroup srcInstanceGroup : instanceGroup.getUserRequest().getInstanceGroupGraph().getSrcList(instanceGroup)) {
            Datacenter scheduledDatacenter = getPossibleScheduledDatacenter(srcInstanceGroup, interSchedulerResult);
            if (scheduledDatacenter != Datacenter.NULL && !scheduledDatacenter.isFailureCluster()) {
                availableDatacenters.removeIf(dc -> networkTopology.getBw(scheduledDatacenter, dc) < instanceGroup.getUserRequest().getInstanceGroupGraph().getBw(srcInstanceGroup, instanceGroup));
            }
        }
    }

    // RandomAndHeuristicAlgorithm.randomScoring(interSchedulerResult, instanceGroupAvailableDatacenters);
    public static void randomScoring(InterSchedulerResult interSchedulerResult, Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        for (Map.Entry<InstanceGroup, List<Datacenter>> scheduleRes : instanceGroupAvailableDatacenters.entrySet()) {
            if (scheduleRes.getValue().size() == 0) {
                interSchedulerResult.getFailedInstanceGroups().add(scheduleRes.getKey());
            } else {
                Datacenter target = scheduleRes.getValue().get(random.nextInt(scheduleRes.getValue().size()));
                interSchedulerResult.addDcResult(scheduleRes.getKey(), target);

                // 更新统计信息
                updateStateMap(interScheduleSimpleStateMap, target, scheduleRes.getKey());
            }
        }
    }

    public static void heuristicScoring(InterSchedulerResult interSchedulerResult, Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        for (Map.Entry<InstanceGroup, List<Datacenter>> scheduleRes : instanceGroupAvailableDatacenters.entrySet()) {
            if (scheduleRes.getValue().size() == 0) {
                interSchedulerResult.getFailedInstanceGroups().add(scheduleRes.getKey());
            } else {
                // 对数据中心排序
                Collections.sort(scheduleRes.getValue(), (d1, d2) -> {
                    double scoreD1 = getScoreForDc(scheduleRes.getKey(), d1, (SimpleStateEasyObject)interScheduleSimpleStateMap.get(d1));
                    double scoreD2 = getScoreForDc(scheduleRes.getKey(), d2, (SimpleStateEasyObject)interScheduleSimpleStateMap.get(d2));
    
                    // 按降序排序
                    return -Double.compare(scoreD1, scoreD2);
                });

                Datacenter target = scheduleRes.getValue().get(0);
                interSchedulerResult.addDcResult(scheduleRes.getKey(), target);

                // 更新统计信息
                updateStateMap(interScheduleSimpleStateMap, target, scheduleRes.getKey());
            }
        }
    }


    public static void heuristicScoringByPriceAndAvailable(InterSchedulerResult interSchedulerResult, Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        for (Map.Entry<InstanceGroup, List<Datacenter>> scheduleRes : instanceGroupAvailableDatacenters.entrySet()) {
            if (scheduleRes.getValue().size() == 0) {
                interSchedulerResult.getFailedInstanceGroups().add(scheduleRes.getKey());
            } else {
                Map<Datacenter, Pair<Double,Double> > scoreList = new HashMap<>();
                for(Datacenter datacenter : scheduleRes.getValue()) {
                    Double score_available = getAvailableScoreForDc(scheduleRes.getKey(), datacenter, (SimpleStateEasyObject)interScheduleSimpleStateMap.get(datacenter));
                    Double score_price = Double.MAX_VALUE;
                    if (score_available>=0) {    // 为-1时表示资源不足，使其价格得分为最大值；否则正常计算价格得分
                        score_price = getPriceScoreForDc(scheduleRes.getKey(), datacenter, (SimpleStateEasyObject)interScheduleSimpleStateMap.get(datacenter));
                    }
                    scoreList.put(datacenter ,new Pair<>(score_price, score_available));
                }
                // 对数据中心排序
                Collections.sort(scheduleRes.getValue(), (d1, d2) -> {
                    double scoreD1_price = scoreList.get(d1).getFirst();
                    double scoreD1_available = scoreList.get(d1).getSecond();
                    double scoreD2_price = scoreList.get(d2).getFirst();
                    double scoreD2_available = scoreList.get(d2).getSecond();
                    
                    // 按降序排序
                    if(Math.abs(scoreD1_price -  scoreD2_price) < 1e-6) {
                        // 若 scoreD1_price == scoreD2_price，则看 score_available
                        // 若 scoreD1_available > scoreD2_available，则 d1 排在前面
                        return -Double.compare(scoreD1_available, scoreD2_available);
                    } else {
                        // 若 scoreD1_price < scoreD2_price，则 d1 排在前面
                        return Double.compare(scoreD1_price, scoreD2_price);
                    }
                });

                Datacenter target = scheduleRes.getValue().get(0);
                interSchedulerResult.addDcResult(scheduleRes.getKey(), target);

                // 更新统计信息
                updateStateMap(interScheduleSimpleStateMap, target, scheduleRes.getKey());
            }
        }
    }


    public static void heuristicScoringByPriceAndAvailableAndCompete(InterSchedulerResult interSchedulerResult, Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters, Map<Datacenter, Object> interScheduleSimpleStateMap) {
        for (Map.Entry<InstanceGroup, List<Datacenter>> scheduleRes : instanceGroupAvailableDatacenters.entrySet()) {
            if (scheduleRes.getValue().size() == 0) {
                interSchedulerResult.getFailedInstanceGroups().add(scheduleRes.getKey());
            } else {
                Map<Datacenter, Pair<Double,Double> > scoreList = new HashMap<>();
                for(Datacenter datacenter : scheduleRes.getValue()) {
                    Double score_available = getAvailableScoreWithCompeteForDc(scheduleRes.getKey(), datacenter, (SimpleStateWithCompeteEasyObject)interScheduleSimpleStateMap.get(datacenter));
                    Double score_price = Double.MAX_VALUE;
                    if (score_available>=0) {    // 为-1时表示资源不足，使其价格得分为最大值；否则正常计算价格得分
                        score_price = getPriceScoreForDc(scheduleRes.getKey(), datacenter, (SimpleStateEasyObject)interScheduleSimpleStateMap.get(datacenter));
                    }
                    scoreList.put(datacenter ,new Pair<>(score_price, score_available));
                }
                // 对数据中心排序
                Collections.sort(scheduleRes.getValue(), (d1, d2) -> {
                    double scoreD1_price = scoreList.get(d1).getFirst();
                    double scoreD1_available = scoreList.get(d1).getSecond();
                    double scoreD2_price = scoreList.get(d2).getFirst();
                    double scoreD2_available = scoreList.get(d2).getSecond();
                    
                    // 按降序排序
                    if(Math.abs(scoreD1_price -  scoreD2_price) < 1e-6) {
                        // 若 scoreD1_price == scoreD2_price，则看 score_available
                        // 若 scoreD1_available > scoreD2_available，则 d1 排在前面
                        return -Double.compare(scoreD1_available, scoreD2_available);
                    } else {
                        // 若 scoreD1_price < scoreD2_price，则 d1 排在前面
                        return Double.compare(scoreD1_price, scoreD2_price);
                    }
                });

                Datacenter target = scheduleRes.getValue().get(0);
                interSchedulerResult.addDcResult(scheduleRes.getKey(), target);

                // 更新统计信息
                updateStateMap(interScheduleSimpleStateMap, target, scheduleRes.getKey());
            }
        }
    }


    private static double getScoreForDc(InstanceGroup instanceGroup, Datacenter datacenter, SimpleStateEasyObject simpleStateEasyObject) {
        long cpuSum = instanceGroup.getCpuSum();
        long ramSum = instanceGroup.getRamSum();
        long storageSum = instanceGroup.getStorageSum();
        long bwSum = instanceGroup.getBwSum();
        if(simpleStateEasyObject.getCpuAvailableSum()<cpuSum || simpleStateEasyObject.getRamAvailableSum()<ramSum || simpleStateEasyObject.getStorageAvailableSum()<storageSum || simpleStateEasyObject.getBwAvailableSum()<bwSum){
            return -1;
        }else{
            double score = (simpleStateEasyObject.getCpuAvailableSum() * 10 / (double) simpleStateEasyObject.getCpuCapacitySum() + simpleStateEasyObject.getRamAvailableSum() * 10 / (double) simpleStateEasyObject.getRamCapacitySum()) / 2;
            return score;
        }
    }

    private static double getPriceScoreForDc(InstanceGroup instanceGroup, Datacenter datacenter, SimpleStateEasyObject simpleStateEasyObject) {
        long cpuSum = instanceGroup.getCpuSum();
        long ramSum = instanceGroup.getRamSum();
        long storageSum = instanceGroup.getStorageSum();
        long bwSum = instanceGroup.getBwSum();
        double score = (cpuSum * (datacenter.getPricePerCpu() + datacenter.getPricePerCpuPerSec()) + ramSum * (datacenter.getPricePerRam() + datacenter.getPricePerRamPerSec()) + storageSum * (datacenter.getPricePerStorage() + datacenter.getPricePerStoragePerSec()) + bwSum * (datacenter.getPricePerBw() + datacenter.getPricePerBwPerSec()));
        return score;
    }

    private static double getAvailableScoreForDc(InstanceGroup instanceGroup, Datacenter datacenter, SimpleStateEasyObject simpleStateEasyObject) {
        long cpuSum = instanceGroup.getCpuSum();
        long ramSum = instanceGroup.getRamSum();
        long storageSum = instanceGroup.getStorageSum();
        long bwSum = instanceGroup.getBwSum();
        if(simpleStateEasyObject.getCpuAvailableSum()<cpuSum || simpleStateEasyObject.getRamAvailableSum()<ramSum || simpleStateEasyObject.getStorageAvailableSum()<storageSum || simpleStateEasyObject.getBwAvailableSum()<bwSum){
            return -1;
        } else {
            double score = cpuALPHA * simpleStateEasyObject.getCpuAvailableSum() + memBETA * simpleStateEasyObject.getRamAvailableSum();
            return score;
        }
    }

    
    private static double getAvailableScoreWithCompeteForDc(InstanceGroup instanceGroup, Datacenter datacenter, SimpleStateWithCompeteEasyObject simpleStateWithCompeteEasyObject) {
        long cpuSum = instanceGroup.getCpuSum();
        long ramSum = instanceGroup.getRamSum();
        long storageSum = instanceGroup.getStorageSum();
        long bwSum = instanceGroup.getBwSum();
        if(simpleStateWithCompeteEasyObject.getCpuAvailableSum()<cpuSum || simpleStateWithCompeteEasyObject.getRamAvailableSum()<ramSum || simpleStateWithCompeteEasyObject.getStorageAvailableSum()<storageSum || simpleStateWithCompeteEasyObject.getBwAvailableSum()<bwSum){
            return -1;
        } else {
            if(simpleStateWithCompeteEasyObject.getCpuAvailableSum()<simpleStateWithCompeteEasyObject.getCpuCompeteSum() || simpleStateWithCompeteEasyObject.getRamAvailableSum()<simpleStateWithCompeteEasyObject.getRamCompeteSum() || simpleStateWithCompeteEasyObject.getStorageAvailableSum()<simpleStateWithCompeteEasyObject.getStorageCompeteSum() || simpleStateWithCompeteEasyObject.getBwAvailableSum()<simpleStateWithCompeteEasyObject.getBwCompeteSum()) {
                // 随机使 (1-AvailableSum/CompeteSum) 比例请求不调度到该 DC
                double randomDouble = random.nextDouble();
                if(randomDouble >= simpleStateWithCompeteEasyObject.getCpuAvailableSum()/simpleStateWithCompeteEasyObject.getCpuCompeteSum() || randomDouble >= simpleStateWithCompeteEasyObject.getRamAvailableSum()/simpleStateWithCompeteEasyObject.getRamCompeteSum() || randomDouble >= simpleStateWithCompeteEasyObject.getStorageAvailableSum()/simpleStateWithCompeteEasyObject.getStorageCompeteSum() || randomDouble >= simpleStateWithCompeteEasyObject.getBwAvailableSum()/simpleStateWithCompeteEasyObject.getBwCompeteSum()) {
                    return -1;
                }
            } 
            double score = cpuALPHA * simpleStateWithCompeteEasyObject.getCpuAvailableSum() + memBETA * simpleStateWithCompeteEasyObject.getRamAvailableSum();
            return score;
        }
    }

    private static void updateStateMap(Map<Datacenter, Object> interScheduleSimpleStateMap, Datacenter target, InstanceGroup instanceGroup) {
        SimpleStateEasyObject simpleStateEasyObject = (SimpleStateEasyObject) interScheduleSimpleStateMap.get(target);
        simpleStateEasyObject.allocateResource(instanceGroup.getCpuSum(), instanceGroup.getRamSum(), instanceGroup.getStorageSum(), instanceGroup.getBwSum());
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
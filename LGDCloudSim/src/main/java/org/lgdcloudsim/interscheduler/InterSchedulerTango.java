package org.lgdcloudsim.interscheduler;

import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.Datacenter;
import org.lgdcloudsim.network.NetworkTopology;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.request.UserRequest;
import org.lgdcloudsim.statemanager.SimpleStateWithCompeteEasyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InterSchedulerTango extends InterSchedulerSimple{
    public InterSchedulerTango(int id, Simulation simulation, int collaborationId, int target, boolean isSupportForward) {
        super(id, simulation, collaborationId, target, isSupportForward);
    }
    @Override
    protected InterSchedulerResult scheduleToDatacenter(List<InstanceGroup> instanceGroups){
        double start = System.currentTimeMillis();
        List<Datacenter> allDatacenters = simulation.getCollaborationManager().getDatacenters(collaborationId);
        InterSchedulerResult interSchedulerResult = new InterSchedulerResult(this, allDatacenters);
        double end = System.currentTimeMillis();
        this.excludeTime = Math.max(0.0, end - start);
        
        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = tangoFiltering(instanceGroups, allDatacenters);

        if (interScheduleSimpleStateMap.get(allDatacenters.get(0)) instanceof SimpleStateWithCompeteEasyObject) {
            RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailableAndCompete(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
        } else {
            RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailable(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
        }

        return interSchedulerResult;
    }

    /*
     * 将亲和组按照请求分组
     * @param instanceGroups 亲和组列表
     * @return Map<UserRequest, List<InstanceGroup>> 请求与亲和组的映射
     */
    private Map<UserRequest, List<InstanceGroup>> groupInstanceGroupByUserRequest(List<InstanceGroup> instanceGroups) {
        Map<UserRequest, List<InstanceGroup>> userRequestInstanceGroupsMap = new HashMap<>();
        for (InstanceGroup instanceGroup : instanceGroups) {
            UserRequest userRequest = instanceGroup.getUserRequest();
            if (userRequestInstanceGroupsMap.containsKey(userRequest)) {
                userRequestInstanceGroupsMap.get(userRequest).add(instanceGroup);
            } else {
                List<InstanceGroup> instanceGroupList = new ArrayList<>();
                instanceGroupList.add(instanceGroup);
                userRequestInstanceGroupsMap.put(userRequest, instanceGroupList);
            }
        }
        return userRequestInstanceGroupsMap;
    }

    private Map<InstanceGroup, List<Datacenter>> tangoFiltering(List<InstanceGroup> instanceGroups, List<Datacenter> allDatacenters) {

        Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = new HashMap<>();

        NetworkTopology networkTopology = simulation.getNetworkTopology();

        // 对属于同一个请求的实例组统一调度
        Map<UserRequest, List<InstanceGroup>> userRequestInstanceGroupsMap = groupInstanceGroupByUserRequest(instanceGroups);
        // 遍历同属于一个请求的所有实例组
        for (Map.Entry<UserRequest, List<InstanceGroup>> entry : userRequestInstanceGroupsMap.entrySet()) {
            UserRequest userRequest = entry.getKey();
            List<InstanceGroup> instanceGroupList = entry.getValue();
            
            // step1: Initialization with access delay demand
            for(InstanceGroup instanceGroup : instanceGroupList){
                // 深拷贝allDatacenters并赋值
                List<Datacenter> allDatacentersCopy = new ArrayList<>();
                for(Datacenter datacenter : allDatacenters){
                    if(instanceGroup.getAccessLatency() > networkTopology.getAccessLatency(instanceGroup.getUserRequest(), datacenter)) {
                        allDatacentersCopy.add(datacenter);
                    }
                }
                instanceGroupAvailableDatacenters.put(instanceGroup, allDatacentersCopy);
            }

            // step2: Iterative Update
            Boolean flag = true;
            while (flag == true) {
                flag = false;
                for (InstanceGroup instanceGroup : instanceGroupList) {
                    List<Datacenter> availableDatacenters = instanceGroupAvailableDatacenters.get(instanceGroup);
                    Integer numBeforeUpdate = availableDatacenters.size();

                    // 若选定当前数据中心后，存在其它实例组：其所有可行数据中心，都无法满足实例组间延迟。则删除当前数据中心。
                    availableDatacenters.removeIf(nullDatacenter -> {
                        for (InstanceGroup otherInstanceGroup : instanceGroupList) {
                            if (otherInstanceGroup != instanceGroup) {
                                Boolean deleteFlag = true;
                                for (Datacenter otherDatacenter : instanceGroupAvailableDatacenters.get(otherInstanceGroup)) {
                                    if (networkTopology.getDelay(nullDatacenter, otherDatacenter) <= userRequest.getInstanceGroupGraph().getDelay(instanceGroup, otherInstanceGroup)) {
                                        deleteFlag = false;
                                    }
                                }
                                if (deleteFlag == true) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    });

                    Integer numAfterUpdate = availableDatacenters.size();
                    if(numBeforeUpdate != numAfterUpdate){
                        flag = true;
                    }
                }
            }
        }
        return instanceGroupAvailableDatacenters;
    }
}

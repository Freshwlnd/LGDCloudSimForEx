package org.lgdcloudsim.interscheduler;

import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.Datacenter;
import org.lgdcloudsim.queue.InstanceGroupQueueCFS;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.statemanager.SimpleStateWithCompeteEasyObject;

import java.util.List;
import java.util.Map;

public class InterSchedulerLattice extends InterSchedulerSimple {
    
    PMQSF pmqsf;

    public InterSchedulerLattice(int id, Simulation simulation, int collaborationId, int target, boolean isSupportForward) {
        super(id, simulation, collaborationId, target, isSupportForward);
        pmqsf = new PMQSF(simulation.getCollaborationManager().getDatacenters(collaborationId), simulation);
        this.setwaitSchedulingInstanceGroupQueue(new InstanceGroupQueueCFS());
    }
    
    @Override
    protected InterSchedulerResult scheduleToDatacenter(List<InstanceGroup> instanceGroups){
        double start = System.currentTimeMillis();
        List<Datacenter> allDatacenters = simulation.getCollaborationManager().getDatacenters(collaborationId);
        InterSchedulerResult interSchedulerResult = new InterSchedulerResult(this, allDatacenters);
        double end = System.currentTimeMillis();
        this.excludeTime = Math.max(0.0, end - start);

        for(InstanceGroup instanceGroup : instanceGroups){
            Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = this.pmqsf.filtering(instanceGroup, allDatacenters, simulation, interScheduleSimpleStateMap, interSchedulerResult);

            // 如果 interScheduleSimpleStateMap 的 value 类型为 SimpleStateWithCompeteEasyObject，则调用 heuristicScoringByPriceAndAvailableAndCompete 方法
            // 否则调用 heuristicScoring 方法

            if (interScheduleSimpleStateMap.get(allDatacenters.get(0)) instanceof SimpleStateWithCompeteEasyObject) {
                RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailableAndCompete(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
            } else {
                RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailable(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
            }
        }

        return interSchedulerResult;
    }

    // 比simple调度器增加 SegmentTree 更新
    @Override
    public void synBetweenDcState(List<Datacenter> datacenters) {
        for (Datacenter datacenter : datacenters) {
            if (!dcStateSynType.containsKey(datacenter)) {
                throw new IllegalStateException("InterSchedulerSimple.synBetweenDcState: There is not type of " + datacenter.getName());
            }

            String stateType = dcStateSynType.get(datacenter);
            interScheduleSimpleStateMap.put(datacenter, datacenter.getStatesManager().getStateByType(stateType));
        }
        updateBWInfo(datacenters);
    }

    private void updateBWInfo(List<Datacenter> datacenters) {
        pmqsf.updateBWInfo(datacenters, simulation);
    }
}

package org.lgdcloudsim.interscheduler;

import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.Datacenter;
import org.lgdcloudsim.queue.InstanceGroupQueueCFS;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.statemanager.SimpleStateWithCompeteEasyObject;

import java.util.List;
import java.util.Map;

public class InterSchedulerDelayFirst extends InterSchedulerSimple{
    public InterSchedulerDelayFirst(int id, Simulation simulation, int collaborationId, int target, boolean isSupportForward) {
        super(id, simulation, collaborationId, target, isSupportForward);
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
            Map<InstanceGroup, List<Datacenter>> instanceGroupAvailableDatacenters = RandomAndHeuristicAlgorithm.delayFirstFiltering(instanceGroup, allDatacenters, simulation, interScheduleSimpleStateMap);

            if (interScheduleSimpleStateMap.get(allDatacenters.get(0)) instanceof SimpleStateWithCompeteEasyObject) {
                RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailableAndCompete(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
            } else {
                RandomAndHeuristicAlgorithm.heuristicScoringByPriceAndAvailable(interSchedulerResult, instanceGroupAvailableDatacenters, interScheduleSimpleStateMap);
            } 
        }
        
        return interSchedulerResult;
    }
}

package org.lgdcloudsim.statemanager;

import lombok.Getter;
import lombok.Setter;
import org.lgdcloudsim.request.Instance;

/**
 * A class implementing the {@link SimpleState} interface.
 * When synchronizing the state to the inter-scheduler,
 * it will generate a simple copy of the state, see {@link SimpleStateEasyObject}.
 *
 * @author Anonymous
 * @since LGDCloudSim 1.0
 */
@Getter
@Setter
public class SimpleStateWithCompete extends SimpleStateEasy {
    
    /**
     * The competing gap.
     */
    long nowCompeteGap;

    /**
     * The sum of the competing cpu of last competing gap.
     */
    long cpuCompeteSum = 0;

    /**
     * The sum of the competing ram of last competing gap.
     */
    long ramCompeteSum = 0;

    /**
     * The sum of the competing storage of last competing gap.
     */
    long storageCompeteSum = 0;

    /**
     * The sum of the competing bw of last competing gap.
     */
    long bwCompeteSum = 0;

    /**
     * The sum of the competing cpu of now competing gap.
     */
    long nowCpuCompeteSum = 0;

    /**
     * The sum of the competing ram of now competing gap.
     */
    long nowRamCompeteSum = 0;

    /**
     * The sum of the competing storage of now competing gap.
     */
    long nowStorageCompeteSum = 0;

    /**
     * The sum of the competing bw of now competing gap.
     */
    long nowBwCompeteSum = 0;

    /**
     * Construct a new SimpleStateEasy.
     *
     * @param statesManager the {@link StatesManager} it belongs to.
     */
    public SimpleStateWithCompete(StatesManager statesManager) {
        super(statesManager);
        nowCompeteGap = 0;
    }

    @Override
    public SimpleState updateSimpleStateAllocated(int hostId, int[] hostState, Instance instance) {
        cpuAvailableSum -= instance.getCpu();
        ramAvailableSum -= instance.getRam();
        storageAvailableSum -= instance.getStorage();
        bwAvailableSum -= instance.getBw();

        long newCompeteGap = (int) Math.floor( this.statesManager.getDatacenter().getSimulation().clock() / this.statesManager.getCompeteGap());
        if (newCompeteGap != nowCompeteGap) {
            nowCompeteGap = newCompeteGap;
            cpuCompeteSum = nowCpuCompeteSum;
            ramCompeteSum = nowRamCompeteSum;
            storageCompeteSum = nowStorageCompeteSum;
            bwCompeteSum = nowBwCompeteSum;
            nowCpuCompeteSum = 0;
            nowRamCompeteSum = 0;
            nowStorageCompeteSum = 0;
            nowBwCompeteSum = 0;
        }

        nowCpuCompeteSum += instance.getCpu();
        nowRamCompeteSum += instance.getRam();
        nowStorageCompeteSum += instance.getStorage();
        nowBwCompeteSum += instance.getBw();

        return this;
    }

    @Override
    public Object generate() {
        return new SimpleStateEasyObject(statesManager.getHostNum(),
                cpuAvailableSum,ramAvailableSum,storageAvailableSum,bwAvailableSum,
                statesManager.getHostCapacityManager().getCpuCapacitySum(),
                statesManager.getHostCapacityManager().getRamCapacitySum(),
                statesManager.getHostCapacityManager().getStorageCapacitySum(),
                statesManager.getHostCapacityManager().getBwCapacitySum());
    }

    @Override
    public Object generateWithCompete() {
        return new SimpleStateWithCompeteEasyObject(statesManager.getHostNum(),
                cpuAvailableSum,ramAvailableSum,storageAvailableSum,bwAvailableSum,
                statesManager.getHostCapacityManager().getCpuCapacitySum(),
                statesManager.getHostCapacityManager().getRamCapacitySum(),
                statesManager.getHostCapacityManager().getStorageCapacitySum(),
                statesManager.getHostCapacityManager().getBwCapacitySum(),
                cpuCompeteSum,ramCompeteSum,storageCompeteSum,bwCompeteSum);
    }
}

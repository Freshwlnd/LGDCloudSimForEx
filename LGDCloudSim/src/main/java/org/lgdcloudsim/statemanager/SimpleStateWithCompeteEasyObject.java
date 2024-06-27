package org.lgdcloudsim.statemanager;

import lombok.Getter;
import lombok.Setter;

/**
 * The class is the object sent to the inter-scheduler during synchronization.
 * It contains the following information:
 * <ul>
 *     <li>The number of hosts in the datacenter</li>
 *     <li>The sum of the available cpu of all hosts in the datacenter</li>
 *     <li>The sum of the available ram of all hosts in the datacenter</li>
 *     <li>The sum of the available storage of all hosts in the datacenter</li>
 *     <li>The sum of the available bw of all hosts in the datacenter</li>
 *     <li>The sum of the total cpu capacity of all hosts in the datacenter</li>
 *     <li>The sum of the total ram capacity of all hosts in the datacenter</li>
 *     <li>The sum of the total storage capacity of all hosts in the datacenter</li>
 *     <li>The sum of the total bw capacity of all hosts in the datacenter</li>
 * </ul>
 *
 * @author Anonymous
 * @since LGDCloudSim 1.0
 */
@Getter
@Setter
public class SimpleStateWithCompeteEasyObject extends SimpleStateEasyObject {
    
    /**
     * The sum of the competing cpu of all hosts in the datacenter.
     */
    long cpuCompeteSum;

    /**
     * The sum of the competing ram of all hosts in the datacenter.
     */
    long ramCompeteSum;

    /**
     * The sum of the competing storage of all hosts in the datacenter.
     */
    long storageCompeteSum;

    /**
     * The sum of the competing bw of all hosts in the datacenter.
     */
    long bwCompeteSum;

    /**
     * Construct a simple state easy object with the number of hosts in the datacenter, the sum of the available resources and the sum of the total resources.
     *
     * @param hostNum             the number of hosts in the datacenter.
     * @param cpuAvailableSum     the sum of the available cpu of all hosts in the datacenter.
     * @param ramAvailableSum     the sum of the available ram of all hosts in the datacenter.
     * @param storageAvailableSum the sum of the available storage of all hosts in the datacenter.
     * @param bwAvailableSum      the sum of the available bw of all hosts in the datacenter.
     * @param cpuCapacitySum      the sum of the total cpu capacity of all hosts in the datacenter.
     * @param ramCapacitySum      the sum of the total ram capacity of all hosts in the datacenter.
     * @param storageCapacitySum  the sum of the total storage capacity of all hosts in the datacenter.
     * @param bwCapacitySum       the sum of the total bw capacity of all hosts in the datacenter.
     * @param cpuCompeteSum       the sum of the competing cpu of all hosts in the datacenter.
     * @param ramCompeteSum       the sum of the competing ram of all hosts in the datacenter.
     * @param storageCompeteSum   the sum of the competing storage of all hosts in the datacenter.
     * @param bwCompeteSum        the sum of the competing bw of all hosts in the datacenter.
     */
    public SimpleStateWithCompeteEasyObject(int hostNum, long cpuAvailableSum, long ramAvailableSum, long storageAvailableSum, long bwAvailableSum, long cpuCapacitySum, long ramCapacitySum, long storageCapacitySum, long bwCapacitySum, long cpuCompeteSum, long ramCompeteSum, long storageCompeteSum, long bwCompeteSum) {
        super(hostNum, cpuAvailableSum, ramAvailableSum, storageAvailableSum, bwAvailableSum, cpuCapacitySum, ramCapacitySum, storageCapacitySum, bwCapacitySum);
        this.cpuCompeteSum = cpuCompeteSum;
        this.ramCompeteSum = ramCompeteSum;
        this.storageCompeteSum = storageCompeteSum;
        this.bwCompeteSum = bwCompeteSum;
    }

}

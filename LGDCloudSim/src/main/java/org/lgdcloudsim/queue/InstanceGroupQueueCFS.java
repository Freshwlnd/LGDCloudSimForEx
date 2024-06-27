package org.lgdcloudsim.queue;

import lombok.Getter;
import lombok.Setter;
import org.lgdcloudsim.request.Instance;
import org.lgdcloudsim.request.InstanceGroup;
import org.lgdcloudsim.request.UserRequest;

import java.util.*;

/**
 * A class to represent a instanceGroup queue with Complexity-First Sorting.
 * This class implements the interface {@link InstanceGroupQueue}.
 *
 * @author Xuyh
 * @since LGDCloudSim 1.0
 */
public class InstanceGroupQueueCFS extends InstanceGroupQueueFifo {

    @Getter
    @Setter
    private double cpuALPHA = 1.0;

    @Getter
    @Setter
    private double memBETA = 0.5;
    
    public InstanceGroupQueueCFS() {
        super();
    }

    /**
     * Create a new instance of InstanceGroupQueueFifo with the given list of instanceGroups and the default batch number.
     *
     * @param batchNum the number of instanceGroups to be sent in a batch
     */
    public InstanceGroupQueueCFS(int batchNum) {
        super(batchNum);
    }

    /**
     * Add the instanceGroup in the request to the end of the queue
     *
     * @param userRequestsOrInstanceGroups the list of userRequests to be added to the queue
     * @return the instanceGroupQueue
     */
    @Override
    public InstanceGroupQueue add(List<?> userRequestsOrInstanceGroups) {
        
        List<InstanceGroup> instanceGroups = new ArrayList<>();

        if (!userRequestsOrInstanceGroups.isEmpty()) {
            if (userRequestsOrInstanceGroups.get(0) instanceof UserRequest) {
                for (UserRequest userRequest : (List<UserRequest>) userRequestsOrInstanceGroups) {
                    instanceGroups.addAll(userRequest.getInstanceGroups());
                }
            } else if (userRequestsOrInstanceGroups.get(0) instanceof InstanceGroup) {
                instanceGroups.addAll((List<InstanceGroup>) userRequestsOrInstanceGroups);
            } else {
                throw new RuntimeException("The type of the list is not supported.");
            }
        }

        double start = System.currentTimeMillis();

        // 根据匿名函数的返回值对instanceGroups进行排序，匿名函数中计算每个 instanceGroup 的 网络连接数 edgeNumLinked 和 资源需求量 sumqValue，当 edgeNumLinked 不同时，按 edgeNumLinked 降序排列，当 edgeNumLinked 相同时，按 sumqValue 降序排列
        instanceGroups.sort((instanceGroup1, instanceGroup2) -> {
            int compValue1 = instanceGroup1.getEdgeNumLinked();
            int compValue2 = instanceGroup2.getEdgeNumLinked();
            
            double sumqValue1 = cpuALPHA * instanceGroup1.getCpuSum() + memBETA * instanceGroup1.getRamSum();
            double sumqValue2 = cpuALPHA * instanceGroup2.getCpuSum() + memBETA * instanceGroup2.getRamSum();

            if (compValue1 != compValue2) {
                return Integer.compare(compValue2, compValue1);
            } else {
                return Double.compare(sumqValue2, sumqValue1);
            }
        });

        
        double end = System.currentTimeMillis();

        this.sortTime = Math.max(0.1, end - start);

        for (InstanceGroup instanceGroup : instanceGroups) {
            add(instanceGroup);
        }

        return this;
    }

}

package org.example;

import ch.qos.logback.classic.Level;
import org.lgdcloudsim.core.CloudSim;
import org.lgdcloudsim.core.Factory;
import org.lgdcloudsim.core.FactorySimple;
import org.lgdcloudsim.core.Simulation;
import org.lgdcloudsim.datacenter.InitDatacenter;
import org.lgdcloudsim.network.NetworkTopology;
import org.lgdcloudsim.network.NetworkTopologySimple;
import org.lgdcloudsim.network.RandomDelayDynamicModel;
import org.lgdcloudsim.record.MemoryRecord;
import org.lgdcloudsim.user.UserRequestManager;
import org.lgdcloudsim.user.UserRequestManagerCsv;
import org.lgdcloudsim.user.UserSimple;
import org.lgdcloudsim.util.Log;

public class Lattice {
    Simulation lgdcloudSim;
    Factory factory;
    UserSimple user;
    UserRequestManager userRequestManager;
    String REGION_DELAY_FILE = "./src/main/resources/regionDelay.csv";
    String AREA_DELAY_FILE = "./src/main/resources/areaDelay.csv";
    
    String testEx =  "1-overall";
    String testAlgorithm = "1-Lattice";
    String fileSuf = "";
    String dirSuf = "";

    String CONFIG_FILE_PREF = "./src/main/resources/example/Lattice/";

    String DATACENTER_BW_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/DatacenterBwConfig.csv";
    String USER_REQUEST_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/generateRequestParameter.csv";
    String DATACENTER_CONFIG_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/DatacentersConfig.json";

    String DBNAME = testEx+"."+testAlgorithm+".db";

    public static void main(String[] args) {
        new Lattice(args);
    }

    private void setArgs(String[] args) {
        if(args.length > 0) {
            testEx = args[0];
            testAlgorithm = args[1];

            if(args.length > 2) {
                fileSuf = "."+args[2];
            }
            if(args.length > 3) {
                dirSuf = args[3];
            }
            
            DATACENTER_BW_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/DatacenterBwConfig.csv";
            USER_REQUEST_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/generateRequestParameter.csv";
            DATACENTER_CONFIG_FILE = CONFIG_FILE_PREF+testEx+"/"+testAlgorithm+"/DatacentersConfig.json";

            DBNAME = testEx+"."+testAlgorithm+fileSuf+".db";

            System.out.println(testEx+" "+testAlgorithm+" "+fileSuf+" "+dirSuf+"\n"+DBNAME);
        }
    }

    private Lattice(String[] args) {
        setArgs(args);
        
        double start = System.currentTimeMillis();
        Log.setLevel(Level.INFO);
        lgdcloudSim = new CloudSim();
        factory = new FactorySimple();
        lgdcloudSim.setDbName(DBNAME);
        lgdcloudSim.setSqlRecord(factory.getSqlRecord("detailScheduleTime", DBNAME, dirSuf));
        initUser();
        initNetwork();
        initDatacenters();
        double endInit = System.currentTimeMillis();
        lgdcloudSim.start();
        double end = System.currentTimeMillis();
        System.out.println("\nRunning situation information:");
        System.out.println("\tInitialisation takes time: " + (endInit - start) / 1000 + "s");
        System.out.println("\tSimulation runs take time: " + (end - endInit) / 1000 + "s");
        System.out.println("\tSimulation total time spent: " + (end - start) / 1000 + "s");
        System.out.println("\tMaximum memory usage during simulation: " + MemoryRecord.getMaxUsedMemory() / 1000000 + " Mb");
        System.out.println("\tSimulation run result save path: " + lgdcloudSim.getSqlRecord().getDbPath());
    }


    private void initUser() {
        userRequestManager = new UserRequestManagerCsv(USER_REQUEST_FILE);
        user = new UserSimple(lgdcloudSim, userRequestManager);
    }

    private void initDatacenters() {
        InitDatacenter.initDatacenters(lgdcloudSim, factory, DATACENTER_CONFIG_FILE);
    }

    private void initNetwork() {
        NetworkTopology networkTopology = new NetworkTopologySimple(REGION_DELAY_FILE, AREA_DELAY_FILE, DATACENTER_BW_FILE);
        networkTopology.setDelayDynamicModel(new RandomDelayDynamicModel());
        lgdcloudSim.setNetworkTopology(networkTopology);
    }
}

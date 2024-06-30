import os
import sqlite3
import csv
import copy

# Get current working directory
current_dir = os.getcwd()

# Get the absolute path to the directory where the current script is located
script_dir = os.path.dirname(os.path.abspath(__file__))

# Change current working directory
os.chdir(script_dir)

#===========================================================#
#                  Experimental parameters                  #
#===========================================================#
dirPrefix = "test-/"
DBFileSuf = ".example"
LOGFileSuf = "-example"
#===========================================================#
testExList = ["1-overall","2-ablation"]
testAlgorithmList_overall = ["1-Lattice", "2-Diktyo", "3-TanGo", "4-DelayFirst"]
testAlgorithmList_ablation = ["1-Lattice", "5-Lattice_woP", "6-Lattice_woC", "7-Lattice_woP&C"]
header_overall = ["Lattice", "Diktyo", "TanGo", "Delay-first"]
header_ablation = ["Lattice", "LatticewoP", "Lattice-woC", "Lattice-woP&C"]
#===========================================================#
# DBNAME_PREF = dirPrefix+testEx+"."+testAlgorithm+DBFileSuf
# LOGNAME_PREF = dirPrefix+testEx+"-"+testAlgorithm+LOGFileSuf
#===========================================================#
#                      SQL statement                        #
#===========================================================#
DELETE_VIEW_SQL = """
DROP VIEW IF EXISTS instanceDelay;
"""
CREATE_VIEW_SQL = """
CREATE VIEW IF NOT EXISTS instanceDelay AS
SELECT 
    instance.id, 
    userRequest.submitTime AS submitTime, 
    instance.startTime-userRequest.submitTime AS delay, 
    instanceGroup.interScheduleEndTime-userRequest.submitTime AS interScheduleTime, 
    instanceGroup.receivedTime-instanceGroup.InterScheduleEndTime AS transToDCTime, 
    instance.intraScheduleEndTime-instanceGroup.receivedTime AS intraScheduleTime, 
    instance.startTime-instance.IntraScheduleEndTime AS allocateTime
FROM instance 
LEFT JOIN instanceGroup on instance.instanceGroupId = instanceGroup.id 
LEFT JOIN userRequest on instance.userRequestId = userRequest.id 
Where instance.startTime >= 0;
"""
#===========================================================#
# Decision delay
DECISION_DELAY_SQL = """
SELECT submitTime,AVG(interScheduleTime),Max(interScheduleTime),Min(interScheduleTime) FROM instanceDelay GROUP BY submitTime;
"""
#===========================================================#
# Success rate
SUCCESS_RATE_SQL = """
SELECT
    userRequest.submitTime,
    SUM(CASE WHEN instanceGroup.receivedDc != -1 THEN 1 ELSE 0 END) AS successNum,
    t.totalCount AS sumNum,
    CAST(SUM(CASE WHEN instanceGroup.receivedDc != -1 THEN 1 ELSE 0 END) AS REAL) / t.totalCount * 100.0 AS successRate
FROM
    instanceGroup
JOIN userRequest ON instanceGroup.userRequestId = userRequest.id
JOIN
    (
        SELECT
            userRequest.submitTime,
            COUNT(instanceGroup.id) AS totalCount
        FROM
            instanceGroup
        JOIN userRequest ON instanceGroup.userRequestId = userRequest.id
        GROUP BY
            userRequest.submitTime
    ) t
ON userRequest.submitTime = t.submitTime
GROUP BY
    userRequest.submitTime;
"""
# Destination
DESTINATION_SQL = """
SELECT
    userRequest.submitTime,
    datacenter.id AS receivedDc,
    IFNULL(COUNT(instanceGroup.id), 0) AS countDc,
    CAST(IFNULL(COUNT(instanceGroup.id), 0) AS REAL) / t.totalCount * 100.0 AS percentage
FROM
    userRequest
CROSS JOIN (
    SELECT -1 AS id UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL 
    SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) AS datacenter
LEFT JOIN instanceGroup ON instanceGroup.userRequestId = userRequest.id AND instanceGroup.receivedDc = datacenter.id
LEFT JOIN
    (
        SELECT
            userRequest.submitTime,
            COUNT(instanceGroup.id) AS totalCount
        FROM
            instanceGroup
        JOIN userRequest ON instanceGroup.userRequestId = userRequest.id
        GROUP BY
            userRequest.submitTime
    ) t
ON userRequest.submitTime = t.submitTime
GROUP BY
    userRequest.submitTime,
    datacenter.id;
"""
#===========================================================#
# Overall CPU, RAM resource utilization
TOTAL_USED_RESOURCE_SQL = """
SELECT
    SUM(instance.cpu) AS usedCPU, 
    datacenterSum.sumCPU AS sumCPU, 
    CAST(SUM(instance.cpu) AS REAL)/datacenterSum.sumCPU*100.0 AS CPURate, 
    SUM(instance.ram) AS usedRAM, 
    datacenterSum.sumRAM AS sumRAM, 
    CAST(SUM(instance.ram) AS REAL)/datacenterSum.sumRAM*100.0 AS RAMRate
FROM 
    instance 
LEFT JOIN 
    instanceGroup on instance.instanceGroupId = instanceGroup.id  
LEFT JOIN
    (
        SELECT SUM(cpu) AS sumCPU, SUM(ram) AS sumRAM 
        FROM datacenter
        WHERE region != 'NULL' OR location != 'null'
    ) AS datacenterSum
WHERE 
    instance.finishTime is null  AND instanceGroup.receivedDc!=-1;
"""
# Overall bandwidth resource utilization
TOTAL_USED_BW_RESOURCE_SQL = """
SELECT 
    IFNULL(SUM(instanceGroupGraph.bw), 0) AS usedBW, 
    dcNetworkSum.sumBW AS restBW, 
    CAST(IFNULL(SUM(instanceGroupGraph.bw), 0) AS REAL) / (CAST(IFNULL(SUM(instanceGroupGraph.bw), 0) AS REAL) + dcNetworkSum.sumBW) * 100.0 AS BWRate
FROM 
    instanceGroupGraph 
LEFT JOIN 
    instanceGroup AS srcInstanceGroup ON instanceGroupGraph.srcInstanceGroupId = srcInstanceGroup.id
LEFT JOIN 
    instanceGroup AS dstInstanceGroup ON instanceGroupGraph.dstInstanceGroupId = dstInstanceGroup.id
LEFT JOIN
    (
        SELECT SUM(bw) AS sumBW 
        FROM dcNetwork 
        WHERE srcDatacenterId != dstDatacenterId
    ) AS dcNetworkSum
WHERE 
    srcInstanceGroup.receivedDc != -1 AND dstInstanceGroup.receivedDc != -1 AND instanceGroupGraph.srcDcId != instanceGroupGraph.dstDcId;
"""
# CPU, RAM resource utilization by region
USED_RESOURCE_SQL = """
SELECT
    datacenter.id AS dcId, 
    IFNULL(instanceSum.usedCPU, 0) AS usedCPU, 
    datacenterSum.sumCPU AS sumCPU, 
    CAST(IFNULL(instanceSum.usedCPU, 0) AS REAL)/datacenterSum.sumCPU*100.0 AS CPURate, 
    IFNULL(instanceSum.usedRAM, 0) AS usedRAM, 
    datacenterSum.sumRAM AS sumRAM, 
    CAST(IFNULL(instanceSum.usedRAM, 0) AS REAL)/datacenterSum.sumRAM*100.0 AS RAMRate
FROM 
    datacenter
LEFT JOIN
    (
        SELECT datacenter.id AS id, SUM(cpu) AS sumCPU, SUM(ram) AS sumRAM 
        FROM datacenter
        WHERE region != 'NULL' OR location != 'null'
        GROUP BY datacenter.id
    ) AS datacenterSum
    ON datacenter.id = datacenterSum.id
LEFT JOIN
    (
        SELECT 
            SUM(instance.cpu) AS usedCPU, 
            SUM(instance.ram) AS usedRAM,
            instanceGroup.receivedDc AS dcId
        FROM instance
        LEFT JOIN 
            instanceGroup on instance.instanceGroupId = instanceGroup.id
        WHERE 
            instance.finishTime is null AND instanceGroup.receivedDc!=-1
        GROUP BY
            instanceGroup.receivedDc
    ) AS instanceSum
    ON datacenter.id = instanceSum.dcId
WHERE region != 'NULL' OR location != 'null';
"""
# Bandwidth resource utilization per network link
USED_BW_RESOURCE_SQL = """
SELECT 
    dcNetwork.srcDatacenterId AS srcDcId, 
    dcNetwork.dstDatacenterId AS dstDcId,
    IFNULL(instanceSum.usedBW, 0) AS usedBW, 
    dcNetworkSum.sumBW AS restBW, 
    CAST(IFNULL(instanceSum.usedBW, 0) AS REAL) / (CAST(IFNULL(instanceSum.usedBW, 0) AS REAL) + dcNetworkSum.sumBW) * 100.0 AS BWRate
FROM
    dcNetwork
LEFT JOIN
    (
        SELECT 
            SUM(instanceGroupGraph.bw) AS usedBW,
            instanceGroupGraph.srcDcId AS srcDcId,
            instanceGroupGraph.dstDcId AS dstDcId
        FROM 
            instanceGroupGraph 
        LEFT JOIN 
            instanceGroup AS srcInstanceGroup ON instanceGroupGraph.srcInstanceGroupId = srcInstanceGroup.id
        LEFT JOIN 
            instanceGroup AS dstInstanceGroup ON instanceGroupGraph.dstInstanceGroupId = dstInstanceGroup.id
        WHERE 
            srcInstanceGroup.receivedDc != -1 AND dstInstanceGroup.receivedDc != -1 AND instanceGroupGraph.srcDcId != instanceGroupGraph.dstDcId
        GROUP BY
            instanceGroupGraph.srcDcId, instanceGroupGraph.dstDcId
    ) AS instanceSum
    ON dcNetwork.srcDatacenterId = instanceSum.srcDcId AND dcNetwork.dstDatacenterId = instanceSum.dstDcId
LEFT JOIN
    (
        SELECT srcDatacenterId, dstDatacenterId, SUM(bw) AS sumBW 
        FROM dcNetwork 
        GROUP BY srcDatacenterId, dstDatacenterId
    ) AS dcNetworkSum
    ON dcNetwork.srcDatacenterId = dcNetworkSum.srcDatacenterId AND dcNetwork.dstDatacenterId = dcNetworkSum.dstDatacenterId
WHERE 
    dcNetwork.srcDatacenterId != dcNetwork.dstDatacenterId AND dcNetwork.srcDatacenterId != -1 AND dcNetwork.dstDatacenterId != -1;
"""
#===========================================================#

data = {}       # Recording of single round data
batchData = {}  # Record multiple rounds of data

# Define a function to execute a query and export the results
def execute_and_export(data, filename):
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerows(data)

# Define a function to read data from a CSV file
def read_csv(filename):
    with open(filename, 'r') as f:
        reader = csv.reader(f)
        data = list(reader)
    return data

# Getting data from the database
def get_data():
    global data
    testAlgorithmList = []
    for testEx in testExList:
        if testEx not in data:
            data[testEx] = {}
        if testEx == "1-overall":
            testAlgorithmList = testAlgorithmList_overall
        elif testEx == "2-ablation":
            testAlgorithmList = testAlgorithmList_ablation
        for testAlgorithm in testAlgorithmList:
            
            DBNAME_PREF = dirPrefix+testEx+"."+testAlgorithm+DBFileSuf
            LOGNAME_PREF = dirPrefix+testEx+"-"+testAlgorithm+LOGFileSuf

            if os.path.exists('../RecordDb/'+DBNAME_PREF+".db") == False:
                continue

            print("connect to database: ", '../RecordDb/'+DBNAME_PREF+".db")

            try:
                # Connect to your SQLite database file
                conn = sqlite3.connect('../RecordDb/'+DBNAME_PREF+".db")

                # Initialise the data dictionary if a database file exists
                if testAlgorithm not in data[testEx]:
                    data[testEx][testAlgorithm] = {}
                
                # Create a cursor object for executing SQL statements
                cursor = conn.cursor()

                cursor.execute(DELETE_VIEW_SQL)
                cursor.execute(CREATE_VIEW_SQL)
                conn.commit()  # Submit the view creation operation
                print("View created successfully")

                # Decision delay
                cursor.execute(DECISION_DELAY_SQL)
                data[testEx][testAlgorithm]["decision_delay"] = decision_delay_data = cursor.fetchall()
                execute_and_export(decision_delay_data, '../RecordDb/'+DBNAME_PREF+'.decision_delay_data.csv')
                print("Get inter schedule time data successfully")

                # Success rate
                cursor.execute(SUCCESS_RATE_SQL)
                data[testEx][testAlgorithm]["success_rate"] = success_rate_data = cursor.fetchall()
                execute_and_export(success_rate_data, '../RecordDb/'+DBNAME_PREF+'.success_rate_data.csv')
                print("Get schedule success rate data successfully")

                # Destination
                cursor.execute(DESTINATION_SQL)
                data[testEx][testAlgorithm]["destination"] = destination_data = cursor.fetchall()
                execute_and_export(destination_data, '../RecordDb/'+DBNAME_PREF+'.destination_data.csv')
                print("Get schedule destination data successfully")

                # Overall CPU, RAM resource utilization
                cursor.execute(TOTAL_USED_RESOURCE_SQL)
                data[testEx][testAlgorithm]["total_used_resource"] = total_used_resource_data = cursor.fetchall()
                execute_and_export(total_used_resource_data, '../RecordDb/'+DBNAME_PREF+'.total_used_resource_data.csv')
                print("Get total used resource data successfully")

                # Overall bandwidth resource utilization
                cursor.execute(TOTAL_USED_BW_RESOURCE_SQL)
                data[testEx][testAlgorithm]["total_used_bw_resource"] = total_used_bw_resource_data = cursor.fetchall()
                execute_and_export(total_used_bw_resource_data, '../RecordDb/'+DBNAME_PREF+'.total_used_bw_resource_data.csv')
                print("Get total used bw resource data successfully")

                # CPU, RAM resource utilization by region
                cursor.execute(USED_RESOURCE_SQL)
                data[testEx][testAlgorithm]["used_resource"] = used_resource_data = cursor.fetchall()
                execute_and_export(used_resource_data, '../RecordDb/'+DBNAME_PREF+'.used_resource_data.csv')
                print("Get used resource data successfully")

                # Bandwidth resource utilization per network link
                cursor.execute(USED_BW_RESOURCE_SQL)
                data[testEx][testAlgorithm]["used_bw_resource"] = used_bw_resource_data = cursor.fetchall()
                execute_and_export(used_bw_resource_data, '../RecordDb/'+DBNAME_PREF+'.used_bw_resource_data.csv')
                print("Get used bw resource data successfully")

                # TCO
                if os.path.exists('./logs/'+LOGNAME_PREF+".log") == True:
                    p = os.popen("tail -n 15 './logs/"+LOGNAME_PREF+".log' | grep 'TCO' | awk '{print $4}'")
                    TCO = float(p.readlines()[0].strip())
                    data[testEx][testAlgorithm]["TCO"] = [[TCO]]
                    execute_and_export([[TCO]], '../RecordDb/'+DBNAME_PREF+'.TCO.csv')
                    print("Get TCO data successfully")
                else:
                    print("No TCO data")


                print()
            
            except Exception as e:
                print("Error: ", e)

# Getting data from CSV
def get_data_from_csv():
    global data
    testAlgorithmList = []
    for testEx in testExList:
        if testEx not in data:
            data[testEx] = {}
        if testEx == "1-overall":
            testAlgorithmList = testAlgorithmList_overall
        elif testEx == "2-ablation":
            testAlgorithmList = testAlgorithmList_ablation
        for testAlgorithm in testAlgorithmList:
            
            DBNAME_PREF = dirPrefix+testEx+"."+testAlgorithm+DBFileSuf
            LOGNAME_PREF = dirPrefix+testEx+"-"+testAlgorithm+LOGFileSuf

            if os.path.exists('../RecordDb/'+DBNAME_PREF+".db") == False:
                continue

            print("connect to database: ", '../RecordDb/'+DBNAME_PREF+".db")

            try:
                # Initialise the data dictionary if a database file exists
                if testAlgorithm not in data[testEx]:
                    data[testEx][testAlgorithm] = {}

                # Decision delay
                data[testEx][testAlgorithm]["decision_delay"] = decision_delay_data = read_csv('../RecordDb/'+DBNAME_PREF+'.decision_delay_data.csv')
                print("Get inter schedule time data successfully")

                # Success rate
                data[testEx][testAlgorithm]["success_rate"] = success_rate_data = read_csv('../RecordDb/'+DBNAME_PREF+'.success_rate_data.csv')
                print("Get schedule success rate data successfully")

                # Destination
                data[testEx][testAlgorithm]["destination"] = destination_data = read_csv('../RecordDb/'+DBNAME_PREF+'.destination_data.csv')
                print("Get schedule destination data successfully")

                # Overall CPU, RAM resource utilization
                data[testEx][testAlgorithm]["total_used_resource"] = total_used_resource_data = read_csv('../RecordDb/'+DBNAME_PREF+'.total_used_resource_data.csv')
                print("Get total used resource data successfully")

                # Overall bandwidth resource utilization
                data[testEx][testAlgorithm]["total_used_bw_resource"] = total_used_bw_resource_data = read_csv('../RecordDb/'+DBNAME_PREF+'.total_used_bw_resource_data.csv')
                print("Get total used bw resource data successfully")

                # CPU, RAM resource utilization by region
                data[testEx][testAlgorithm]["used_resource"] = used_resource_data = read_csv('../RecordDb/'+DBNAME_PREF+'.used_resource_data.csv')
                print("Get used resource data successfully")

                # Bandwidth resource utilization per network link
                data[testEx][testAlgorithm]["used_bw_resource"] = used_bw_resource_data = read_csv('../RecordDb/'+DBNAME_PREF+'.used_bw_resource_data.csv')
                print("Get used bw resource data successfully")

                # TCO
                if os.path.exists('./logs/'+LOGNAME_PREF+".log") == True:
                    data[testEx][testAlgorithm]["TCO"] = TCO = read_csv('../RecordDb/'+DBNAME_PREF+'.TCO.csv')
                    print("Get TCO data successfully")
                else:
                    print("No TCO data")

                print()
            
            except Exception as e:
                print("Error: ", e)

# Getting batch data
def get_batch_data(dirPrefixList):
    print("get batch data...")
    global data
    testAlgorithmList = []
    for testEx in testExList:
        if testEx not in data:
            data[testEx] = {}
        if testEx == "1-overall":
            testAlgorithmList = testAlgorithmList_overall
        elif testEx == "2-ablation":
            testAlgorithmList = testAlgorithmList_ablation
        for testAlgorithm in testAlgorithmList:
            
            DBNAME_PREF = dirPrefixList[0]+testEx+"."+testAlgorithm+DBFileSuf
            LOGNAME_PREF = dirPrefixList[0]+testEx+"-"+testAlgorithm+LOGFileSuf

            if os.path.exists('../RecordDb/'+DBNAME_PREF+".db") == False:
                continue

            print("connect to database: ", '../RecordDb/'+DBNAME_PREF+".db")
            try:
                # Initialise the data dictionary if a database file exists
                if testAlgorithm not in data[testEx]:
                    data[testEx][testAlgorithm] = {}

                # Decision delay
                data[testEx][testAlgorithm]["decision_delay"] = decision_delay_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "decision_delay")
                print("Get inter schedule time data successfully")

                # Success rate
                data[testEx][testAlgorithm]["success_rate"] = success_rate_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "success_rate")
                print("Get schedule success rate data successfully")

                # Destination
                data[testEx][testAlgorithm]["destination"] = destination_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "destination")
                print("Get schedule destination data successfully")

                # Overall CPU, RAM resource utilization
                data[testEx][testAlgorithm]["total_used_resource"] = total_used_resource_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "total_used_resource")
                print("Get total used resource data successfully")

                # Overall bandwidth resource utilization
                data[testEx][testAlgorithm]["total_used_bw_resource"] = total_used_bw_resource_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "total_used_bw_resource")
                print("Get total used bw resource data successfully")

                # CPU, RAM resource utilization by region
                data[testEx][testAlgorithm]["used_resource"] = used_resource_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "used_resource")
                print("Get used resource data successfully")

                # Bandwidth resource utilization per network link
                data[testEx][testAlgorithm]["used_bw_resource"] = used_bw_resource_data = getAverageData(dirPrefixList, testEx, testAlgorithm, "used_bw_resource")
                print("Get used bw resource data successfully")

                # TCO
                if os.path.exists('./logs/'+LOGNAME_PREF+".log") == True:
                    data[testEx][testAlgorithm]["TCO"] = TCO = getAverageData(dirPrefixList, testEx, testAlgorithm, "TCO")
                    print("Get TCO data successfully")
                else:
                    print("No TCO data")

                print()
            
            except Exception as e:
                print("Error: ", e)

# Get batch data averages
def getAverageData(dirPrefixList, testEx, testAlgorithm, dataType):
    # Find the average value of each element in batchData[dirPrefix][testEx][testAlgorithm][dataType] for all dirPrefixes in dirPrefixList in batchData.
    rawData = []
    for dirPrefixTmp in dirPrefixList:
        rawData.append(batchData[dirPrefixTmp][testEx][testAlgorithm][dataType])
    avgData = copy.deepcopy(rawData[0])
    for i in range(len(rawData[0])):
        # 如果avgData[i]是一个元组，则转化成列表
        if isinstance(avgData[i], tuple):
            avgData[i] = list(avgData[i])
        for j in range(len(rawData[0][i])):
            avgData[i][j] = 0.
            for x in range(len(rawData)):
                avgData[i][j] += float(rawData[x][i][j])
            avgData[i][j] /= len(rawData)
    return avgData

# Processing data
def process_data():
    for testEx in testExList:
        
        schedule_time_data = {}
        decision_delay_data = {}
        success_rate_data = {}
        destination_data = {}
        total_used_resource_data = {}
        total_used_bw_resource_data = {}
        used_resource_data = {}
        used_bw_resource_data = {}
        testAlgorithmList = []
        header = []

        if testEx == "1-overall":
            header = header_overall
            testAlgorithmList = testAlgorithmList_overall
        elif testEx == "2-ablation":
            header = header_ablation
            testAlgorithmList = testAlgorithmList_ablation
        
        # Integrate data from all algorithms
        for testAlgorithm in testAlgorithmList:
            try:
                if testAlgorithm in data[testEx]:
                    decision_delay_data[testAlgorithm] = (data[testEx][testAlgorithm]["decision_delay"])
                    success_rate_data[testAlgorithm] = (data[testEx][testAlgorithm]["success_rate"])
                    destination_data[testAlgorithm] = (data[testEx][testAlgorithm]["destination"])
                    total_used_resource_data[testAlgorithm] = (data[testEx][testAlgorithm]["total_used_resource"])
                    total_used_bw_resource_data[testAlgorithm] = (data[testEx][testAlgorithm]["total_used_bw_resource"])
                    used_resource_data[testAlgorithm] = (data[testEx][testAlgorithm]["used_resource"])
                    used_bw_resource_data[testAlgorithm] = (data[testEx][testAlgorithm]["used_bw_resource"])
            except Exception as e:
                print("Error: ", e)
        
        # Decision delay
        output = []
        tmp_header = ["Times"]
        tmp_header.extend(header)
        output.append(tmp_header)
        for i in range(len(decision_delay_data[testAlgorithmList[0]])):
            Delay = [i+1]
            for algorithm in testAlgorithmList:
                Delay.append(decision_delay_data[algorithm][i][2])
            output.append(Delay)
        execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.SUM.decision_delay_data.csv')
        print("process inter schedule time data successfully")

        # Success rate
        output = []
        tmp_header = ["Times"]
        tmp_header.extend(header)
        output.append(tmp_header)
        for i in range(len(success_rate_data[testAlgorithmList[0]])):
            successRate = [i+1]
            for algorithm in testAlgorithmList:
                successRate.append(success_rate_data[algorithm][i][3])
            output.append(successRate)
        execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.SUM.success_rate_data.csv')
        print("process schedule success rate data successfully")

        # Destination
        for testAlgorithm in testAlgorithmList:
            output = []
            tmp_header = ["Batch"]
            for i in range(10):
                tmp_header.append("DC"+str(i+1))
            tmp_header.append("Fail")
            output.append(tmp_header)
            lst_submittime = -1
            lst_dcid = -1
            destination = [1]
            failRate = 0
            batchId = 0
            for i in range(len(destination_data[testAlgorithm])):
                if float(destination_data[testAlgorithm][i][0]) != float(lst_submittime):
                    if lst_submittime != -1:
                        if lst_dcid != 11:
                            while lst_dcid!=11:
                                destination.append(0)
                                lst_dcid+=1
                        destination.append(failRate)
                        output.append(destination)
                    batchId+=1
                    destination = [batchId]
                    failRate = 0
                    lst_submittime = destination_data[testAlgorithm][i][0]
                    lst_dcid = -1
                while float(destination_data[testAlgorithm][i][1]) != float(lst_dcid):
                    if lst_dcid==-1:
                        lst_dcid = 1
                    else:
                        destination.append(0)
                        lst_dcid+=1
                if lst_dcid==-1:
                    failRate = destination_data[testAlgorithm][i][3]
                    lst_dcid = 1
                else:
                    destination.append(destination_data[testAlgorithm][i][3])
                    lst_dcid+=1
            while lst_dcid!=11:
                destination.append(0)
                lst_dcid+=1
            destination.append(failRate)
            output.append(destination)
            execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.'+testAlgorithm+'.SUM.destination_data.csv')
            print("process schedule destination data successfully")

        # Overall CPU, RAM, bandwidth resource utilization
        output = []
        tmp_header = [""]
        tmp_header.extend(header)
        output.append(tmp_header)
        cpuUtilization = ["CPU Util"]
        ramUtilization = ["RAM Util"]
        bwUtilization = ["BW Util"]
        for algorithm in testAlgorithmList:
            cpuUtilization.append(total_used_resource_data[algorithm][0][2])
            ramUtilization.append(total_used_resource_data[algorithm][0][5])
            bwUtilization.append(total_used_bw_resource_data[algorithm][0][2])
        output.append(cpuUtilization)
        output.append(ramUtilization)
        output.append(bwUtilization)
        execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.SUM.total_used_resource_data.csv')
        print("process total used resource data successfully")

        # Resource utilization rate by region
        output = []
        cpuUtilization = []
        ramUtilization = []
        bwUtilization = []
        maxLen = 0
        for algorithm in testAlgorithmList:
            tmp_cpuUtilization = []
            tmp_ramUtilization = []
            tmp_bwUtilization = []
            for sub_data in used_resource_data[algorithm]:
                tmp_cpuUtilization.append(sub_data[3])
                tmp_ramUtilization.append(sub_data[6])
            for sub_data in used_bw_resource_data[algorithm]:
                tmp_bwUtilization.append(sub_data[4])
            maxLen = max(len(tmp_cpuUtilization), len(tmp_ramUtilization), len(tmp_bwUtilization))
        for algorithm in testAlgorithmList:
            tmp_cpuUtilization = []
            tmp_ramUtilization = []
            tmp_bwUtilization = []
            for sub_data in used_resource_data[algorithm]:
                tmp_cpuUtilization.append(sub_data[3])
                tmp_ramUtilization.append(sub_data[6])
            for sub_data in used_bw_resource_data[algorithm]:
                tmp_bwUtilization.append(sub_data[4])
            # For each algorithm, make up the length of its cpu, ram, and bw data
            tmp_cpuUtilization += [None] * (maxLen-len(tmp_cpuUtilization))
            tmp_ramUtilization += [None] * (maxLen-len(tmp_ramUtilization))
            tmp_bwUtilization += [None] * (maxLen-len(tmp_bwUtilization))
            cpuUtilization.append(tmp_cpuUtilization)
            ramUtilization.append(tmp_ramUtilization)
            bwUtilization.append(tmp_bwUtilization)
        # cpuUtilization, ramUtilization, bwUtilization store various resources for each algorithm under each data centre respectively
        # Put cpu, ram, bw data together by algorithm
        output = [item for sublist in zip(cpuUtilization, ramUtilization, bwUtilization) for item in sublist]
        # Transpose them so that they are in datacentre order (after transposition, first dimension: cpu, ram, bw for each algorithm, second dimension: datacentre/datacentre pair number)
        output = list(map(list, zip(*output)))
        # The tmp_header_algorithm element is the element in the header repeated three times in succession
        tmp_header_algorithm = [item for sublist in zip(header, header, header) for item in sublist]
        # tmp_header elements are "cpuUtilisation", "ramUtilisation", "bwUtilisation" repeated n times where n is the number of header elements
        tmp_header = ["cpuUtilization", "ramUtilization", "bwUtilization"] * len(header)
        output.insert(0, tmp_header)
        output.insert(0, tmp_header_algorithm)
        execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.SUM.used_resource_data.csv')
        print("process used resource data successfully")

        # TCO
        output = []
        tmp_header = []
        tmp_header.extend(header)
        output.append(tmp_header)
        TCOData = []
        for algorithm in testAlgorithmList:
            TCOData.append(data[testEx][algorithm]["TCO"][0][0])
        output.append(TCOData)
        execute_and_export(output, '../RecordDb/'+dirPrefix+testEx+'.SUM.TCO_data.csv')
        print("process TCO data successfully")

def main():
    dirPrefixList = []
    for i in range(1, 11):
        global dirPrefix
        dirPrefix = "test-"+str(i)+"/"
        dirPrefixList.append(dirPrefix)
        
        # GET DATA from DB or CSV
        get_data()
        # get_data_from_csv()

        # PROCESS DATA
        process_data()

        # STORE DATA
        global batchData
        batchData[dirPrefix] = copy.deepcopy(data)
    dirPrefix = "SUM-"+str(1)+"-"+str(10)+"/"
    get_batch_data(dirPrefixList)
    if not os.path.exists('../RecordDb/'+dirPrefix):
        os.makedirs('../RecordDb/'+dirPrefix)
    process_data()
    

if __name__ == '__main__':
    main()

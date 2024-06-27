#!/bin/bash

# mvn 4.0.0
# java 17.0.10

homeDir="/home/xyh/LGDCloudSim"
scriptDir=$homeDir"/scripts"
dbDir=$homeDir"/RecordDb"
javaFile=$homeDir"/src/main/java/org/example/Lattice.java"
javaPkg="org.example.Lattice"
fileSuf="example"
dirSuf=""

list_testEx=("1-overall" "2-ablation")
list_testAlgorithm=("1-Lattice" "2-Diktyo" "3-TanGo" "4-DelayFirst" "5-Lattice_woP" "6-Lattice_woC" "7-Lattice_woP&C")

echo ""
echo ""
# echo "The current time is: $(date +'%Y-%m-%d %H:%M:%S')"
echo "[Compiling...]"
cd $homeDir
mvn clean compile
echo "[Compilation complete!]"
echo ""
echo ""

if [ ! -d "$scriptDir/logs/" ]; then
  mkdir -p $scriptDir/logs/
fi
if [ ! -d "$dbDir/" ]; then
  mkdir -p $dbDir/
fi

# Record start time (initial value 0)
all_start_seconds=$SECONDS

for i in {1..10}; do
  dirSuf="test-$i/"
  echo "[Running $dirSuf ...]"
  mkdir -p $scriptDir/logs/$dirSuf
  mkdir -p $dbDir/$dirSuf

  # Record start time (initial value 0)
  start_seconds=$SECONDS

  # Iterate through each list and pass the elements as parameters to the Java program
  for testEx in "${list_testEx[@]}"; do
    if [ $testEx = "1-overall" ]; then
      list_testAlgorithm=("1-Lattice" "2-Diktyo" "3-TanGo" "4-DelayFirst")
    elif [ $testEx = "2-ablation" ]; then
      list_testAlgorithm=("5-Lattice_woP" "6-Lattice_woC" "7-Lattice_woP&C") 
    fi      
    for testAlgorithm in "${list_testAlgorithm[@]}"; do

      echo "[Running $testEx-$testAlgorithm ...]"
      start_seconds_tmp=$SECONDS
      mvn exec:java -Dexec.mainClass="$javaPkg" -Dexec.args="$testEx $testAlgorithm $fileSuf $dirSuf" > $scriptDir/logs/${dirSuf}$testEx-$testAlgorithm-$fileSuf.log 2>&1 
      echo "[Finish running $testEx-$testAlgorithm-$fileSuf-$dirSuf! Spend $((SECONDS - start_seconds_tmp)) s.]"
      echo ""
    done
  done
  cp $scriptDir/logs/${dirSuf}1-overall-1-Lattice-$fileSuf.log $scriptDir/logs/${dirSuf}2-ablation-1-Lattice-$fileSuf.log
  cp $dbDir/${dirSuf}1-overall.1-Lattice.$fileSuf.db $dbDir/${dirSuf}2-ablation.1-Lattice.$fileSuf.db
  cp $scriptDir/nohup.out $scriptDir/logs/${dirSuf}nohup.out

  echo "[Finish a round of all algorithm runs! Spend $((SECONDS - start_seconds)) s.]"
  echo ""
  echo ""

done

echo "[Complete all ten loops! Spend $((SECONDS - all_start_seconds)) s.]"
echo ""
echo ""
echo "---"

python3 $scriptDir/dataSolve.py
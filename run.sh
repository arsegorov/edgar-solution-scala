#!/bin/bash

declare grn="\e[1;49;32m"
declare nrm="\e[0;39;49m"
declare info="[${grn}info${nrm}]\t"

# Building
if [ ! -f edgar.jar ]; then
    ./build.sh
fi

# Running
# default arguments
args=( )
args[1]=./input/log.csv
args[2]=./input/inactivity_period.txt
args[3]=./output/sessionization.txt

# substituting the passed arguments
for i in 1 2 3; do
    if [ ! -z ${!i} ]; then
        args[i]=${!i}
    fi
done

echo -e "${info}Running the program ... "
scala edgar.jar ${args[1]} ${args[2]} ${args[3]}

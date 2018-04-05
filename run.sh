#!/bin/bash

declare cyan="\e[1;49;36m"
declare grn="\e[1;49;32m"
declare nrm="\e[0;39;49m"
declare info="[${grn}info${nrm}]\t"
declare done="Done"

dest=out

# Building
if [ ! -f edgar.jar ]; then
    # Cleaning ./bin, ./output
    for d in ${dest}; do
        if [ -e ${d} ]; then
            echo -e "${info}Cleaning out ${cyan}$d/${nrm}"
            cd ${d}
            for f in $(ls -A); do
                rm -r ${f}
            done
            cd ..
        else
            echo -e "${info}Creating ${cyan}$d/${nrm}"
            mkdir ${d}
        fi
    done

    # Compiling
    echo -en "${info}Compiling sources ... "
    scalac -sourcepath src/main/scala/edgar/ -d ./${dest}/ src/main/scala/edgar/*
    echo "Done"

    # Packaging
    echo -en "${info}Packaging ${cyan}edgar.jar${nrm} ... "
    cd ${dest}
    jar -cfm ../edgar.jar ../src/main/scala/META-INF/MANIFEST.MF *
    cd ..
    echo "Done"
fi

# Running
# Setting default arguments
args=( )
args[1]=./input/log.csv
args[2]=./input/inactivity_period.txt
args[3]=./output/sessionization.txt

# substituting the passed arguments
for i in 1..3; do
    if [ ! -z ${!i} ]; then
        args[i]=${!i}
    fi
done

echo -e "${info}Running the program ... "
scala edgar.jar ${args[1]} ${args[2]} ${args[3]}

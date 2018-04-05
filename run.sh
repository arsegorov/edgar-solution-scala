#!/bin/bash

# Output decorations
declare -r cyan="\e[1;49;36m"
declare -r grn="\e[1;49;32m"
declare -r nrm="\e[0;39;49m"
declare -r info="[${grn}info${nrm}]\t"

# Paths
declare -r scala_in=src/main/scala
declare -r scala_src=${scala_in}/edgar
declare -r scala_out=out
declare -r jar=edgar.jar

# Building
if [ ! -f edgar.jar ]; then
    # Preparing directories
    for d in ${scala_out}; do
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
    echo -en "${info}Compiling sources to ${cyan}${scala_out}/${nrm} ... "
    scalac -sourcepath ${scala_src}/ -d ./${scala_out}/ ${scala_src}/*
    echo "Done"

    # Packaging
    echo -en "${info}Packaging ${cyan}${jar}${nrm} ... "
    cd ${scala_out}
    jar -cfm ../${jar} ../${scala_in}/META-INF/MANIFEST.MF *
    cd ..
    echo "Done"

    # Cleaning out compiler output
    echo -e "${info}Removing ${cyan}${scala_out}/${nrm}"
    rm -r ${scala_out}
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

echo -e "${info}Running the program, ${cyan}${jar}${nrm}"
scala ${jar} ${args[1]} ${args[2]} ${args[3]}

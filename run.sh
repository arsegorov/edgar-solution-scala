#!/bin/bash
declare yel="\e[1;49;33m"
declare cyan="\e[1;49;36m"
declare grn="\e[1;49;32m"
declare nrm="\e[0;39;49m"
declare info="[${grn}info${nrm}]\t"
declare done="\t${grn}Done${nrm}"

# Cleaning ./bin, ./lib
for d in bin lib output; do
    if [ -e ${d} ]; then
        echo -e "${info}Cleaning out directory ${cyan}./$d/${nrm} ... "
        cd ${d}
        for f in $(ls -A); do
            rm -r ${f}
        done
        cd ..
        echo -e ${done}
    else
        echo -e "${info}Creating directory ./$d/"
        mkdir ${d}
    fi
done

# Adding the library
echo -e "${info}Copying the scala library from ${cyan}./src/lib/${nrm} to ${cyan}./lib/${nrm} ... "
cp -r ./src/lib/* ./lib/
echo -e ${done}

# Compiling
echo -e "${info}Compiling the sources from ${cyan}./src/main/scala/edgar/${nrm} to ${cyan}./bin/${nrm} ... "
scalac -sourcepath src/main/scala/edgar/ -d ./bin/ src/main/scala/edgar/*
echo -e ${done}

# Packaging
echo -e "${info}Packaging the classes from ${cyan}./bin/${nrm} to ${cyan}./edgar.jar${nrm} ... "
cd bin
jar -cfm ../edgar.jar ../src/main/scala/META-INF/MANIFEST.MF *
cd ..
echo -e ${done}

# Running
echo -e "${info}Running the program ... "
java -jar edgar.jar ./input/log.csv ./input/inactivity_period.txt ./output/sessionization.txt

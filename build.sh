#!/bin/bash

declare cyan="\e[1;49;36m"
declare grn="\e[1;49;32m"
declare nrm="\e[0;39;49m"
declare info="[${grn}info${nrm}]\t"
declare done="Done"

# Cleaning ./bin, ./output
for d in bin output; do
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
scalac -sourcepath src/main/scala/edgar/ -d ./bin/ src/main/scala/edgar/*
echo "Done"

# Packaging
echo -en "${info}Packaging ${cyan}edgar.jar${nrm} ... "
cd bin
jar -cfm ../edgar.jar ../src/main/scala/META-INF/MANIFEST.MF *
cd ..
echo "Done"

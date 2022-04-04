#!/bin/bash
curl -L -o buf1 https://github.com/bufbuild/buf/releases/download/v1.1.1/buf-Linux-x86_64
chmod +x buf1 
./buf1 --version 
git fetch origin packageTest2
git checkout packageTest2
git status 
EXIT_CODE=0
output=""
IFS=$'\n' bufOut=($(./buf1 breaking --against '.git#branch=packageTest'))
for m in $(git log packageTest..packageTest2 --format=format:%H )
do
    echo $m
    for k in $(git show --pretty="" --name-only $m | grep \.proto$)
    do         
        echo $k
        
        for l in "${bufOut[@]}"
        do
            if grep -q "$k" <<< "$l";
            then
                output+=$l
                output+="\n"
                echo $l
                EXIT_CODE=100
            fi
        done
    done     
echo $EXIT_CODE >> exit_code.txt
echo $output >> output.txt                                                                     
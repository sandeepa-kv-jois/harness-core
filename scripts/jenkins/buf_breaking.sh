#!/bin/bash
curl -L -o buf1 https://github.com/bufbuild/buf/releases/download/v1.1.0/buf-Linux-x86_64 
chmod +x buf1 
./buf1 --version 
git fetch origin buf_test 
git checkout buf_test 
git status 
EXIT_CODE=0                                 
output=""
IFS=$'\n' bufOut=($(./buf1 breaking --against '.git#branch=develop'))
for m in $(git log develop..buf_test --find-renames --format=format:%H  --diff-filter=R)
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
done

for i in $(git log develop..buf_test --format=format:%H)
do
echo $i
for j in $(git show --pretty="" --name-only $i | grep \.proto$)
do
    git cat-file -e develop:$j 2> /dev/null
    e=$?
    if [ $e -eq 0 ]     
    then
        echo $j;
        o=$(./buf1 breaking --against '.git#branch=develop' --path $j )
        TEMP_EXIT_CODE=$?
        if [ -z "$TEMP_EXIT_CODE" ]
        then
        echo "No Breaking Change"
        else
        if [ $TEMP_EXIT_CODE -ne 0 ]
        then
            echo $o
            output+=$o
            output+="\n"
            EXIT_CODE=$TEMP_EXIT_CODE
            echo $EXIT_CODE
        fi
        fi
    fi
    done
done
echo $EXIT_CODE >> exit_code.txt
echo $output >> output.txt

EXIT_CODE=0
for i in $(git log develop..F3 --format=format:%H)
do 
echo $i
for j in $(git show --pretty="" --name-only $i | grep \.proto$)
do
    echo $j;
    buf breaking --against '.git#branch=develop' --path $j || TEMP_EXIT_CODE=$? 
    if [ -z "$TEMP_EXIT_CODE" ]
    then
    echo "No Breaking Change"
    else
    if [ $TEMP_EXIT_CODE -ne 0 ]
    then
        EXIT_CODE=$TEMP_EXIT_CODE
        echo $EXIT_CODE
    fi 
    fi
    done
done 
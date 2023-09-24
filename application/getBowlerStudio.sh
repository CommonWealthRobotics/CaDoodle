#!/bin/bash

mkdir -p lib/
FILE=lib/BowlerStudio.jar
if test -f "$FILE"; then
    echo "$FILE exists."
else
    wget https://github.com/CommonWealthRobotics/BowlerStudio/releases/latest/download/BowlerStudio.jar -O $FILE
fi

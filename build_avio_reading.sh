#!/bin/bash

# Needs 2 args
if [ -z "$2" ]
then
	echo "usage: "$0" <path_to_javacpp_classes> <path_to_javacpp_ffmpeg_classes>"
	exit 1
fi

# Clean existing target dir
rm -rf target
# Make new dir structure w "parents" flag
mkdir -p target/classes/clone206/examples/javacpp_ffmpeg

# Copy necessary javacpp class files
cp -R $1/org target/classes/
cp -R $2/org target/classes/

# Compile
javac -d target/classes -classpath target/classes src/main/java/clone206/examples/javacpp_ffmpeg/AVIOReading.java

# Remove pesky mac files
find target/classes -iname .DS_Store -print0 |xargs -0 rm -f

# Bundle all classes into jar file, include manifest
jar -cvfm avio_reading.jar manifest_avio_reading.mf -C target/classes .

exit 0

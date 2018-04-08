# A java implementation of ffmpeg's example utilities

For now, just the [avio_reading](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/avio_reading.c) ffmpeg sample program. Makes use of [javacpp-presets/ffmpeg](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg) JNI bindings for [ffmpeg](https://www.ffmpeg.org/).

Depends on [maven](https://maven.apache.org) for building.

## Building 

`mvn package`

## Running

`java -jar target/avio_reading-1.0.jar <media_file>`

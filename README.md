# A java implementation of ffmpeg's example utilities

The [avio_reading](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/avio_reading.c) (API example program to show how to read from a custom buffer accessed through AVIOContext) and [transcode_aac](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/transcode_acc.c) (Convert an input audio file to AAC in an MP4 container) ffmpeg sample programs. Makes use of [javacpp-presets/ffmpeg](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg) JNI bindings for [ffmpeg](https://www.ffmpeg.org/).

Depends on [maven](https://maven.apache.org) for building.

## Building 

`mvn package`

## Running

`java -jar target/avio_reading.jar <media_file>`


`java -jar target/transcode_aac.jar <input_audio_file> <output_mp4>`

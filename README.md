# A java implementation of ffmpeg's example utilities

For now, just the [avio_reading](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/avio_reading.c) ffmpeg sample program. Makes use of [javacpp-presets/ffmpeg](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg) JNI bindings for [ffmpeg](https://www.ffmpeg.org/).

Before following the below building instructions, make sure you have javacpp and the ffmpeg javacpp preset installed. You will then need to provide paths to the classes for both of them during building.

## Building 

*nix/macos:

`./build_avio_reading.sh <path_to_javacpp_classes> <path_to_javacpp_ffmpeg_classes>`

## Running

`java -jar avio_reading.jar <media_file>`

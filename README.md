# A java implementation of ffmpeg's example utilities

Includes the following ffmpeg sample programs:

- [avio_reading](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/avio_reading.c) (API example program to show how to read from a custom buffer accessed through AVIOContext)

- [transcode_aac](https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/transcode_acc.c) (Convert an input audio file to AAC in an MP4 container)

- [transcoding](https://github.com/FFmpeg/FFmpeg/blob/n3.4.1/doc/examples/transcoding.c) (API example for demuxing, decoding, filtering, encoding and muxing. Java code for the transcoding sample program originally by [hullarb](https://github.com/hullarb/javacpp-ffmpeg-example))


Makes use of [javacpp-presets/ffmpeg](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg) JNI bindings for [ffmpeg](https://www.ffmpeg.org/).

Depends on [maven](https://maven.apache.org) for building.

## Building 

### Jars with dependencies for the currently running OS:

`mvn package`

### Jars with dependencies for all supported OS's:

`mvn package -Dall_platforms`

## Running

`java -jar target/avio_reading.jar <media_file>`


`java -jar target/transcode_aac.jar <input_audio_file> <output_mp4>`

`java -jar target/transcoding.jar <input_movie> <output_movie_mp4>`

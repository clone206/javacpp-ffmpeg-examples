/* Copyright 2018 Kevin Witmer
 *
 * Code based on an example program by Andreas Unterweger
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package clone206.examples.javacpp_ffmpeg;

import java.io.*;
import org.bytedeco.javacpp.*;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swresample.*;

/**
 * java javacpp-presets/ffmpeg translation of the transcoding ffmpeg official example
 * https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/transcode_acc.c
 * 
 * Depends on javacpp and the ffmpeg javacpp preset,
 * with classpaths set accordingly on compile/run
 *
 * @author Kevin Witmer
 *
 * @file
 * simple audio converter
 *
 * @example transcode_aac.c
 * Convert an input audio file to AAC in an MP4 container using FFmpeg.
 */

public class TranscodeAAC {
    /** The output bit rate in kbit/s */
    public static final int OUTPUT_BIT_RATE = 96000;
    /** The number of output channels */
    public static final byte OUTPUT_CHANNELS = 2;

    static void check(int err) {
        if (err < 0) {
            BytePointer e = new BytePointer(512);
            av_strerror(err, e, 512);
            throw new RuntimeException(e.getString().substring(0, (int) BytePointer.strlen(e)) + ":" + err);
        }
    }

    static void openInput(String filename, AVFormatContext input_format_context, AVCodecContext input_codec_context) {
        check( avformat_open_input(input_format_context, filename, null, null) );
        check( avformat_find_stream_info(input_format_context, (PointerPointer) null) );

        if (input_format_context.nb_streams() != 1) {
            throw new RuntimeException("Expected one audio input stream, but found " + input_format_context.nb_streams());
        }

        AVCodec input_codec = avcodec_find_decoder( input_format_context.streams(0).codecpar().codec_id() );
        
        if (input_codec == null) {
            new RuntimeException("Could not find decoder for codec: " + input_format_context.streams(0).codecpar().codec_id());
        }

        AVCodecContext avctx = avcodec_alloc_context3(input_codec);

        check( avcodec_parameters_to_context(avctx, input_format_context.streams(0).codecpar()) );
        check( avcodec_open2(avctx, input_codec, (AVDictionary) null) );

        input_codec_context = avctx;
    }

    public static void main(String[] args) throws IOException {
        AVFormatContext input_format_context = new AVFormatContext(null);
        AVCodecContext input_codec_context = new AVCodecContext(null);
        
        if (args.length < 2) {
            System.err.println("\nexample usage: ");
            System.err.println("java -jar transcode_aac.jar <input_file> <output_file>");
            System.exit(-1);
        }
        
        // Register all formats and codecs
        av_register_all();

        openInput(args[0], input_format_context, input_codec_context);
        
        if (input_codec_context != null)
            avcodec_free_context(input_codec_context);
        
        avformat_close_input(input_format_context);

        System.exit(0);
    }
}

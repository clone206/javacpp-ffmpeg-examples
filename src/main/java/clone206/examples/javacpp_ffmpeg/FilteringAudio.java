/* Copyright 2018 Kevin Witmer
 *
 * Code based on an example program by Nicolas George, Stefano Sabatini, and Clément Bœsch.
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
import java.nio.*;
import org.bytedeco.javacpp.*;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avfilter.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

/**
 * @file
 * API example for audio decoding and filtering
 * @example filtering_audio.c
 */
public class FilteringAudio {
    public static final String filter_descr = "aresample=8000,aformat=sample_fmts=s16:channel_layouts=mono";
    public static final String player = "ffplay -f s16le -ar 8000 -ac 1 -";

    static AVFormatContext fmt_ctx = new AVFormatContext(null);
    static AVCodecContext dec_ctx = new AVCodecContext(null);
    static AVFilterContext buffersink_ctx = new AVFilterContext(),
                           buffersrc_ctx = new AVFilterContext();
    static AVFilterGraph filter_graph = new AVFilterGraph();
    static int audio_stream_index = -1;

    /* Custom implementation of missing av_err2str() ffmpeg function */
    static String my_av_err2str (int err) {
        BytePointer e = new BytePointer(512);
        av_strerror(err, e, 512);
        return e.getString().substring(0, (int) BytePointer.strlen(e));
    }
    
    /* Check for error code returned by ffmpeg func and throw error */
    static void check (int err) {
        if (err < 0) {
            throw new RuntimeException(my_av_err2str(err) + ":" + err);
        }
    }

    /* Open an input file and the required decoder. */
    static void open_input_file (String filename) {
        AVCodec dec = new AVCodec();
        
        /* Open the input file to read from it. */
        check( avformat_open_input(fmt_ctx, filename, null, null) );
        /* Get information on the input file (number of streams etc.). */
        check( avformat_find_stream_info(fmt_ctx, (PointerPointer) null) );

        /* select the audio stream */
        check( audio_stream_index = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, dec, 0) );

        /* create decoding context */
        dec_ctx = avcodec_alloc_context3(dec);

        if (dec_ctx.isNull()) {
            throw new RuntimeException( "Error: " + my_av_err2str(AVERROR_ENOMEM()) );
        }

        check( avcodec_parameters_to_context(dec_ctx, fmt_ctx.streams(audio_stream_index).codecpar()) );
        av_opt_set_int(dec_ctx, "refcounted_frames", 1, 0);

        /* init the audio decoder */
        check( avcodec_open2(dec_ctx, dec, (AVDictionary) null) );
    }

    static void init_filters (String filters_descr) {
        BytePointer args = new BytePointer(512);
        AVFilter abuffersrc  = avfilter_get_by_name("abuffer"),
                 abuffersink = avfilter_get_by_name("abuffersink");
        AVFilterInOut outputs = avfilter_inout_alloc(),
                      inputs  = avfilter_inout_alloc();

        /* Some pointers to arrays for setting binary filter options */
        BytePointer out_sample_fmts = new BytePointer(8);
        out_sample_fmts.putInt(0, AV_SAMPLE_FMT_S16);
        out_sample_fmts.putInt(4, -1);

        BytePointer out_channel_layouts = new BytePointer(16);
        out_channel_layouts.putLong(0, AV_CH_LAYOUT_MONO);
        out_channel_layouts.putLong(8, -1);
        
        BytePointer out_sample_rates = new BytePointer(8);
        out_sample_rates.putInt(0, 8000);
        out_sample_rates.putInt(4, -1);

        AVFilterLink outlink = new AVFilterLink();
        AVRational time_base = fmt_ctx.streams(audio_stream_index).time_base();

        try {
            filter_graph = avfilter_graph_alloc();

            if (outputs.isNull() || inputs.isNull() || filter_graph.isNull()) {
                throw new RuntimeException(my_av_err2str( AVERROR_ENOMEM() ) + ":" + AVERROR_ENOMEM());
            }

            /* buffer audio source: the decoded frames from the decoder will be inserted here. */ 
            if (dec_ctx.channel_layout() == 0) {
                dec_ctx.channel_layout( av_get_default_channel_layout(dec_ctx.channels()) );
            }

            args.putString(String.format(
                        "time_base=%d/%d:sample_rate=%d:sample_fmt=%s:channel_layout=0x%x",
                        time_base.num(), time_base.den(), dec_ctx.sample_rate(),
                        av_get_sample_fmt_name(dec_ctx.sample_fmt()).getString(), dec_ctx.channel_layout()
            ));

            System.err.println( "filter args: " + args.getString() );

            check( avfilter_graph_create_filter(buffersrc_ctx, abuffersrc, new BytePointer("in"), args, null, filter_graph) );

            /* buffer audio sink: to terminate the filter chain. */
            check( avfilter_graph_create_filter(buffersink_ctx, abuffersink, "out", null, null, filter_graph) );

            /* Set binary options */
            check( av_opt_set_bin(buffersink_ctx, "sample_fmts", out_sample_fmts, 4, AV_OPT_SEARCH_CHILDREN) );
            check( av_opt_set_bin(buffersink_ctx, "channel_layouts", out_channel_layouts, 8, AV_OPT_SEARCH_CHILDREN) );
            check( av_opt_set_bin(buffersink_ctx, "sample_rates", out_sample_rates, 4, AV_OPT_SEARCH_CHILDREN) );

            /*
             * Set the endpoints for the filter graph. The filter_graph will
             * be linked to the graph described by filters_descr.
             */

            /*
             * The buffer source output must be connected to the input pad of
             * the first filter described by filters_descr; since the first
             * filter input label is not specified, it is set to "in" by
             * default.
             */
            outputs.name( new BytePointer("in") );
            outputs.filter_ctx(buffersrc_ctx);
            outputs.pad_idx(0);
            outputs.next(null);

            /*
             * The buffer sink input must be connected to the output pad of
             * the last filter described by filters_descr; since the last
             * filter output label is not specified, it is set to "out" by
             * default.
             */
            inputs.name( new BytePointer("out") );
            inputs.filter_ctx(buffersink_ctx);
            inputs.pad_idx(0);
            inputs.next(null);

            check( avfilter_graph_parse_ptr(filter_graph, filters_descr, inputs, outputs, null) );
            check( avfilter_graph_config(filter_graph, null) );

            /* Print summary of the sink buffer
             * Note: args buffer is reused to store channel layout string */
            args = new BytePointer(512); // Clear out args string for reuse
            outlink = buffersink_ctx.inputs(0);
            av_get_channel_layout_string( args, (int) args.capacity(), -1, outlink.channel_layout() );

            System.err.println( String.format(
                "Output: srate:%dHz fmt:%s chlayout:%s\n",
                outlink.sample_rate(),
                ( av_get_sample_fmt_name( outlink.format() ) ).getString(),
                args.getString()
            ) );
        }
        finally {
            avfilter_inout_free(inputs);
            avfilter_inout_free(outputs);
        }
    }

    static void print_frame (final AVFrame frame) throws IOException {
        final int n = frame.nb_samples() * av_get_channel_layout_nb_channels( frame.channel_layout() );
        byte[] sample_bytes = new byte[2];


        for (int i = 0; i <= n*2 - 2; i += 2) {
            sample_bytes[0] = frame.data(0).get(i);
            sample_bytes[1] = frame.data(0).get(i+1);
            System.out.write( sample_bytes );
        }
    }

    public static void main (String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: filtering_audio.java <file> | " + player);
            System.exit(-1);
        }

        int ret = 0;
        AVPacket packet = new AVPacket();
        AVFrame frame = av_frame_alloc(),
                filt_frame = av_frame_alloc();
           

        try {
            if (frame.isNull() || filt_frame.isNull()) {
                throw new RuntimeException("Could not allocate frame");
            }
            
            av_register_all();
            avfilter_register_all();

            open_input_file(args[0]);
            init_filters(filter_descr);

            /* read all packets */
            while (true) {
                if ( (ret = av_read_frame(fmt_ctx, packet)) < 0 ) {
                    break;
                }

                if (packet.stream_index() == audio_stream_index) {
                    if ((ret = avcodec_send_packet(dec_ctx, packet)) < 0) {
                        break;
                    }

                    while (ret >= 0) {
                        /* push the audio data from decoded frame into the filtergraph */
                        ret = avcodec_receive_frame(dec_ctx, frame);

                        if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) {
                            break;
                        }
                        else if (ret < 0) {
                            throw new RuntimeException("Error while receiving frame from the decoder");
                        }

                        if (ret >= 0) {
                            /* push the audio data from decoded frame into the filtergraph */
                            if (av_buffersrc_add_frame_flags(buffersrc_ctx, frame, AV_BUFFERSRC_FLAG_KEEP_REF) < 0) {
                                System.err.println("Error while feeding the audio filtergraph");
                                break;
                            }

                            /* pull filtered audio from the filtergraph */
                            while (true) {
                                ret = av_buffersink_get_frame(buffersink_ctx, filt_frame);

                                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) {
                                    break;
                                }
                                if (ret < 0) {
                                    throw new RuntimeException("Couldn't get frame from filtergraph");
                                }
                                print_frame(filt_frame);
                                av_frame_unref(filt_frame);
                            }
                            av_frame_unref(frame);
                        }
                    }
                }
                av_packet_unref(packet);
            }
        }
        finally {
            avfilter_graph_free(filter_graph);
            avcodec_free_context(dec_ctx);
            avformat_close_input(fmt_ctx);
            av_frame_free(frame);
            av_frame_free(filt_frame);

            if (ret < 0 && ret != AVERROR_EOF) {
                System.err.println("Error occurred: " + my_av_err2str(ret));
                System.exit(-1);
            }

            System.exit(0);
        }
    }
}

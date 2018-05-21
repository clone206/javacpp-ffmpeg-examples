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
import static org.bytedeco.javacpp.avcodec.AVCodecContext.FF_COMPLIANCE_EXPERIMENTAL;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swresample.*;

/**
 * java javacpp-presets/ffmpeg translation of the transcode_aac ffmpeg official example
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
    /* The output bit rate in kbit/s */
    public static final int OUTPUT_BIT_RATE = 96000;
    /* The number of output channels */
    public static final byte OUTPUT_CHANNELS = 2;

    // Making these objects (and the below primitives) global avoids pass-by-value gotchas
    static AVFormatContext input_format_context = new AVFormatContext(null),
                           output_format_context = new AVFormatContext(null);
    static AVCodecContext input_codec_context = new AVCodecContext(null),
                          output_codec_context = new AVCodecContext(null);
    static SwrContext resample_context = new SwrContext(null);
    static AVAudioFifo fifo = new AVAudioFifo(null);
    static AVFrame input_frame = av_frame_alloc(),
                   output_frame = av_frame_alloc();
    static PointerPointer<?> converted_input_samples = new PointerPointer<>((long) OUTPUT_CHANNELS);
    static AVPacket input_packet = new AVPacket(),
                    output_packet = new AVPacket();
    static IntPointer data_present = new IntPointer((long) 1),
                      data_written = new IntPointer((long) 1);

    // For deciding when we've exhausted a buffer
    static int finished = 0;
    /* Global timestamp for the audio frames */
    static long pts = 0;

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
    static void openInput (String filename) {
        /* Open the input file to read from it. */
        check( avformat_open_input(input_format_context, filename, null, null) );
        /* Get information on the input file (number of streams etc.). */
        check( avformat_find_stream_info(input_format_context, (PointerPointer) null) );

        /* Make sure that there is only one stream in the input file. */
        if (input_format_context.nb_streams() != 1) {
            throw new RuntimeException("Expected one audio input stream, but found " + input_format_context.nb_streams());
        }

        /* Find a decoder for the audio stream. */
        AVCodec input_codec = avcodec_find_decoder( input_format_context.streams(0).codecpar().codec_id() );
        
        if (input_codec == null) {
            new RuntimeException("Could not find decoder for codec: " + input_format_context.streams(0).codecpar().codec_id());
        }

        /* allocate a new decoding context */
        AVCodecContext avctx = avcodec_alloc_context3(input_codec);

        /* initialize the stream parameters with demuxer information */
        check( avcodec_parameters_to_context(avctx, input_format_context.streams(0).codecpar()) );
        /* Open the decoder for the audio stream to use it later. */
        check( avcodec_open2(avctx, input_codec, (AVDictionary) null) );

        /* Save the decoder context for easier access later. */
        input_codec_context = avctx;
    }

    /*
     * Open an output file and the required encoder.
     * Also set some basic encoder parameters.
     * Some of these parameters are based on the input file's parameters.
     */
    static void openOutput (String filename) {
        AVCodecContext avctx = new AVCodecContext(null);
        AVIOContext output_io_context = new AVIOContext(null);
        AVStream stream = new AVStream(null);
        AVCodec output_codec = new AVCodec(null);

        /* Create a new format context for the output container format. */
        check( avformat_alloc_output_context2(output_format_context, null, null, filename) );
        /* Open the output file to write to it. */
        check( avio_open(output_io_context, filename, AVIO_FLAG_WRITE) );
        /* Associate the output file (pointer) with the container format context. */
        output_format_context.pb( output_io_context );

        /* Find the encoder to be used by its name. */
        if ( (output_codec = avcodec_find_encoder(AV_CODEC_ID_AAC)).isNull() ) {
            throw new RuntimeException("Could not find AAC encoder");
        }
        
        /* Create a new audio stream in the output file container. */
        if ( (stream = avformat_new_stream(output_format_context, null)).isNull() ) {
            throw new RuntimeException("Could not create new stream");
        }

        if ( (avctx = avcodec_alloc_context3(output_codec)).isNull() ) {
            throw new RuntimeException("Could not allocate an encoding context");
        }

        /*
         * Set the basic encoder parameters.
         * The input file's sample rate is used to avoid a sample rate conversion.
         */
        avctx.channels(OUTPUT_CHANNELS);
        avctx.channel_layout(av_get_default_channel_layout(OUTPUT_CHANNELS));
        avctx.sample_rate(input_codec_context.sample_rate());
        avctx.sample_fmt(output_codec.sample_fmts().get(0));
        avctx.bit_rate(OUTPUT_BIT_RATE);

        /* Allow the use of the experimental AAC encoder */
        avctx.strict_std_compliance(FF_COMPLIANCE_EXPERIMENTAL);

        /* Set the sample rate for the container. */
        stream.time_base(new AVRational());
        stream.time_base().den( input_codec_context.sample_rate() );
        stream.time_base().num( 1 );

        /*
         * Some container formats (like MP4) require global headers to be present
         * Mark the encoder so that it behaves accordingly.
         */
        if ( (output_format_context.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
            avctx.flags( avctx.flags() | AV_CODEC_FLAG_GLOBAL_HEADER );
        }

        /* Open the encoder for the audio stream to use it later. */
        check( avcodec_open2(avctx, output_codec, (AVDictionary) null) );
        /* Initialize stream params */
        check( avcodec_parameters_from_context(stream.codecpar(), avctx) );

        /* Save the encoder context for easier access later. */
        output_codec_context = avctx;
    }

    /* Initialize one data packet for reading. */
    static void init_input_packet () {
        /* Set the packet data and size so that it is recognized as being empty. */
        input_packet.data(null);
        input_packet.size(0);
        av_init_packet(input_packet);
    }

    /* Initialize one data packet for writing. */
    static void init_output_packet () {
        /* Set the packet data and size so that it is recognized as being empty. */
        output_packet.data(null);
        output_packet.size(0);
        av_init_packet(output_packet);
    }

    /*
     * Initialize the audio resampler based on the input and output codec settings.
     * If the input and output sample formats differ, a conversion is required
     * libswresample takes care of this, but requires initialization.
     */
    static void init_resampler () {
        /*
         * Create a resampler context for the conversion.
         * Set the conversion parameters.
         * Default channel layouts based on the number of channels
         * are assumed for simplicity (they are sometimes not detected
         * properly by the demuxer and/or decoder).
         */
        if ( (resample_context = swr_alloc_set_opts(
                    (SwrContext) null,
                    av_get_default_channel_layout(output_codec_context.channels()),
                    output_codec_context.sample_fmt(),
                    output_codec_context.sample_rate(),
                    av_get_default_channel_layout(input_codec_context.channels()),
                    input_codec_context.sample_fmt(),
                    input_codec_context.sample_rate(),
                    0,
                    (Pointer) null
                )).isNull() ) {
            throw new RuntimeException("Could not allocate resample context");
        }
        /*
        * Perform a sanity check so that the number of converted samples is
        * not greater than the number of samples to be converted.
        * If the sample rates differ, this case has to be handled differently
        */
        if ( output_codec_context.sample_rate() != input_codec_context.sample_rate() ) {
            throw new RuntimeException("Input and output sample rates don't match");
        }

        /* Open the resampler with the specified parameters. */
        check( swr_init(resample_context) );
    }

    /* Initialize a FIFO buffer for the audio samples to be encoded. */
    static void init_fifo () {
        /* Create the FIFO buffer based on the specified output sample format. */
        if ( (fifo = av_audio_fifo_alloc(
                    output_codec_context.sample_fmt(),
                    output_codec_context.channels(),
                    1
                )).isNull() ) {
            throw new RuntimeException("Could not allocate FIFO");
        }
    }

    /* Decode one audio frame from the input file. */
    static void decode_audio_frame () {
        int error;

        /* Initialize packet used for temporary storage. */
        init_input_packet();

        /* Read one audio frame from the input file into a temporary packet. */
        if ( (error = av_read_frame(input_format_context, input_packet)) < 0) {
            /* If we are at the end of the file, flush the decoder below. */
            if (error == AVERROR_EOF) {
                finished = 1;
            }
            else {
                throw new RuntimeException("Could not read frame *(error '" + my_av_err2str(error) + "')");
            }
        }

        /*
         * Decode the audio frame stored in the temporary packet.
         * The input audio stream decoder is used to do this.
         * If we are at the end of the file, pass an empty packet to the decoder
         * to flush it.
         */
        if ( (error = avcodec_decode_audio4(input_codec_context, input_frame, data_present, input_packet)) < 0 ) {
            av_packet_unref(input_packet);
            throw new RuntimeException("Could not decode frame (error '" + my_av_err2str(error) + ")");
        }

        /*
         * If the decoder has not been flushed completely, we are not finished,
         * so that this function has to be called again.
         */
        if (finished != 0 && data_present.get() != 0) {
            finished = 0;
        }
        av_packet_unref(input_packet);
    }

    /* Add converted input audio samples to the FIFO buffer for later processing. */
    static void add_samples_to_fifo () {
        final int frame_size = input_frame.nb_samples();

        /*
         * Make the FIFO as large as it needs to be to hold both,
         * the old and the new samples.
         */
        check( av_audio_fifo_realloc(fifo, (av_audio_fifo_size(fifo) + frame_size)) );

        /* Store the new samples in the FIFO buffer. */
        if ( av_audio_fifo_write(fifo, converted_input_samples, frame_size) < frame_size ) {
            throw new RuntimeException("Could not write data to FIFO");
        }
    }

    /*
     * Read one audio frame from the input file, decodes, converts and stores
     * it in the FIFO buffer.
     */
    static void read_decode_convert_and_store () {
        try {
            /* Initialize temporary storage for one input frame. */
            if ( (input_frame = av_frame_alloc()).isNull() ) {
                throw new RuntimeException("Could not allocate input frame");
            }

    	    /* Decode one frame worth of audio samples. */
            decode_audio_frame();

    	    /*
             * If we are at the end of the file and there are no more samples
             * in the decoder which are delayed, we are actually finished.
             * This must not be treated as an error.
             */
            if (finished == 0 && data_present.get() != 0) {
        	    /* Initialize the temporary storage for the converted input samples. */
                check( av_samples_alloc(
                    converted_input_samples, null, output_codec_context.channels(),
                    input_frame.nb_samples(), output_codec_context.sample_fmt(), 0
                ) );
        	    /*
                * Convert the input samples to the desired output sample format.
                * This requires a temporary storage provided by converted_input_samples.
                */
                check( swr_convert(
                    resample_context, converted_input_samples, input_frame.nb_samples(),
                    input_frame.extended_data(), input_frame.nb_samples()
                ) );
        	    /* Add the converted input samples to the FIFO buffer for later processing. */
                add_samples_to_fifo();
            }
        }
        // cleanup
        finally {
            if ( !converted_input_samples.isNull() ) {
                av_freep(converted_input_samples);
            }
            av_frame_free(input_frame);
        }
    }

	/*
     * Initialize one input frame for writing to the output file.
     * The frame will be exactly frame_size samples large.
     */
    static void init_output_frame (int frame_size) {
        int error;

    	/* Create a new frame to store the audio samples. */
        if ( (output_frame = av_frame_alloc()).isNull() ) {
            throw new RuntimeException("Could not allocate output frame");
        }

    	/*
         * Set the frame's parameters, especially its size and format.
         * av_frame_get_buffer needs this to allocate memory for the
         * audio samples of the frame.
         * Default channel layouts based on the number of channels
         * are assumed for simplicity.
         */
        output_frame.nb_samples( frame_size );
        output_frame.channel_layout( output_codec_context.channel_layout() );
        output_frame.format( output_codec_context.sample_fmt() );
        output_frame.sample_rate( output_codec_context.sample_rate() );

    	/*
         * Allocate the samples of the created frame. This call will make
         * sure that the audio frame can hold as many samples as specified.
         */
        if ((error = av_frame_get_buffer(output_frame, 0)) < 0) {
            av_frame_free(output_frame);
            throw new RuntimeException("Could not allocate output frame samples (error'" + my_av_err2str(error) + "')");
        }
    }

	/* Encode one frame worth of audio to the output file. */
    static void encode_audio_frame () {
        int error;

        init_output_packet();

        if ( !output_frame.isNull() ) {
            output_frame.pts( pts );
            pts += output_frame.nb_samples();
        }

        if ( (error = avcodec_encode_audio2(output_codec_context, output_packet, output_frame, data_written)) < 0 ) {
            av_packet_unref(output_packet);
            throw new RuntimeException("Could not encode frame (error '" + my_av_err2str(error) + "')");
        }

    	/* Write one audio frame from the temporary packet to the output file. */
        if (data_written.get() != 0) {
            if ( (error = av_write_frame(output_format_context, output_packet)) < 0 ) {
                av_packet_unref(output_packet);
                throw new RuntimeException("Could not write frame (error '" + my_av_err2str(error) + "')");
            }
            av_packet_unref(output_packet);
        }
    }

	/*
     * Load one audio frame from the FIFO buffer, encode and write it to the
     * output file.
     */
    static void load_encode_and_write () {
        final int frame_size = (av_audio_fifo_size(fifo) < output_codec_context.frame_size()
                ? av_audio_fifo_size(fifo)
                : output_codec_context.frame_size()
        );
        data_written.put(0); // Reset global var

    	/* Initialize temporary storage for one output frame. */
        init_output_frame(frame_size);

    	/*
         * Read as many samples from the FIFO buffer as required to fill the frame.
         * The samples are stored in the frame temporarily.
         */
        if (av_audio_fifo_read(fifo, output_frame.data(), frame_size) < frame_size) {
            av_frame_free(output_frame);
            throw new RuntimeException("Could not read data from FIFO");
        }

        try {
    	    /* Encode one frame worth of audio samples. */
            encode_audio_frame();
        }
        finally {
            av_frame_free(output_frame);
        }
    }

	/* Convert an audio file to an AAC file in an MP4 container. */
    public static void main (String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("\nexample usage: ");
            System.err.println("java -jar transcode_aac.jar <input_file> <output_file>");
            System.exit(-1);
        }
        
        // Register all formats and codecs
        av_register_all();

        try {
            data_present.put(0);
            // Open input and output files, passing along the contexts
            openInput(args[0]);
            openOutput(args[1]);

    	    /* Initialize the resampler to be able to convert audio sample formats. */
            init_resampler();
    	    /* Initialize the FIFO buffer to store audio samples to be encoded. */
            init_fifo();
    	    /* Write the header of the output file container. */
            check( avformat_write_header(output_format_context, (AVDictionary) null) );

            while (finished == 0) {
        	    /* Use the encoder's desired frame size for processing. */
                final int output_frame_size = output_codec_context.frame_size();
                finished = 0;

        	    /*
                * Make sure that there is one frame worth of samples in the FIFO
                * buffer so that the encoder can do its work.
                * Since the decoder's and the encoder's frame size may differ, we
                * need to FIFO buffer to store as many frames worth of input samples
                * that they make up at least one frame worth of output samples.
                */
                while (av_audio_fifo_size(fifo) < output_frame_size) {
            	    /*
                    * Decode one frame worth of audio samples, convert it to the
                    * output sample format and put it into the FIFO buffer.
                    */
                    read_decode_convert_and_store();
                    
            	    /*
                    * If we are at the end of the input file, we continue
                    * encoding the remaining audio samples to the output file.
                    */
                    if (finished != 0) {
                        break;
                    }
                }

        	    /*
                * If we have enough samples for the encoder, we encode them.
                * At the end of the file, we pass the remaining samples to
                * the encoder.
                */
                while (av_audio_fifo_size(fifo) >= output_frame_size
                        || (finished != 0 && av_audio_fifo_size(fifo) > 0)) {
            	    /*
                    * Take one frame worth of audio samples from the FIFO buffer,
                    * encode it and write it to the output file.
                    */
                    load_encode_and_write();
                }

        	    /*
                * If we are at the end of the input file and have encoded
                * all remaining samples, we can exit this loop and finish.
                */
                if (finished != 0) {
                    data_written.put(0);
            	    /* Flush the encoder as it may have delayed frames. */
                    do {
                        encode_audio_frame();
                    }
                    while (data_written.get() != 0);
                    break;
                }
            }

    	    /* Write the trailer of the output file container. */
            check( av_write_trailer(output_format_context) );
        }
        // cleanup
        finally {
            if ( !fifo.isNull() ) {
                swr_free(resample_context);
            }
            swr_free(resample_context);
            
            if ( !output_codec_context.isNull() ) {
                avcodec_free_context(output_codec_context);
            }
            if ( !output_format_context.isNull() ) {
                avio_closep(output_format_context.pb());
                avformat_free_context(output_format_context);
            }
            if ( !input_codec_context.isNull() ) {
                avcodec_free_context(input_codec_context);
            }
            if ( !input_format_context.isNull() ) {
                avformat_close_input(input_format_context);
            }
        }

        System.exit(0);
    }
}

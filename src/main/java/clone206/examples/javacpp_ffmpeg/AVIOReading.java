/* Copyright 2018 Kevin Witmer
 *
 * Code based on an example program by Stefano Sabatini
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
import static org.bytedeco.javacpp.swscale.*;

/**
 * java javacpp-presets/ffmpeg translation of the avio_reading ffmpeg official example
 * https://github.com/FFmpeg/FFmpeg/blob/n3.4.2/doc/examples/avio_reading.c
 * 
 * Depends on javacpp and the ffmpeg javacpp preset,
 * with classpaths set accordingly on compile/run
 *
 * @author Kevin Witmer
 *
 * @file
 * libavformat AVIOContext API example.
 *
 * Make libavformat demuxer access media content through a custom
 * AVIOContext read callback.
 * @example AVIOReading.java
 */

/** 
 * Defines the callback object. Used by the format context 
 * (via the associated I/O context) when reading packet by packet
 */ 
class ReadInput extends Read_packet_Pointer_BytePointer_int {
    private FileInputStream istream;    // Stream from user inputted file
    private long file_size;             // Size of user inputted file

    // Constructor
    public ReadInput (FileInputStream istream, long file_size) {
        super();
        this.istream = istream;
        this.file_size = file_size;
    }

    // The callback
    @Override
    public int call(Pointer opaque, BytePointer buf, int buf_size) {
        byte[] b = new byte[buf_size];  // Allocate byte array
        int len;                        // Length read from file
        long pos;                       // Position of file stream

        try {
            // Get the current position
            pos = istream.getChannel().position();
            // Read from stream into byte array
            len = istream.read(b);

            // Check for EOF
            if (len <= 0) {
                return AVERROR_EOF;
            }
            else {
                // Print the position of the file stream (in hex) 
                // and the size of the remaining file stream (in decimal)
                System.out.println( String.format("ptr:0x%09x size:%d", pos, (file_size - pos)) );
                // Copy the byte array's contents into the passed-in buffer
                buf.put(b, 0, len);
            }
        } catch (IOException e) {
            return -1;
        }

        // If we got this far, return number of bytes read in.
        return len;
    }
}

public class AVIOReading {
    /* Called when we've finished or encountered a fatal error. Releases resources and prints any error msg */
    private static void cleanup (AVFormatContext fmt_ctx, FileInputStream istream, int ret) {
        int errbuf_size = 1024;                     // Max length for error msgs
        byte[] errbuf    = new byte[errbuf_size];   // Holds the error msgs
        
        // Close file input stream
        try {
            istream.close();
        }
        catch (IOException e) {
            System.exit(-1);
        }

        // Close format context
        avformat_close_input(fmt_ctx);

        // Print any errors
        if (ret < 0) {
            av_strerror(ret, errbuf, errbuf_size);
            System.err.println(new String(errbuf));
            System.exit(-1);
        }
    }

    public static void main (String[] args) throws IOException {
        AVFormatContext fmt_ctx     = new AVFormatContext(null);
        int avio_ctx_buffer_size    = 4096,
            ret                     = 0;
        BytePointer avio_ctx_buffer = new BytePointer( av_malloc(avio_ctx_buffer_size) );
        AVDictionary options        = new AVDictionary();

        if (args.length < 1) {
            System.err.println("\nexample usage: ");
            System.err.println("java -jar avio_reading.jar <input_file>");
            System.err.println("API example program to show how to read from a custom buffer accessed through AVIOContext.\n");
            System.exit(-1);
        }
        
        // Set up the file input stream for passing to the aviocontext callback
        String input_filename       = args[0];
        File file                   = new File(input_filename);
        long file_size              = file.length();
        file.setWritable(false);
        FileInputStream istream = new FileInputStream(file);

        // So that stream/format-related calls below work
        av_register_all();
        
        // Allocate the format context 
        fmt_ctx = avformat_alloc_context();
        // Set up the I/O context and pass it in to the format context. Null the opaque pointer since it's of no use in this example
        fmt_ctx.pb( avio_alloc_context(avio_ctx_buffer, avio_ctx_buffer_size, 0, null, new ReadInput(istream, file_size), null, null) );
        fmt_ctx.pb().seekable(0);   // Disable seeking

        // Use the format context and its associated I/O context to open the file stream
        ret = avformat_open_input(fmt_ctx, "", null, null);

        if (ret < 0) {
            System.err.println("Could not open input");
            cleanup(fmt_ctx, istream, ret);
        }

        // Use the format context to get info about the streams contained in the file
        ret = avformat_find_stream_info(fmt_ctx, options);
        
        if (ret < 0) {
            System.err.println("Could not find stream information");
            cleanup(fmt_ctx, istream, ret);
        }

        // Dump the stream info to the screen
        av_dump_format(fmt_ctx, 0, input_filename, 0);
        
        // Free resources, print any errors
        cleanup(fmt_ctx, istream, ret);

        System.exit(0);
    }  
}

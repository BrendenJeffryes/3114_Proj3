import java.nio.*;
import java.nio.channels.FileChannel;
import java.io.*;

// The Radix Sort implementation
// -------------------------------------------------------------------------
/**
 *
 * @author Peter Reilly (preilly) Brenden Jeffryes (jbrenden)
 * @version Final Submission
 */
public class Radix {
    private final int recordSize = 8; // 8 bytes per record
    private final int readMem = 300000; // RAM for reading the data
    private final int writeMem = 600000; // RAM for writing the sorted data
    private final int radix = 256; // radix per byte(256 values)
    private final int numPasses = 4; // number of passes

    // buffer for reading chunks of records
    private final ByteBuffer readBuffer = ByteBuffer.allocate(readMem);
    // array of buffers. One for each bucket
    private final ByteBuffer[] bucketBuffers = new ByteBuffer[radix];
    // current position, per bucket, inside the output file
    private final long[] writePos = new long[radix];
    // counts how many records are in each bucket
    private int[] count = new int[radix];

    private long numRecords;
    private int recordsPerChunk = readMem / recordSize;

    private RandomAccessFile inputFile;
    private RandomAccessFile outputFile;
    private FileChannel inputChannel;
    private FileChannel outputChannel;

    /**
     * Create a new Radix object.
     * 
     * @param theFile
     *            The RandomAccessFile to be sorted
     * @param s
     *            The stats PrintWriter
     *
     * @throws IOException
     */
    public Radix(RandomAccessFile theFile, PrintWriter s) throws IOException {

        // set input file and channel
        inputFile = theFile;
        inputChannel = inputFile.getChannel();

        // create temporary file for sorting
        File tempFile = File.createTempFile("radixpass", ".bin");
        tempFile.deleteOnExit();

        // set outputs
        outputFile = new RandomAccessFile(tempFile, "rw");
        outputChannel = outputFile.getChannel();

        // allocate memory for the 256 buckets
        int bucketSize = writeMem / radix;
        for (int i = 0; i < radix; i++) {
            bucketBuffers[i] = ByteBuffer.allocate(bucketSize);
        }

        numRecords = theFile.length() / recordSize;
        // run sort
        radixSort();
    }


    /**
     * Do a Radix sort
     *
     * @throws IOException
     */
    private void radixSort() throws IOException {

        boolean sortedInA = true; // track where the current sorted data is

        // performs 1 pass for each byte inside the key (4 passes)
        // for each pass, it extracts the subsequent byte
        for (int pass = 0, rtok = 1; pass < numPasses; pass++, rtok *= radix) {

            // reset count
            for (int i = 0; i < radix; i++)
                count[i] = 0;

            // count how many records go into each bucket
            countRecords(rtok);
            // use counts to find the correct positions
            computeWritePositions();
            // write to buckets after reading
            writeRecords(rtok);

            // flush any remaining buffers
            for (int i = 0; i < radix; i++) {
                flushBucket(i);
            }

            // swap files and channels
            RandomAccessFile tempF = inputFile;
            inputFile = outputFile;
            outputFile = tempF;

            FileChannel tempC = inputChannel;
            inputChannel = outputChannel;
            outputChannel = tempC;

            sortedInA = !sortedInA;
        }

        // If the final sorted data ended up in the temp file, then copy it back
        // to the original
        if (!sortedInA) {
            copyFile(inputFile, outputFile, readBuffer);
        }
    }


    /**
     * Counts how many records belong in each bucket for the current pass.
     * 
     * This method reads through the input file in chunks, then extracts the
     * right bytes from each key. Then it increments count
     * 
     * @param rtok
     *            is the Radix token to extract the current byte from the key
     * @throws IOException
     */
    private void countRecords(int rtok) throws IOException {

        // Check if the position is within the read buffer
        for (long pos = 0; pos < numRecords; pos += recordsPerChunk) {

            // calculate how many records are left in the chunk
            int recordsLeft = (int)Math.min(recordsPerChunk, numRecords - pos);

            // read the chunk from the file
            readBuffer.clear();
            inputChannel.position(pos * recordSize);
            inputChannel.read(readBuffer);
            readBuffer.flip();

            // convert from byte to int
            IntBuffer intBuf = readBuffer.asIntBuffer();

            // count records in the buckets
            for (int i = 0; i < recordsLeft; i++) {
                int key = intBuf.get(2 * i);
                int value = intBuf.get(2 * i + 1);
                // get the current byte from the key and increment bucket
                count[(key / rtok) % radix]++;
            }
        }
    }


    /**
     * Converts the bucket counts into starting positions for the output file
     * writes
     */
    private void computeWritePositions() {
        int total = 0;
        for (int i = 0; i < radix; i++) {
            int oldCount = count[i];
            count[i] = total;
            // new total helps create index for starting position for each
            // bucket
            total += oldCount;

            // set the actual write position
            writePos[i] = (long)count[i] * recordSize;
        }
    }


    /**
     * Reads records from the input and writes them to their spots in output
     * While writing to the bucket's buffer, if it gets full it will also flush
     * the buffer to the file.
     * 
     * @param rtok
     *            is the radix token. Used for selecting the byte to write
     * @throws IOException
     */
    private void writeRecords(int rtok) throws IOException {
        // Start reading at the beginning
        inputChannel.position(0);

        for (long pos = 0; pos < numRecords; pos += recordsPerChunk) {
            // find how many records are left in the chunks
            int recordsLeft = (int)Math.min(recordsPerChunk, numRecords - pos);

            // read chunks
            readBuffer.clear();
            inputChannel.read(readBuffer);
            readBuffer.flip();

            // convert to int
            IntBuffer intBuf = readBuffer.asIntBuffer();

            // move each record into its own bucket
            for (int i = 0; i < recordsLeft; i++) {
                int key = intBuf.get(2 * i);
                int value = intBuf.get(2 * i + 1);
                // find what bucket we need
                int digit = (key / rtok) % radix;

                // Write record to buffer
                ByteBuffer buf = bucketBuffers[digit];
                buf.putInt(key);
                buf.putInt(value);

                // If bucket is full, flush it. This is what actually writes to
                // the file
                if (buf.position() >= buf.capacity() - recordSize) {
                    flushBucket(digit);
                }
            }
        }
    }


    /**
     * Flushes a bucket's buffer to the output file
     * 
     * @param digit
     *            is the bucket number
     * @throws IOException
     */
    private void flushBucket(int digit) throws IOException {
        ByteBuffer buf = bucketBuffers[digit];

        // return if the buffer is empty
        if (buf.position() == 0)
            return;

        // write to file and clear buffer
        buf.flip();
        outputChannel.position(writePos[digit]);
        outputChannel.write(buf);
        writePos[digit] += buf.limit();
        buf.clear();
    }


    /**
     * Copies 1 file to another
     * 
     * @param source
     *            file to read
     * @param dest
     *            file to write
     * @param buffer
     *            is the buffer we're using to transfer the data
     * @throws IOException
     */
    private static void copyFile(
        RandomAccessFile source,
        RandomAccessFile dest,
        ByteBuffer buffer)
        throws IOException {

        FileChannel srcChannel = source.getChannel();
        FileChannel destChannel = dest.getChannel();

        // read/write from the beginning
        source.seek(0);
        dest.setLength(0);
        dest.seek(0);

        buffer.clear();

        // copy in chunks
        while (true) {
            buffer.clear();
            int bytesRead = srcChannel.read(buffer);
            // check if we're at the end
            if (bytesRead == -1)
                break;
            // write data
            buffer.flip();
            while (buffer.hasRemaining())
                destChannel.write(buffer);
        }
    }
}

package net.gmx.szermatt.dreamcatcher.harmony

import androidx.annotation.GuardedBy
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayDeque
import kotlin.concurrent.withLock

class Pipe {
    private val lock = ReentrantLock()
    private val blockCondition = lock.newCondition()

    @GuardedBy("lock")
    private val queue = ArrayDeque<ByteBuffer>()

    @GuardedBy("lock")
    private var closed = false
    private var dump = false
    private var dumpHeader = "Pipe"
    private var dumpCharset = Charsets.UTF_8

    /**
     * The stream to use to read from the pipe.
     *
     * Reading past the data currently available blocks until [outputStream] has been closed.
     */
    val inputStream = object : InputStream() {
        override fun available(): Int {
            val sum = lock.withLock {
                queue.sumOf { it.remaining() }
            }
            if (sum == 0 && closed) {
                throw EOFException()
            }
            return sum
        }

        override fun read() = readInternal(true)

        override fun read(b: ByteArray?): Int {
            return read(b, 0, b?.size ?: 0)
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            Objects.checkFromIndexSize(off, len, b!!.size)
            if (len == 0) {
                return 0
            }

            val initial = readInternal(true)
            if (initial == -1) return -1
            if (b != null) b[off] = initial.toByte()

            var i = 1
            try {
                while (i < len) {
                    val c = readInternal(false)
                    if (c == -1) break
                    if (b != null) b[off + i] = c.toByte()
                    i++
                }
            } catch (e: IOException) {
            }
            return i
        }

        /**
         * Reads one byte, returns -1 on EOS.
         *
         * When the pipe is empty, but unclosed and [blocking] is true, this call blocks. Otherwise,
         * the call returns -1 as it would when the end of the stream has been reached.
         */
        private fun readInternal(blocking: Boolean): Int {
            lock.withLock {
                while (true) {
                    while (!queue.isEmpty() && !queue.first().hasRemaining()) {
                        queue.removeFirst()
                    }
                    if (!queue.isEmpty()) {
                        return queue.first().get().toInt()
                    }
                    if (closed || !blocking) {
                        return -1
                    }
                    blockCondition.await()
                    println("$dumpHeader unblocked")
                }
            }
        }
    }

    /** The stream to use to write to the pipe. */
    val outputStream = object : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(arr: ByteArray?) {
            write(arr, 0, arr?.size ?: 0)
        }

        override fun write(arr: ByteArray?, off: Int, len: Int) {
            if (len == 0 || arr == null) return;

            if (dump) {
                println("--- %s START ---".format(dumpHeader))
                println(String(arr.copyOfRange(off, off + len), dumpCharset))
                println("\n--- %s END ---".format(dumpHeader))
            }
            val buf = ByteBuffer.allocate(len)
            buf.put(arr, off, len)
            buf.rewind()
            lock.withLock {
                queue.addLast(buf)
                blockCondition.signalAll()
            }
        }

        override fun close() {
            lock.withLock {
                if (closed) return
                closed = true;
                blockCondition.signalAll()
            }
        }
    }

    /** Closes the pipe for writing. The pipe is still available for reading until EOS. */
    fun close() {
        outputStream.close()
    }

    /** Returns the number of bytes available to be read without blocking. */
    fun available() = inputStream.available()

    /** Dump data to stdout, when it is written to the pipe. */
    fun dumpAs(header: String, charset: Charset) {
        dump = true
        dumpHeader = header
        dumpCharset = charset
    }
}
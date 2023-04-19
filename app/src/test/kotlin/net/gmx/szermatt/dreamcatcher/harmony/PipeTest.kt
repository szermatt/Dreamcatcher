package net.gmx.szermatt.dreamcatcher.harmony

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

class PipeTest {
    @Test
    fun writeThenRead() {
        val p = Pipe()
        p.outputStream.write(byteArrayOf(10, 20, 30))
        p.outputStream.write(byteArrayOf(40, 50, 60))
        p.outputStream.close()
        assertEquals(10, p.inputStream.read())
        assertEquals(20, p.inputStream.read())
        assertEquals(30, p.inputStream.read())
        assertEquals(40, p.inputStream.read())
        assertEquals(50, p.inputStream.read())
        assertEquals(60, p.inputStream.read())
        assertEquals(-1, p.inputStream.read())
    }

    @Test
    fun available() {
        val p = Pipe()
        p.outputStream.write(byteArrayOf(10, 20, 30))

        assertEquals(3, p.inputStream.available())
        assertEquals(10, p.inputStream.read())
        assertEquals(2, p.inputStream.available())
        assertEquals(20, p.inputStream.read())
        assertEquals(1, p.inputStream.available())
        assertEquals(30, p.inputStream.read())
        assertEquals(0, p.inputStream.available())

        p.outputStream.write(byteArrayOf(40, 50, 60))

        assertEquals(3, p.inputStream.available())
        assertEquals(40, p.inputStream.read())
        assertEquals(2, p.inputStream.available())
        assertEquals(50, p.inputStream.read())
        assertEquals(1, p.inputStream.available())
        assertEquals(60, p.inputStream.read())
        assertEquals(0, p.inputStream.available())

        p.outputStream.close()
        assertThrows(IOException::class.java) {
            p.inputStream.available()
        }
    }

    @Test
    fun asyncReadWrite() {
        val p = Pipe()
        val threadPool = Executors.newCachedThreadPool()
        try {
            runBlocking(threadPool.asCoroutineDispatcher()) {
                launch {
                    assertEquals(10, p.inputStream.read())
                    assertEquals(20, p.inputStream.read())
                    assertEquals(30, p.inputStream.read())
                    assertEquals(40, p.inputStream.read())
                    assertEquals(50, p.inputStream.read())
                    assertEquals(60, p.inputStream.read())
                    assertEquals(-1, p.inputStream.read())
                }
                launch {
                    p.outputStream.write(byteArrayOf(10, 20, 30))
                    delay(1)
                    p.outputStream.write(byteArrayOf(40, 50, 60))
                    delay(1)
                    p.outputStream.close()
                }
            }
        } finally {
            threadPool.shutdown()
        }
    }
}
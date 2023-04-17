package net.gmx.szermatt.dreamcatcher.harmony

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PowerOffTaskTest {

    @Test
    fun run() {
        initSocketImpl()
        val authSocket = FakeSocket()
        sockets.add(authSocket)

        val mainSocket = FakeSocket()
        sockets.add(mainSocket)

        val task = PowerOffTask("127.0.0.1")
        task.run()
    }

    private fun initSocketImpl() {
        synchronized(classLock) {
            if (socketImplSetup) {
                return
            }
            Socket.setSocketImplFactory(FakeSocketImplFactory {
                sockets.poll(10, TimeUnit.SECONDS)
                    ?: throw IllegalStateException("No FakeSocket available")
            })
            socketImplSetup = true
        }
    }

    companion object {
        val classLock = Any() // protects fields below
        val sockets = ArrayBlockingQueue<FakeSocket>(10)
        var socketImplSetup = false
    }
}
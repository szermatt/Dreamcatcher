package net.gmx.szermatt.dreamcatcher.harmony

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.Socket

class PowerOffTaskTest {

    companion object {
        var socket = FakeSocket()

        @BeforeAll
        fun setupSocket() {
            Socket.setSocketImplFactory(FakeSocketImplFactory {
                socket = FakeSocket()
                socket
            })
        }
    }

    @Test
    fun run() {
        PowerOffTask()
    }
}
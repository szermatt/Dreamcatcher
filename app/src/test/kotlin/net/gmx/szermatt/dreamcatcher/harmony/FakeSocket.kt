package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import javax.net.SocketFactory

/** A fake socket backed by an input and output buffer. */
class FakeSocket(
    val input: ByteBuffer,
    val output: ByteBuffer,
) {
    /** A fake socket with buffers of default size.*/
    constructor() : this(ByteBuffer.allocate(1024), ByteBuffer.allocate(1024)) {}

    var connectedTo : SocketAddress? = null
    var boundTo : InetSocketAddress? = null
    var closed = false
}

internal class FakeSocketImplFactory(private val next: () -> FakeSocket): SocketImplFactory {
    override fun createSocketImpl(): SocketImpl = FakeSocketImpl(next())
}


///** A factory that returns [FakeSocket]s. */
//class FakeSocketFactory(
//    private val next: () -> FakeSocket
//) : SocketFactory() {
//
//    override fun createSocket() : Socket = Socket(FakeSocketImpl(next()))
//
//    override fun createSocket(host: String?, port: Int) =
//        createSocket(host, port, null, 0)
//
//    override fun createSocket(
//        host: String?,
//        port: Int,
//        localHost: InetAddress?,
//        localPort: Int
//    ) = internalCreateSocket(InetAddress.getAllByName(host), port, localHost, localPort)
//
//    override fun createSocket(host: InetAddress?, port: Int) =
//        internalCreateSocket(if (host != null) arrayOf(host) else arrayOf(), port)
//
//    override fun createSocket(
//        address: InetAddress?,
//        port: Int,
//        localAddress: InetAddress?,
//        localPort: Int
//    ) = internalCreateSocket(
//        if (address == null) {
//            arrayOf()
//        } else {
//            arrayOf(address)
//        },
//        port,
//        localAddress,
//        localPort
//    )
//
//    private fun internalCreateSocket(
//        addresses: Array<InetAddress>,
//        port: Int,
//        localAddress: InetAddress?,
//        localPort: Int
//    ) : Socket {
//        var lastE : IOException = null
//        val localSocketAddress = if (localAddress != null) {
//            InetSocketAddress(localAddress, localPort)
//        } else {
//            null
//        }
//        if (addresses.isEmpty()) {
//            val s = Socket(FakeSocketImpl(next()))
//            if (localSocketAddress != null) {
//                s.bind(localSocketAddress)
//            }
//            return s
//        }
//        for (address in addresses) {
//            val s = Socket(FakeSocketImpl(next()))
//            if (localSocketAddress != null) {
//                s.bind(localSocketAddress)
//            }
//            try {
//                s.connect(InetSocketAddress(address, port))
//                return s
//            } catch (e: IOException) {
//                lastE = e
//            }
//        }
//        throw lastE!!
//    }
//}

internal class FakeSocketImpl(private val socket : FakeSocket) : SocketImpl() {
    private val inputStream = ByteBufferBackedInputStream(socket.input)
    private val outputStream = ByteBufferBackedOutputStream(socket.output)
    private val options = mutableMapOf<Int, Any>()
    private var stream = false

    override fun setOption(optID: Int, value: Any?) {
        if (value == null) {
            options.remove(optID)
        } else {
            options[optID] = value
        }
    }

    override fun getOption(optID: Int): Any {
        return options[optID]!!
    }

    override fun create(stream: Boolean) {
        this.stream = stream
    }

    override fun connect(host: String?, port: Int) {
        connect(if (host != null) InetSocketAddress(host, port) else null, 0)
    }

    override fun connect(address: InetAddress?, port: Int) {
        connect(if (address != null) InetSocketAddress(address, port) else null, 0)
    }

    override fun connect(address: SocketAddress?, timeout: Int) {
        socket.connectedTo = address
    }

    override fun bind(host: InetAddress?, port: Int) {
        socket.boundTo = InetSocketAddress(host, port)
    }

    override fun listen(backlog: Int) {
        throw NotImplementedError("Socket.listen")
    }

    override fun accept(s: SocketImpl?) {
        throw NotImplementedError("Socket.accept")
    }

    override fun getInputStream(): InputStream {
        return inputStream
    }

    override fun getOutputStream(): OutputStream {
        return outputStream
    }

    override fun available(): Int {
        return 0
    }

    override fun close() {
        socket.closed = true
    }

    override fun sendUrgentData(data: Int) {
        throw NotImplementedError("Socket.sendUrgentData")
    }
}
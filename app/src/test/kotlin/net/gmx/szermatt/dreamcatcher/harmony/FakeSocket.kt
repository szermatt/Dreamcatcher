package net.gmx.szermatt.dreamcatcher.harmony

import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.charset.Charset

/**
 * Sets things up so that all new [Socket]s are backed by [FakeSocket]s.
 *
 * This can only be called once.
 */
fun initFakeSockets(next: () -> FakeSocket) {
    Socket.setSocketImplFactory(FakeSocketImplFactory(next))
}

/** A fake socket backed by an input and output pipe. */
class FakeSocket {
    /**
     * The pipe that represents this socket input.
     *
     * Use `input.inputStream` to read data from the socket, `input.outputStream` to simulate
     * the remote end sending data through the socket.
     */
    val input = Pipe()

    /**
     * The pipe that represents this socket output.
     *
     * Use `output.outputStream` to write data to the socket, `output.inputStream` to simulate
     * the remote end receiving data through the socket.
     */
    val output = Pipe()

    /** The address connected to, to be checked in tests. */
    var connectedTo: SocketAddress? = null

    /** The local address the socket is bound to, to be checked in tests. */
    var boundTo: InetSocketAddress? = null

    /** Whether the socket has been closed. */
    var closed = false

    /** Dumps data to stdout when written or read from the socket. */
    fun dumpAs(header: String, charset: Charset) {
        output.dumpAs("$header OUT", charset)
        input.dumpAs("$header IN", charset)
    }
}

private class FakeSocketImplFactory(private val next: () -> FakeSocket) : SocketImplFactory {
    override fun createSocketImpl(): SocketImpl = FakeSocketImpl(next())
}

private class FakeSocketImpl(private val socket: FakeSocket) : SocketImpl() {
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
        return socket.input.inputStream
    }

    override fun getOutputStream(): OutputStream {
        return socket.output.outputStream
    }

    override fun available(): Int {
        return socket.input.inputStream.available()
    }

    override fun close() {
        socket.output.outputStream.close()
        socket.input.inputStream.close()
        socket.closed = true
    }

    override fun sendUrgentData(data: Int) {
        throw NotImplementedError("Socket.sendUrgentData")
    }
}
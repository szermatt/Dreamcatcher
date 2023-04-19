package net.gmx.szermatt.dreamcatcher.harmony

import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.charset.Charset

/** A fake socket backed by an input and output buffer. */
class FakeSocket() {
    val input = Pipe()
    val output = Pipe()

    var connectedTo: SocketAddress? = null
    var boundTo: InetSocketAddress? = null
    var closed = false

    fun dumpAs(header: String, charset: Charset) {
        output.dumpAs("$header OUT", charset)
        input.dumpAs("$header IN", charset)
    }
}

internal class FakeSocketImplFactory(private val next: () -> FakeSocket): SocketImplFactory {
    override fun createSocketImpl(): SocketImpl = FakeSocketImpl(next())
}


internal class FakeSocketImpl(private val socket : FakeSocket) : SocketImpl() {
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
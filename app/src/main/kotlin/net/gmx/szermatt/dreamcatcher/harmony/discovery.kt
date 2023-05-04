package net.gmx.szermatt.dreamcatcher.harmony

import java.io.IOException
import java.io.InputStreamReader
import java.net.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sends out a discovery message to the hub and returns the address of the first Hub that responds.
 *
 * TODO: remember the UUID of the right hub, if there's more than one, and use it here
 */
fun discoverHub(
    timeout: Duration = 1500.milliseconds,
    tryCount: Int = 3,
): InetAddress? {
    ServerSocket(0 /* any available port */).use { serverSocket ->
        serverSocket.soTimeout = timeout.inWholeMilliseconds.toInt()
        repeat(tryCount) {
            sendDiscoveryRequest(serverSocket.localPort)
            while (true) {
                val connectionSocket = try {
                    serverSocket.accept()
                } catch (e: SocketTimeoutException) {
                    break // retry
                }
                try {
                    val text = InputStreamReader(
                        connectionSocket.getInputStream(), Charsets.UTF_8
                    ).readText()
                    val attrs = parseDiscoveryResponse(text)
                    val ip = attrs["ip"] ?: continue // skip

                    return@discoverHub InetAddress.getByName(ip)
                } catch (e: IOException) {
                    // skip
                } finally {
                    connectionSocket.close()
                }
            }
        }
    }
    return null
}

/** Puts the discovery response into a map. */
private fun parseDiscoveryResponse(text: String): Map<String, String> {
    return text.split(';')
        .map {
            Pair(
                it.substringBefore(':'),
                it.substringAfter(':')
            )
        }
        .toMap()
}

/** Broadcasts a discovery request to the port the Harmony Hub listens to. */
private fun sendDiscoveryRequest(serverPort: Int) {
    DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.soTimeout = 1000

        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val data =
            "_logitech-reverse-bonjour._tcp.local.\n${serverPort}".encodeToByteArray()
        socket.send(DatagramPacket(data, data.size, broadcastAddress, 5224))
    }
}

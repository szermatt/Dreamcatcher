package net.gmx.szermatt.dreamcatcher.harmony

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.io.InputStreamReader
import java.net.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DiscoveredHub(
    val friendlyName: String,
    val uuid: String,
    val ip: String,
) {
    companion object {
        fun fromString(text: String): DiscoveredHub? {
            val attrs = text.split(';')
                .map {
                    Pair(
                        it.substringBefore(':'),
                        it.substringAfter(':')
                    )
                }
                .toMap()
            val ip = attrs["ip"] ?: return null
            val uuid = attrs["uuid"] ?: return null
            val friendlyName = attrs["friendlyName"] ?: ""
            return DiscoveredHub(friendlyName, uuid, ip)
        }
    }

    override fun toString(): String {
        return "friendlyName:${friendlyName};uuid:${uuid};ip:${ip}"
    }
}

fun discoveryFlow(
    timeout: Duration = 1500.milliseconds,
    tryCount: Int = 3,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Flow<DiscoveredHub> {
    return flow {
        ServerSocket(0 /* any available port */).use { serverSocket ->
            serverSocket.soTimeout = timeout.inWholeMilliseconds.toInt()
            repeat(tryCount) {
                sendDiscoveryRequest(serverSocket.localPort)
                while (true) {
                    val connection = try {
                        serverSocket.accept()
                    } catch (e: SocketTimeoutException) {
                        break // send another discovery request, tryCount allowing
                    }
                    try {
                        connection.use { socket ->
                            val text = InputStreamReader(
                                socket.getInputStream(), Charsets.UTF_8
                            ).readText()
                            DiscoveredHub.fromString(text)?.let { emit(it) }
                        }
                    } catch (e: IOException) {
                        continue // skip this connection
                    }
                }
            }
        }
    }.flowOn(ioDispatcher)
}

/**
 * Sends out a discovery message to the hub and returns the address of the first Hub that responds.
 *
 * TODO: remember the UUID of the right hub, if there's more than one, and use it here
 */
fun discoverHub(
    uuid: String? = null,
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
                    if (uuid != null && attrs["uuid"] != uuid) continue // skip

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

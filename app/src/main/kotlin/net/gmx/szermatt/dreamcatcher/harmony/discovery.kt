package net.gmx.szermatt.dreamcatcher.harmony

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import net.gmx.szermatt.dreamcatcher.harmony.DiscoveredHub.Companion.fromString
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Long.min
import java.net.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A Harmony Hub that was discovered.
 *
 * This type collects some of the important properties reported by Harmony Hub upon discovery,
 * given the string sent by the hub as a response to the discovery request, passed to [fromString].
 * Note, however, that [fromString] discards any property not exposed by [DiscoveredHub].
 *
 * DiscoveredHub can be serialized to a string using [toString] and re-created later with
 * [fromString].
 */
class DiscoveredHub(
    val friendlyName: String?,
    val uuid: String,
    val ip: String,
) {
    companion object {
        /**
         * Tries to parse a [DiscoveredHub] from the string and returns it.
         */
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
            val friendlyName = attrs["friendlyName"]
            return DiscoveredHub(friendlyName, uuid, ip)
        }
    }

    override fun toString(): String {
        return "friendlyName:${friendlyName};uuid:${uuid};ip:${ip}"
    }
}

/**
 * Returns a channel of [DiscoveredHub].
 *
 * This function launches jobs to send out discovery requests to the Harmony Hub, collects
 * their responses and sends them to the channel.
 *
 * Discovery keeps on running until it is cancelled, so this should normally be run within a
 * scope with a meaningful lifetime, such as within a `withTimeout` block.
 */
@ExperimentalCoroutinesApi
suspend fun CoroutineScope.discoveryChannel(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ReceiveChannel<DiscoveredHub> = produce(ioDispatcher, 10) {
    ServerSocket(0 /* any available port */).use { serverSocket ->
        val serverPort = serverSocket.localPort
        launch {
            sendDiscoveryRequestsUntilCancelled(serverPort)
        }

        while (isActive && !isClosedForSend) {
            val connection = serverSocket.cancellableAccept()
            try {
                connection.use { s ->
                    DiscoveredHub.fromString(
                        InputStreamReader(s.getInputStream(), Charsets.UTF_8).readText()
                    )?.let { send(it) }
                }
            } catch (e: IOException) {
                continue // skip this connection
            }
        }
    }
}

/**
 * Wraps [ServerSocket.accept] to make it cancellable.
 *
 * If the Job is cancelled, the server socket is closed and cannot be used again.
 */
@ExperimentalCoroutinesApi
private suspend fun ServerSocket.cancellableAccept(): Socket {
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { close() }
        cont.resume(
            try {
                accept()
            } catch (e: SocketException) {
                if (isClosed) {
                    throw CancellationException("cancelled in accept()")
                } else {
                    throw e
                }
            }
        ) {}
    }
}

/**
 * Keeps sending discovery requests, with exponential backoff.
 */
private suspend fun sendDiscoveryRequestsUntilCancelled(
    serverPort: Int,
    baseTimeout: Duration = 500.milliseconds,
    maxTimeout: Duration = 5.seconds
) {
    val baseTimeoutMs = baseTimeout.inWholeMilliseconds
    val maxTimeoutMs = maxTimeout.inWholeMilliseconds
    var tryCount = 0

    val broadcastAddress = InetAddress.getByName("255.255.255.255")
    val data =
        "_logitech-reverse-bonjour._tcp.local.\n${serverPort}".encodeToByteArray()

    while (currentCoroutineContext().isActive) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(data, data.size, broadcastAddress, 5224))
        }
        delay(min(maxTimeoutMs, (2.0.pow(tryCount) * baseTimeoutMs).toLong()))
        tryCount++
    }
}

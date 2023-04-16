package net.gmx.szermatt.dreamcatcher.harmony

import org.jivesoftware.smack.packet.IQ
import java.util.regex.Pattern


/** Exception thrown when Logitech authentication fails.  */
internal class AuthFailedException @JvmOverloads constructor(
    message: String?,
    cause: Throwable? = null
) : RuntimeException(
    String.format("%s: %s", "Logitech authentication failed", message), cause
)

internal abstract class OAReplyParser {
    abstract fun parseReplyContents(statusCode: String?, errorString: String?, contents: String): IQ
    open fun validResponseCode(code: String?): Boolean {
        return validResponses.contains(code)
    }

    companion object {
        /*
     * FIXME: This parser could be far cleaner than it is, given the possibility of the pseudo-json components
     * containing colons, and the structure of them
     */
        val kvRE = Pattern.compile("(.*?)=(.*)")
        var validResponses: MutableSet<String?> = HashSet()

        init {
            validResponses.add("100")
            validResponses.add("200")
            validResponses.add("506") // Bluetooth not connected
            validResponses.add("566") // Command not found for device, recoverable
        }

        protected fun parseKeyValuePairs(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): Map<String, Any> {
            val params: MutableMap<String, Any> = HashMap()
            if (statusCode != null) {
                params["statusCode"] = statusCode
            }
            if (errorString != null) {
                params["errorString"] = errorString
            }
            for (pair in contents.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                val matcher = kvRE.matcher(pair)
                if (!matcher.matches()) {
                    continue
                    // throw new AuthFailedException(format("failed to parse element in auth response: %s", pair));
                }
                var valueObj: Any
                val value = matcher.group(2)
                valueObj = if (value.startsWith("{")) {
                    parsePseudoJson(value)
                } else {
                    value
                }
                params[matcher.group(1)] = valueObj
            }
            return params
        }

        protected fun parsePseudoJson(value: String): Map<String, Any> {
            var value = value
            val params: MutableMap<String, Any> = HashMap()
            value = value.substring(1, value.length - 1)
            for (pair in value.split(", ?".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                val matcher = kvRE.matcher(pair)
                if (!matcher.matches()) {
                    throw AuthFailedException(
                        String.format(
                            "failed to parse element in auth response: %s",
                            value
                        )
                    )
                }
                params[matcher.group(1)] = parsePseudoJsonValue(matcher.group(2))
            }
            return params
        }

        private fun parsePseudoJsonValue(value: String): Any {
            return when (value[0]) {
                '{' -> parsePseudoJsonValue(value)
                '"' -> value.substring(1, value.length - 1)
                '\'' -> value.substring(1, value.length - 1)
                else -> {
                    try {
                        return value.toLong()
                    } catch (e: NumberFormatException) {
                        // do nothing
                    }
                    try {
                        return value.toDouble()
                    } catch (e: NumberFormatException) {
                        // do nothing
                    }
                    value
                }
            }
        }
    }
}
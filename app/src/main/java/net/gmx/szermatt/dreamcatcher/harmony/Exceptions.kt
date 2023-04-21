package net.gmx.szermatt.dreamcatcher.harmony

/** Exception thrown when something fails handling harmony-specific messages. */
internal class HarmonyProtocolException(message: String?) : RuntimeException(message)

/** Exception thrown when Logitech authentication fails.  */
internal class AuthFailedException @JvmOverloads constructor(
    message: String?,
    cause: Throwable? = null
) : RuntimeException(
    String.format("%s: %s", "Logitech authentication failed", message), cause
)

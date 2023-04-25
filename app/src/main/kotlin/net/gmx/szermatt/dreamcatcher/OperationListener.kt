package net.gmx.szermatt.dreamcatcher

import androidx.work.Operation

/**
 * Helper for listening for a successful or failed [Operation].
 */
open class OperationListener(op: Operation) : Runnable {
    private val result = op.result

    /** Override to handle a successful operation. */
    open fun onSuccess() {}

    /** Override to handle a failed operation. */
    open fun onError() {}

    override fun run() {
        try {
            if (result.get() == Operation.SUCCESS) {
                onSuccess()
                return
            }
        } catch (e: Exception) {
        }
        onError()
    }
}
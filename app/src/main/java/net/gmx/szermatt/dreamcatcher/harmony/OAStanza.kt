package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.base.Joiner
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.SimpleIQ
import org.jivesoftware.smack.packet.XMPPError

abstract class OAStanza(protected val mimeType: String?) :
    IQ(object : SimpleIQ("oa", "connect.logitech.com") {}) {
    var statusCode: String? = null
    var errorString: String? = null

    @JsonIgnore // Subclasses use a Jackson object mapper that throws an exception for properties with multiple setters
    override fun setError(error: XMPPError) {
        super.setError(error)
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        if (statusCode != null) {
            xml.attribute("errorcode", statusCode)
        }
        if (errorString != null) {
            xml.attribute("errorstring", errorString)
        }
        xml.attribute("mime", mimeType)
        xml.rightAngleBracket()
        xml.append(joinChildElementPairs(childElementPairs))
        return xml
    }

    private fun joinChildElementPairs(pairs: Map<String, Any?>): String {
        val parts: MutableList<String> = ArrayList()
        for ((key, value) in pairs) {
            parts.add("$key=$value")
        }
        return Joiner.on(":").join(parts)
    }

    protected abstract val childElementPairs: Map<String, Any?>
    val isContinuePacket: Boolean
        get() = "100" == statusCode

    protected fun generateTimestamp(): Long {
        return System.currentTimeMillis() - CREATION_TIME
    }

    companion object {
        private val CREATION_TIME = System.currentTimeMillis()
    }
}
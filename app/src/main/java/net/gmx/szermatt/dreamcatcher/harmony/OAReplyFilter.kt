package net.gmx.szermatt.dreamcatcher.harmony

import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.Stanza
import org.jxmpp.jid.Jid

/**
 * Copied from IQReplyFilter, but tweaked to support the Harmony's response pattern
 */
internal class OAReplyFilter(request: OAStanza, connection: XMPPConnection) : StanzaFilter {
    private val iqAndIdFilter: StanzaFilter
    private val fromFilter: OrFilter
    private val to: Jid?
    private var local: Jid? = null
    private val server: Jid
    private val stanzaId: String

    init {
        to = request.to
        local = if (connection.user == null) {
            // We have not yet been assigned a username, this can happen if the connection is
            // in an early stage, i.e. when performing the SASL auth.
            null
        } else {
            connection.user
        }
        server = connection.serviceName
        stanzaId = request.stanzaId
        val iqFilter: StanzaFilter = OrFilter(IQTypeFilter.ERROR, IQTypeFilter.GET)
        val idFilter: StanzaFilter = StanzaIdFilter(request.stanzaId)
        iqAndIdFilter = AndFilter(iqFilter, idFilter)
        fromFilter = OrFilter()
        fromFilter.addFilter(FromMatchesFilter.createFull(to))
        if (to == null) {
            if (local != null) {
                fromFilter.addFilter(FromMatchesFilter.createBare(local))
            }
            fromFilter.addFilter(FromMatchesFilter.createFull(server))
        } else if (local != null && to.equals(local.asBareJid())) {
            fromFilter.addFilter(FromMatchesFilter.createFull(null))
        }
    }

    override fun accept(stanza: Stanza): Boolean {
        // First filter out everything that is not an IQ stanza and does not have the correct ID set.
        if (!iqAndIdFilter.accept(stanza)) {
            return false
        }

        // Second, check if the from attributes are correct and log potential IQ spoofing attempts
        return if (fromFilter.accept(stanza)) {
            true
        } else {
            println(
                String.format(
                    "Rejected potentially spoofed reply to IQ-stanza. Filter settings: "
                            + "stanzaId=%s, to=%s, local=%s, server=%s. Received stanza with from=%s",
                    stanzaId, to, local, server, stanza.from
                )
            )
            false
        }
    }
}
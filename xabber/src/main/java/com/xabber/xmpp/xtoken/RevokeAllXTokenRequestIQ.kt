package com.xabber.xmpp.xtoken

import com.xabber.android.data.extension.xtoken.XTokenManager
import org.jivesoftware.smack.packet.IQ
import org.jxmpp.jid.DomainBareJid

class RevokeAllXTokenRequestIQ(server: DomainBareJid) : IQ(ELEMENT, XTokenManager.NAMESPACE) {

    init {
        type = Type.set
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder?) = xml?.apply {
        closeEmptyElement()
    }

    private companion object {
        const val ELEMENT = "revoke-all"
    }
}
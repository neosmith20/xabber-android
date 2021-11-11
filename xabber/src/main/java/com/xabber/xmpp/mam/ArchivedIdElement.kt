package com.xabber.xmpp.mam

import com.xabber.android.data.extension.archive.MessageArchiveManager
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.util.XmlStringBuilder

class ArchivedIdElement(val id: String, val by: String) : ExtensionElement {

    override fun getElementName() = ELEMENT

    override fun getNamespace() = NAMESPACE

    override fun toXML() = XmlStringBuilder(this).apply {
        attribute(ATTRIBUTE_ID, id)
        attribute(ATTRIBUTE_BY, by)
        closeEmptyElement()
    }

    companion object {
        const val NAMESPACE = MessageArchiveManager.NAMESPACE
        const val ELEMENT = "archived"
        const val ATTRIBUTE_ID = "id"
        const val ATTRIBUTE_BY = "by"
    }

}
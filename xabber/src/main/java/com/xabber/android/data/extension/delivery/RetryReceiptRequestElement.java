package com.xabber.android.data.extension.delivery;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

public class RetryReceiptRequestElement implements ExtensionElement {

    public static final String ELEMENT = "retry";
    private static final String NAMESPACE = DeliveryManager.NAMESPACE;

    public RetryReceiptRequestElement() {}

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public XmlStringBuilder toXML() {
        XmlStringBuilder xmlStringBuilder = new XmlStringBuilder(this);
        xmlStringBuilder.closeEmptyElement();
        return xmlStringBuilder;
    }

}

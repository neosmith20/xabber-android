package com.xabber.android.data.extension.xtoken;

import com.xabber.android.data.TestApplication;
import com.xabber.xmpp.xtoken.IncomingNewXTokenIQ;
import com.xabber.xmpp.xtoken.XTokenProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = TestApplication.class)
public class XTokenProviderTest {

    private XTokenProvider provider;
    private XmlPullParserFactory factory;
    private String stringToken;

    @Before
    public void setUp() throws Exception {
        provider = new XTokenProvider();
        factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);

        stringToken = "<x xmlns='https://xabber.com/protocol/auth-tokens'>" +
                "<token>VkpTYqfpPcLpwciTRtgHaV7BC9O9kY</token>" +
                "<expire>1536322632</expire>" +
                "<token-uid>49975a48609793c5c93f5e9264f6706f04164</token-uid>" +
                "</x>";
    }

    @Test
    public void parse() {
        IncomingNewXTokenIQ element = parseString(stringToken);
        assertNotNull(element);
        assertEquals("VkpTYqfpPcLpwciTRtgHaV7BC9O9kY", element.getToken());
        assertEquals("49975a48609793c5c93f5e9264f6706f04164", element.getUid());
        assertEquals(1536322632000L, element.getExpire());
    }

    private IncomingNewXTokenIQ parseString(String source) {
        IncomingNewXTokenIQ result = null;
        try {
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(source));
            result = provider.parse(parser, 0);
        } catch (Exception e) {
            fail("Exception while parsing: " + e.toString());
        }
        return result;
    }
}
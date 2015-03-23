package org.mbed.coap.linkformat;

import java.text.ParseException;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;
import org.junit.Test;
import org.mbed.coap.packet.DataConvertingUtility;

/**
 *
 * @author szymon
 */
public class LinkFormatTest {

    public LinkFormatTest() {
    }

    @Test
    public void parseSingle() throws ParseException {

        LinkFormat lf = LinkFormatBuilder.parse("</power>");
        assertEquals("/power", lf.getUri());
        assertEquals(null, lf.getContentType());
        assertNull(lf.getResourceTypeArray());
        assertNull(lf.getInterfaceDescriptionArray());
        assertEquals(false, lf.isObservable());

        lf = LinkFormatBuilder.parse("</temp>;rt=\"test\"");
        assertEquals("/temp", lf.getUri());
        assertEquals(null, lf.getContentType());
        assertArrayEquals(new String[]{"test"}, lf.getResourceTypeArray());
        assertNull(lf.getInterfaceDescriptionArray());
        assertEquals(false, lf.isObservable());

        lf = LinkFormatBuilder.parse("</temp>;obs");
        assertEquals("/temp", lf.getUri());
        assertEquals(null, lf.getContentType());
        assertNull(lf.getResourceTypeArray());
        assertNull(lf.getInterfaceDescriptionArray());
        assertEquals(true, lf.isObservable());
    }

    @Test
    public void parseSingle_withMultCt() throws ParseException {

        LinkFormat lf = LinkFormatBuilder.parse("</power>;ct=\"0 41\"");
        assertEquals("/power", lf.getUri());
        assertEquals(0, lf.getContentType().intValue());
    }

    @Test
    public void parseList() throws ParseException {

        LinkFormat[] lfArr = LinkFormatBuilder.parseList("</power>;rt=\"rt-test\",</temp>;obs;if=\"if-test\"");
        LinkFormat lf1 = lfArr[0];
        LinkFormat lf2 = lfArr[1];
        assertEquals("/power", lf1.getUri());
        assertEquals(null, lf1.getContentType());
        assertArrayEquals(new String[]{"rt-test"}, lf1.getResourceTypeArray());
        assertNull(lf1.getInterfaceDescriptionArray());
        assertEquals(false, lf1.isObservable());

        assertEquals("/temp", lf2.getUri());
        assertEquals(null, lf2.getContentType());
        assertNull(lf2.getResourceTypeArray());
        assertArrayEquals(new String[]{"if-test"}, lf2.getInterfaceDescriptionArray());
        assertEquals(true, lf2.isObservable());

    }

    @Test
    public void testSingle() throws ParseException {
        LinkFormat lf = new LinkFormat("/power");
        lf.setObservable(Boolean.TRUE);
        lf.setResourceType("rt-test", "rt-test2");
        lf.setInterfaceDescription("if-test");
        lf.setMaximumSize(123);
        lf.setResourceInstance("ri-test");
        lf.setExport(Boolean.TRUE);
        lf.setMedia("text/plain");
        lf.setTitle("my title");
        lf.setType("example type");
        lf.setAnchor("/test/anch");
        lf.setContentType((short) 12);

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());

        assertEquals(lf, lf2);
        assertEquals(lf.getUri(), lf2.getUri());
        assertEquals(lf.getContentType(), lf2.getContentType());
        assertArrayEquals(lf.getResourceTypeArray(), lf2.getResourceTypeArray());
        assertArrayEquals(lf.getInterfaceDescriptionArray(), lf2.getInterfaceDescriptionArray());
        assertEquals(lf.isObservable(), lf2.isObservable());
        assertEquals(lf.getMaxSize(), lf2.getMaxSize());
        assertEquals(lf.getResourceInstance(), lf2.getResourceInstance());
        assertEquals(lf.getExport(), lf2.getExport());
        assertEquals(lf.getMedia(), lf2.getMedia());
        assertEquals(lf.getTitle(), lf2.getTitle());
        assertEquals(lf.getType(), lf2.getType());
        assertEquals(lf.getAnchor(), lf2.getAnchor());
    }

    @Test
    public void testSingle2() throws ParseException {
        LinkFormat lf = new LinkFormat("/power");
        lf.set("obs", Boolean.TRUE);
        lf.set("rt", "rt-test", "rt-test2");
        lf.set("if", "if-test");
        lf.set("sz", 123);
        lf.set("ins", "ri-test");
        lf.set("exp", Boolean.TRUE);
        lf.set("media", "text/plain");
        lf.set("title", "my title");
        lf.set("type", "example type");
        lf.set("anchor", "/test/anch");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());

        assertEquals(lf, lf2);
        assertEquals(lf.getUri(), lf2.getUri());
        assertEquals(lf.getContentType(), lf2.getContentType());
        assertArrayEquals(lf.getResourceTypeArray(), lf2.getResourceTypeArray());
        assertArrayEquals(lf.getInterfaceDescriptionArray(), lf2.getInterfaceDescriptionArray());
        assertEquals(lf.isObservable(), lf2.isObservable());
        assertEquals(lf.getMaxSize(), lf2.getMaxSize());
        assertEquals(lf.getResourceInstance(), lf2.getResourceInstance());
        assertEquals(lf.getExport(), lf2.getExport());
        assertEquals(lf.getMedia(), lf2.getMedia());
        assertEquals(lf.getTitle(), lf2.getTitle());
        assertEquals(lf.getType(), lf2.getType());
        assertEquals(lf.getAnchor(), lf2.getAnchor());
    }

    @Test
    public void createSingleWithCustomParameters() throws ParseException {
        LinkFormat lf = new LinkFormat("/power");
        lf.setResourceType("rt-test");
        lf.set("atr1", "val1");
        lf.set("atr2", 22);
        lf.set("atr3", Boolean.TRUE);

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());

        assertEquals(lf.toString(), lf2.toString());
        assertEquals(lf.getParam("atr1"), lf2.getParam("atr1"));
        assertEquals(lf.getParam("atr2"), lf2.getParam("atr2"));
        assertEquals(lf.getParam("atr3"), lf2.getParam("atr3"));
        assertEquals(lf.getUri(), lf2.getUri());
        assertEquals(lf.getContentType(), lf2.getContentType());
        assertArrayEquals(lf.getResourceTypeArray(), lf2.getResourceTypeArray());
        assertArrayEquals(lf.getInterfaceDescriptionArray(), lf2.getInterfaceDescriptionArray());
        assertEquals(lf.isObservable(), lf2.isObservable());
    }

    @Test
    public void testSuccessLinkRelations() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setRelations("rel1");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertArrayEquals(new String[]{"rel1"}, lf2.getRelations());

        //multiple relations
        lf.setRelations("rel1", "rel2", "rel3");

        lf2 = LinkFormatBuilder.parse(lf.toString());
        assertArrayEquals(new String[]{"rel1", "rel2", "rel3"}, lf2.getRelations());

        //parse
        assertArrayEquals(new String[]{"rel1", "rel2"}, LinkFormatBuilder.parse("</dd>;rel=\"rel1 rel2\"").getRelations());
        assertArrayEquals(new String[]{"relation-example-one"}, LinkFormatBuilder.parse("</dd>;rel=\"relation-example-one\"").getRelations());
        assertArrayEquals(new String[]{""}, LinkFormatBuilder.parse("</dd>;rel=\"\"").getRelations());
        assertNull(LinkFormatBuilder.parse("</dd>").getRelations());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailureLinkRelations() {
        LinkFormat lf = new LinkFormat("/test");
        lf.setRelations("fdsfs", "rel1 rel2");
    }

    @Test
    public void testSuccesfullAnchor() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setAnchor("/s/temp");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertEquals("/s/temp", lf2.getAnchor());
        assertEquals("/s/temp", lf.getAnchor());

        //parse
        assertEquals("/s/temp", LinkFormatBuilder.parse("</dd>;anchor=\"/s/temp\"").getAnchor());
        assertEquals("", LinkFormatBuilder.parse("</dd>;anchor=\"\"").getAnchor());
        assertNull(LinkFormatBuilder.parse("</dd>").getAnchor());
    }

    @Test
    public void testSuccesfullRev() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setRev("rev1", "rev2");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertArrayEquals(new String[]{"rev1", "rev2"}, lf2.getRev());

        //multiple relations
        lf.setRev("rev1", "rev2", "rev3");

        lf2 = LinkFormatBuilder.parse(lf.toString());
        assertArrayEquals(new String[]{"rev1", "rev2", "rev3"}, lf2.getRev());

        //parse
        assertArrayEquals(new String[]{"rev1", "rev2"}, LinkFormatBuilder.parse("</dd>;rev=\"rev1 rev2\"").getRev());
        assertArrayEquals(new String[]{"rev-example-one"}, LinkFormatBuilder.parse("</dd>;rev=\"rev-example-one\"").getRev());
        assertArrayEquals(new String[]{""}, LinkFormatBuilder.parse("</dd>;rev=\"\"").getRev());
        assertNull(LinkFormatBuilder.parse("</dd>").getRev());
    }

    @Test
    public void testSuccesfullHReflang() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setHRefLang("LANG-PL");

        assertEquals("</test>;hreflang=LANG-PL", lf.toString());

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertEquals("LANG-PL", lf2.getHRefLang());
        assertEquals("LANG-PL", lf.getHRefLang());

        //parse
        assertEquals("LANG-PL", LinkFormatBuilder.parse("</dd>;hreflang=LANG-PL").getHRefLang());
        assertEquals("", LinkFormatBuilder.parse("</dd>;hreflang=").getHRefLang());
        assertNull(LinkFormatBuilder.parse("</dd>").getHRefLang());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailHReflang1() {
        new LinkFormat("/test").setHRefLang(" LANG-PL");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailHReflang2() {
        new LinkFormat("/test").setHRefLang("LANG-PL" + (char) 128);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailHReflang3() {
        new LinkFormat("/test").setHRefLang("LAN,G;P\\L");
    }

    @Test(expected = ParseException.class)
    public void testFailHReflang4() throws ParseException {
        LinkFormatBuilder.parse("</test>;hreflang=LANGP L");
    }

    @Test
    public void testSuccesfullTitle() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setTitle("test title");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertEquals("test title", lf2.getTitle());
        assertEquals("test title", lf.getTitle());

        //parse
        assertEquals("test title\\test", LinkFormatBuilder.parse("</dd>;title=\"test title\\test\"").getTitle());
        assertEquals("", LinkFormatBuilder.parse("</dd>;title=\"\"").getTitle());
        assertNull(LinkFormatBuilder.parse("</dd>").getTitle());
    }

    @Test
    public void testSuccesfullType() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setType("test type");

        LinkFormat lf2 = LinkFormatBuilder.parse(lf.toString());
        assertEquals("test type", lf2.getType());
        assertEquals("test type", lf.getType());

        //parse
        assertEquals("test type\\test", LinkFormatBuilder.parse("</dd>;type=\"test type\\test\"").getType());
        assertEquals("", LinkFormatBuilder.parse("</dd>;type=\"\"").getType());
        assertNull(LinkFormatBuilder.parse("</dd>").getType());
    }

    @Test
    public void testFilter() throws ParseException {
        final String LINK = "</test/1>;rt=\"dummy-rt\","
                + "</test/2>;rt=\"dummy-rt dummy-rt2\";obs;media=text/plain,"
                + "</test/3>;rt=\"dummy-rt3\";obs";

        List<LinkFormat> list = LinkFormatBuilder.parseLinkAsList(LINK);

        List<LinkFormat> links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy-rt3"));
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/3", links.get(0).getUri());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy-rt"));
        assertNotNull(links);
        assertEquals(2, links.size());
        assertEquals("/test/1", links.get(0).getUri());
        assertEquals("/test/2", links.get(1).getUri());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy-rt3&obs=true"));
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/3", links.get(0).getUri());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("media=text/plain"));
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/2", links.get(0).getUri());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy-rt3&media=text/plain"));
        assertNotNull(links);
        assertEquals(0, links.size());
    }

    @Test
    public void testFilterWithPrefix() throws ParseException {
        final String LINK = "</test/1>;rt=\"dummy-rt\","
                + "</test/2>;rt=\"dummy-rt dummy-rt2\";obs;media=text/plain,"
                + "</test/3>;rt=\"dummy-rt3\";obs,"
                + "</test/4>";

        List<LinkFormat> list = LinkFormatBuilder.parseLinkAsList(LINK);

        List<LinkFormat> links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy-*"));
        assertNotNull(links);
        assertEquals(3, links.size());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=*"));
        assertNotNull(links);
        assertEquals(3, links.size());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("obs=*"));
        assertNotNull(links);
        assertEquals(2, links.size());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("media=text*"));
        assertNotNull(links);
        assertEquals(1, links.size());
        assertEquals("/test/2", links.get(0).getUri());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("rt=dummy*&media=text/plain"));
        assertNotNull(links);
        assertEquals(1, links.size());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("href=/test/1"));
        assertNotNull(links);
        assertEquals(1, links.size());

        links = LinkFormatBuilder.filter(list, DataConvertingUtility.parseUriQuery("href=/test*"));
        assertNotNull(links);
        assertEquals(4, links.size());
    }

    @Test(expected = ParseException.class)
    public void testFailParse1() throws ParseException {
        LinkFormatBuilder.parse("</test>;rt=DUpa");
    }

    @Test(expected = ParseException.class)
    public void testFailParse2() throws ParseException {
        LinkFormatBuilder.parse("</test>;hreflang=\"LAng-PL\"");
    }

    @Test(expected = ParseException.class)
    public void testFailParse3() throws ParseException {
        LinkFormatBuilder.parse("</test>;sz=\"seven\"");
    }

    @Test(expected = ParseException.class)
    public void testFailParse4() throws ParseException {
        LinkFormatBuilder.parse("</test>;title");
    }

    @Test(expected = ParseException.class)
    public void testFailParse4_1() throws ParseException {
        LinkFormatBuilder.parse("</test>;rt");
    }

    @Test(expected = ParseException.class)
    public void testFailParse5_missing_uri() throws ParseException {
        LinkFormatBuilder.parse("/fds>;title=\"tytul\"");
    }

    @Test(expected = ParseException.class)
    public void testFailParse6() throws ParseException {
        LinkFormatBuilder.parse("/fds>;title=\"tytul\"fds");
    }

    @Test(expected = ParseException.class)
    public void testFailParse7() throws ParseException {
        LinkFormatBuilder.parse("</fds>;title=\"tytul\"dfdss\"");
    }

    @Test(expected = ParseException.class)
    public void testFailParse8() throws ParseException {
        LinkFormatBuilder.parse("</fds>;obs=\"true\"");
    }

    @Test(expected = ParseException.class)
    public void testFailParse9() throws ParseException {
        LinkFormatBuilder.parse("</fds>;=\"true\"");
    }

    @Test
    public void testSuccessSpecialCases() throws ParseException {
        assertEquals("ti=le@sns", LinkFormatBuilder.parse("</fds>;title=\"ti=le@sns\"").getTitle());
        assertEquals("text/pla=in", LinkFormatBuilder.parse("</fds>;media=text/pla=in").getMedia());
        assertEquals("/fds", LinkFormatBuilder.parse("</fds>").getUri());

        LinkFormat lf = new LinkFormat("/test");
        lf.setContentType(null);
        assertNull(LinkFormatBuilder.parse(lf.toString()).getContentType());

    }

    @Test
    public void fuzzyTest() {
        int length = 20;
        Random rnd = new Random();
        char[] text = new char[length];
        String fuzzyLf;

        for (int i = 0; i < 100; i++) {
            for (int k = 0; k < length; k++) {
                text[k] = (char) (27 + rnd.nextInt(100));
            }
            fuzzyLf = new String(text);

            try {
                LinkFormatBuilder.parse(fuzzyLf);
                assertFalse("exception ParseException is expected", false);
            } catch (ParseException ex) {
                //only ParseException is expected
            }
        }
    }

    @Test
    public void testNullValues() throws ParseException {
        LinkFormat lf = new LinkFormat("/test");
        lf.setObservable(Boolean.TRUE);
        lf.setResourceType("rt-test", "rt-test2");
        lf.setInterfaceDescription("if-test");
        lf.setMaximumSize(123);
        lf.setResourceInstance("ri-test");
        lf.setExport(Boolean.TRUE);
        lf.setMedia("text/plain");
        lf.setTitle("my title");
        lf.setType("example type");
        lf.setAnchor("/test/anch");
        lf.setContentType((short) 12);

        String nullVal = null;
        //set null
        lf.setObservable(null);
        lf.setResourceType(nullVal);
        lf.setInterfaceDescription((String[]) null);
        lf.setRev(new String[0]);
        lf.setMaximumSize(null);
        lf.setResourceInstance(null);
        lf.setExport(null);
        lf.setMedia(null);
        lf.setTitle(null);
        lf.setType(null);
        lf.setAnchor(null);
        lf.setContentType(null);
        lf.setRelations(new String[]{null});

        assertEquals(Boolean.FALSE, lf.getObservable());
        assertNull(lf.getResourceTypeArray());
        assertNull(lf.getInterfaceDescriptionArray());
        assertNull(lf.getMaximumSize());
        assertNull(lf.getResourceInstance());
        assertNull(lf.getExport());
        assertNull(lf.getMedia());
        assertNull(lf.getTitle());
        assertNull(lf.getAnchor());
        assertNull(lf.getContentType());
        assertNull(lf.getRev());
        assertNull(lf.getRelations());

        assertEquals(new LinkFormat("/test"), LinkFormatBuilder.parse(lf.toString()));

    }

    @Test
    public void linkFormatWithUnknownParams() throws ParseException {

        String linkFormatString = "</deva/temp>;if=\"ns.wadl#c\";unknown=\"param-value\";unknown2=TEST-TOKEN;unknown3;ct=\"1398\"";
        LinkFormat lf = LinkFormatBuilder.parse(linkFormatString);

        LinkFormat lf2 = new LinkFormat("/deva/temp");
        lf2.setInterfaceDescription("ns.wadl#c");
        lf2.setContentType((short) 1398);
        lf2.set("unknown", "param-value");
        lf2.set("unknown2", new PToken("TEST-TOKEN"));
        lf2.set("unknown3", Boolean.TRUE);

//        assertEquals("/deva/temp", lf[0].getUri());
//        assertArrayEquals(new String[]{"ns.wadl#c"}, lf[0].getInterfaceDescription());
//        assertNull(lf[0].getResourceType());
//        assertEquals((Short) (short) 1398, lf[0].getContentType());
        assertEquals("param-value", lf.getParam("unknown"));
        assertEquals(lf2, lf);
    }
}

package microbenchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mbed.coap.packet.BlockOption;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.MediaTypes;
import org.mbed.coap.packet.MessageType;
import org.mbed.coap.packet.Method;
import org.mbed.coap.exception.CoapException;

/**
 *
 * @author szymon
 */
public class ParsingBenchmark {

    long stTime, endTime;

    private CoapPacket packet;

    @Before
    public void warmUp() throws CoapException {
        LogManager.getRootLogger().setLevel(Level.INFO);

        packet = new CoapPacket(Method.GET, MessageType.Confirmable, "/path-pppppppppppppppppp1/path-dddddddddd-2/dfdshffsdkjfhsdks3/444444444444444444444444444444444444444/55555555555555555555555555555555555555555555555", null);
        packet.setMessageId(1234);
        packet.setToken(new byte[]{1, 2, 3, 4, 5});
        packet.headers().setMaxAge(4321L);
        packet.headers().setEtag(new byte[]{89, 10, 31, 7, 1});
        packet.headers().setObserve(9876);
        packet.headers().setBlock1Req(new BlockOption(13, BlockSize.S_16, true));
        packet.headers().setContentFormat(MediaTypes.CT_APPLICATION_XML);
        packet.headers().setLocationPath("/1/222/33333/4444444/555555555555555555555555");
        packet.headers().setUriQuery("ppar=val1&par222222222222222222222=val2222222222222222222222222222222222");
        packet.setPayload("<k>12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890</k>");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        packet.writeTo(baos);
        CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));

        System.out.println("MSG SIZE: " + packet.toByteArray().length);
    }

    @After
    public void coolDown() {
        System.out.println("TIME: " + (endTime - stTime) + " MSG-PER-SEC: " + (1000000 / ((endTime - stTime))) + "k");
    }

    @Test
    public void parsing_1000k() throws CoapException {
        stTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //ByteOutputStream baos = new ByteOutputStream();
            packet.writeTo(baos);

            CoapPacket.deserialize(null, new ByteArrayInputStream(baos.toByteArray()));
            //CoapPacket.deserialize(new ByteInputStream(baos));
        }
        endTime = System.currentTimeMillis();
    }

    //  MICRO-BENCHMARK RESULTS
    //----------------------------------
    //CPU:                Intel Core i5
    //Iterations:               100 000
    //----------------------------------
    //ByteOutputStream:         2717 ms
    //ByteArrayOutputStream:    3205 ms
    //----------------------------------
}

package org.example;

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.FidelityOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExiProcessorTest {

    private ExiProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        processor = new ExiProcessor();
    }

    @Test
    void encodeXmlToExi() throws Exception {
        byte[] xmlBytes = loadResource("/PositionReport.xml");

        ByteArrayOutputStream exiOut = processor.encode(new ByteArrayInputStream(xmlBytes));

        assertNotNull(exiOut);
        assertTrue(exiOut.size() > 0, "EXI output should be non-empty");
        assertNotEquals(xmlBytes.length, exiOut.size(), "EXI output should differ in size from XML input");
    }

    @Test
    void decodeExiToXml() throws Exception {
        byte[] xmlBytes = loadResource("/PositionReport.xml");
        ByteArrayOutputStream exiOut = processor.encode(new ByteArrayInputStream(xmlBytes));
        ByteArrayOutputStream xmlOut = processor.decode(new ByteArrayInputStream(exiOut.toByteArray()));

        assertNotNull(xmlOut);
        assertTrue(xmlOut.size() > 0, "Round-tripped XML output should be non-empty");
        String xmlString = xmlOut.toString();
        assertTrue(xmlString.contains("PositionReport"), "Output should contain PositionReport element");
    }

    @Test
    void withOptions_appliesAlignmentAndFidelity() throws Exception {
        try (var opts = getClass().getResourceAsStream("/exi_options_byte_aligned.xml")) {
            assertNotNull(opts, "Options resource not found");
            ExiProcessor p = new ExiProcessor(opts);

            assertEquals(CodingMode.BYTE_PACKED, p.exiFactory.getCodingMode());
            assertEquals(1024, p.exiFactory.getValueMaxLength());
            assertTrue(p.exiFactory.getFidelityOptions().isFidelityEnabled(FidelityOptions.FEATURE_PREFIX));
            assertTrue(p.exiFactory.getFidelityOptions().isFidelityEnabled(FidelityOptions.FEATURE_COMMENT));
        }
    }

    @Test
    void withOptions_roundTripSucceeds() throws Exception {
        byte[] xmlBytes = loadResource("/PositionReport.xml");
        ExiProcessor p;
        try (var opts = getClass().getResourceAsStream("/exi_options_byte_aligned.xml")) {
            p = new ExiProcessor(opts);
        }

        ByteArrayOutputStream exiOut = p.encode(new ByteArrayInputStream(xmlBytes));
        assertTrue(exiOut.size() > 0);
        ByteArrayOutputStream xmlOut = p.decode(new ByteArrayInputStream(exiOut.toByteArray()));
        assertTrue(xmlOut.toString().contains("PositionReport"));
    }

    @Test
    void withOptions_compressionMode() throws Exception {
        String optionsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <header xmlns="http://www.w3.org/2009/exi">
                  <common><compression/></common>
                </header>
                """;
        ExiProcessor p = new ExiProcessor(new ByteArrayInputStream(optionsXml.getBytes()));
        assertEquals(CodingMode.COMPRESSION, p.exiFactory.getCodingMode());

        byte[] xmlBytes = loadResource("/PositionReport.xml");
        ByteArrayOutputStream exiOut = p.encode(new ByteArrayInputStream(xmlBytes));
        assertTrue(exiOut.size() > 0);
        ByteArrayOutputStream xmlOut = p.decode(new ByteArrayInputStream(exiOut.toByteArray()));
        assertTrue(xmlOut.toString().contains("PositionReport"));
    }

    @Test
    void withOptions_blockSizeAndValuePartitionCapacity() throws Exception {
        String optionsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <header xmlns="http://www.w3.org/2009/exi">
                  <lesscommon>
                    <uncommon>
                      <valuePartitionCapacity>512</valuePartitionCapacity>
                    </uncommon>
                    <blockSize>100000</blockSize>
                  </lesscommon>
                </header>
                """;
        ExiProcessor p = new ExiProcessor(new ByteArrayInputStream(optionsXml.getBytes()));
        assertEquals(512, p.exiFactory.getValuePartitionCapacity());
        assertEquals(100000, p.exiFactory.getBlockSize());
    }

    private byte[] loadResource(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Test resource not found: " + path);
            return stream.readAllBytes();
        }
    }
}

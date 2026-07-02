package org.example;

import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;
import com.siemens.ct.exi.grammars.GrammarFactory;
import com.siemens.ct.exi.main.api.sax.EXIResult;
import com.siemens.ct.exi.main.api.sax.EXISource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ExiProcessor {
    // Default schema path, relative to the working directory, used when the
    // caller does not supply one.
    static final String DEFAULT_SCHEMA = "./schemas/UCI_MessageDefinitions_v2_5_0.xsd";

    EXIFactory exiFactory;

    public ExiProcessor() throws EXIException {
        this(DEFAULT_SCHEMA);
    }

    // schemaPath: the .xsd to build the EXI grammar from. A null or empty value
    // falls back to DEFAULT_SCHEMA. Callers supply this via the exi_init argument.
    public ExiProcessor(String schemaPath) throws EXIException {
        if (schemaPath == null || schemaPath.isEmpty()) {
            schemaPath = DEFAULT_SCHEMA;
        }
        exiFactory = DefaultEXIFactory.newInstance();
        Grammars grammars = GrammarFactory
                .newInstance()
                .createGrammars(schemaPath);
        exiFactory.setGrammars(grammars);
    }

    public ByteArrayOutputStream encode(ByteArrayInputStream xml) throws EXIException, IOException, SAXException, ParserConfigurationException {
        ByteArrayOutputStream exiOut = new ByteArrayOutputStream();
        EXIResult exiResult = new EXIResult(exiFactory);
        exiResult.setOutputStream(exiOut);
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(exiResult.getHandler());
        xmlReader.parse(new InputSource(xml));
        exiOut.close();

        return exiOut;
    }

    public ByteArrayOutputStream decode(ByteArrayInputStream exi) throws EXIException, TransformerException {
        ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
        Result result = new StreamResult(xmlOut);
        SAXSource exiSource = new EXISource(exiFactory);
        InputSource is = new InputSource(exi);
        exiSource.setInputSource(is);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(exiSource, result);

        return xmlOut;
    }
}

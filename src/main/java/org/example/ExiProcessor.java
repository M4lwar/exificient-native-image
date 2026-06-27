package org.example;

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;
import com.siemens.ct.exi.grammars.GrammarFactory;
import com.siemens.ct.exi.main.api.sax.EXIResult;
import com.siemens.ct.exi.main.api.sax.EXISource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExiProcessor {
    private static final String EXI_NS = "http://www.w3.org/2009/exi";

    EXIFactory exiFactory;

    public ExiProcessor() throws EXIException {
        exiFactory = DefaultEXIFactory.newInstance();
        Grammars grammars = GrammarFactory
                .newInstance()
                .createGrammars("./schemas/UCI_MessageDefinitions_v2_5_0.xsd");
        exiFactory.setGrammars(grammars);
    }

    public ExiProcessor(InputStream optionsXml) throws EXIException, ParserConfigurationException, SAXException, IOException {
        this();
        applyOptions(exiFactory, optionsXml);
    }

    static void applyOptions(EXIFactory factory, InputStream optionsXml)
            throws EXIException, ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(optionsXml);
        Element header = doc.getDocumentElement();

        FidelityOptions fidelity = FidelityOptions.createDefault();

        Element lesscommon = directChild(header, "lesscommon");
        if (lesscommon != null) {
            Element blockSize = directChild(lesscommon, "blockSize");
            if (blockSize != null)
                factory.setBlockSize(Integer.parseInt(blockSize.getTextContent().trim()));

            Element preserve = directChild(lesscommon, "preserve");
            if (preserve != null) {
                if (directChild(preserve, "dtd") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_DTD, true);
                if (directChild(preserve, "prefixes") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
                if (directChild(preserve, "lexicalValues") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
                if (directChild(preserve, "comments") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_COMMENT, true);
                if (directChild(preserve, "pis") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_PI, true);
            }

            Element uncommon = directChild(lesscommon, "uncommon");
            if (uncommon != null) {
                Element alignment = directChild(uncommon, "alignment");
                if (alignment != null) {
                    if (directChild(alignment, "byte") != null)
                        factory.setCodingMode(CodingMode.BYTE_PACKED);
                    else if (directChild(alignment, "pre-compress") != null)
                        factory.setCodingMode(CodingMode.PRE_COMPRESSION);
                }
                if (directChild(uncommon, "selfContained") != null)
                    fidelity.setFidelity(FidelityOptions.FEATURE_SC, true);
                Element valueMaxLength = directChild(uncommon, "valueMaxLength");
                if (valueMaxLength != null)
                    factory.setValueMaxLength(Integer.parseInt(valueMaxLength.getTextContent().trim()));
                Element valuePartitionCapacity = directChild(uncommon, "valuePartitionCapacity");
                if (valuePartitionCapacity != null)
                    factory.setValuePartitionCapacity(Integer.parseInt(valuePartitionCapacity.getTextContent().trim()));
            }
        }

        Element common = directChild(header, "common");
        if (common != null) {
            if (directChild(common, "compression") != null)
                factory.setCodingMode(CodingMode.COMPRESSION);
            if (directChild(common, "fragment") != null)
                factory.setFragment(true);
        }

        // strict must be applied last — it clears all other fidelity options
        if (directChild(header, "strict") != null)
            fidelity.setFidelity(FidelityOptions.FEATURE_STRICT, true);

        factory.setFidelityOptions(fidelity);
    }

    private static Element directChild(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && EXI_NS.equals(n.getNamespaceURI())
                    && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
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

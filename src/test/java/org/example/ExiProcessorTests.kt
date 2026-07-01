package org.example

import com.diffplug.selfie.Selfie.expectSelfie
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class ExiProcessorTests {
    private var processor: ExiProcessor = ExiProcessor()

    data class ExiWithLen(val len: Int, val exi: String) {
        constructor(exiBytes: ByteArray) : this(
            exiBytes.size,
            exiBytes.joinToString("") { "%02x".format(it) },
        )
    }

    fun String.prettyPrintXml(): String {
        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(this)))

        val transformer = TransformerFactory
            .newInstance()
            .newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")

        val writer = StringWriter()
        transformer.transform(
            DOMSource(document),
            StreamResult(writer)
        )
        return writer.toString()
    }

    @Test
    fun encodeWithPositionReportMatchesSnapshot() {
        // Arrange
        val xmlBytes = javaClass.getResourceAsStream("/PositionReport.xml")!!.use{ it.readBytes() }

        // Act
        val exiOut = processor.encode(ByteArrayInputStream(xmlBytes))!!.toByteArray()

        // Assert
        expectSelfie(ExiWithLen(exiOut).toString()).toMatchDisk()
    }

    @Test
    fun decodeWithPositionReportMatchesSnapshot() {
        // Arrange
        val xmlBytes = javaClass.getResourceAsStream("/PositionReport.xml")!!.use{ it.readBytes() }
        val exiOut = processor.encode(ByteArrayInputStream(xmlBytes))

        // Act
        val xmlOut = processor.decode(ByteArrayInputStream(exiOut.toByteArray()))

        // Assert
        expectSelfie(xmlOut.toString().prettyPrintXml().trim()).toMatchDisk()
    }
}
package com.developer_rahul.docunova

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * High-performance, lightweight, and crash-proof DOCX generator and parser.
 * Generates standard Office Open XML (ISO/IEC 29500) documents compatible with
 * Microsoft Word, Google Docs, Office 365, and Android Document Viewers.
 * Zero dependency on Apache POI / XMLBeans.
 */
object DocxHelper {

    private const val TAG = "DocxHelper"

    private const val CONTENT_TYPES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    /**
     * Creates a valid .docx file byte array from the given text/spanned content.
     * Preserves bold and underline spans if content is Spanned.
     */
    fun createDocx(content: CharSequence): ByteArray {
        val documentXml = buildDocumentXml(content)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(CONTENT_TYPES_XML.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(RELS_XML.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 3. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    /**
     * Extracts plain text from a .docx file or stream by parsing word/document.xml.
     */
    fun extractText(file: File): String {
        return FileInputStream(file).use { extractText(it) }
    }

    fun extractText(inputStream: InputStream): String {
        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (entryName.equals("word/document.xml", ignoreCase = true) ||
                    entryName.endsWith("/word/document.xml", ignoreCase = true)
                ) {
                    val entryBytes = zis.readBytes()
                    return parseDocumentXml(ByteArrayInputStream(entryBytes))
                }
                entry = zis.nextEntry
            }
        } catch (t: Throwable) {
            logError("Error extracting text from docx", t)
        }
        return ""
    }

    private fun logError(message: String, t: Throwable) {
        try {
            Log.e(TAG, message, t)
        } catch (_: Throwable) {
            System.err.println("$TAG: $message: ${t.message}")
        }
    }

    private fun parseDocumentXml(inputStream: InputStream): String {
        val sb = StringBuilder()
        try {
            val factory = javax.xml.parsers.SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val saxParser = factory.newSAXParser()
            var inText = false

            saxParser.parse(inputStream, object : org.xml.sax.helpers.DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
                    val tag = qName ?: localName ?: ""
                    if (tag.equals("w:t", ignoreCase = true) || tag.equals("t", ignoreCase = true)) {
                        inText = true
                    } else if (tag.equals("w:tab", ignoreCase = true) || tag.equals("tab", ignoreCase = true)) {
                        sb.append("\t")
                    } else if (tag.equals("w:br", ignoreCase = true) || tag.equals("br", ignoreCase = true)) {
                        sb.append("\n")
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText) {
                        sb.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val tag = qName ?: localName ?: ""
                    if (tag.equals("w:t", ignoreCase = true) || tag.equals("t", ignoreCase = true)) {
                        inText = false
                    } else if (tag.equals("w:p", ignoreCase = true) || tag.equals("p", ignoreCase = true)) {
                        sb.append("\n")
                    } else if (tag.equals("w:tc", ignoreCase = true) || tag.equals("tc", ignoreCase = true)) {
                        sb.append(" | ")
                    }
                }
            })
        } catch (t: Throwable) {
            logError("Error parsing docx document.xml", t)
        }
        return sb.toString().trim()
    }

    private fun buildDocumentXml(content: CharSequence): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("""<w:body>""")

        val rawText = content.toString()
        val paragraphs = rawText.split(Regex("\r?\n"))

        for (pText in paragraphs) {
            sb.append("<w:p>")
            if (pText.isNotEmpty()) {
                if (content is Spanned) {
                    val pStart = rawText.indexOf(pText)
                    if (pStart != -1) {
                        val pEnd = pStart + pText.length
                        var i = pStart
                        while (i < pEnd) {
                            val next = content.nextSpanTransition(i, pEnd, Any::class.java)
                            val subText = content.subSequence(i, next).toString()
                            val isBold = content.getSpans(i, next, StyleSpan::class.java).any { it.style == Typeface.BOLD }
                            val isUnderline = content.getSpans(i, next, UnderlineSpan::class.java).isNotEmpty()

                            appendRun(sb, subText, isBold, isUnderline)
                            i = next
                        }
                    } else {
                        appendRun(sb, pText, isBold = false, isUnderline = false)
                    }
                } else {
                    appendRun(sb, pText, isBold = false, isUnderline = false)
                }
            }
            sb.append("</w:p>")
        }

        // Section properties with standard A4 margins
        sb.append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun appendRun(sb: StringBuilder, text: String, isBold: Boolean, isUnderline: Boolean) {
        if (text.isEmpty()) return
        sb.append("<w:r>")
        sb.append("<w:rPr>")
        sb.append("""<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>""")
        sb.append("""<w:sz w:val="22"/>""") // 11pt
        if (isBold) {
            sb.append("<w:b/>")
        }
        if (isUnderline) {
            sb.append("""<w:u w:val="single"/>""")
        }
        sb.append("</w:rPr>")
        sb.append("""<w:t xml:space="preserve">""")
        sb.append(escapeXml(text))
        sb.append("</w:t>")
        sb.append("</w:r>")
    }

    private fun escapeXml(input: String): String {
        val out = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&apos;")
                else -> {
                    // Filter out invalid XML 1.0 characters
                    if (c.code in 0x20..0xD7FF || c == '\t' || c == '\n' || c == '\r' || c.code in 0xE000..0xFFFD) {
                        out.append(c)
                    }
                }
            }
        }
        return out.toString()
    }
}

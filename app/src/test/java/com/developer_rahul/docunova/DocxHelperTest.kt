package com.developer_rahul.docunova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DocxHelperTest {

    @Test
    fun testCreateAndExtractDocx() {
        val sampleText = "Hello Docunova!\nThis is a test of the crash-proof DOCX generator and parser."
        val docxBytes = DocxHelper.createDocx(sampleText)

        assertTrue("Generated docx byte array should not be empty", docxBytes.isNotEmpty())

        // Verify valid ZIP structure
        var hasDocumentXml = false
        var hasContentTypes = false
        var hasRels = false

        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "word/document.xml" -> hasDocumentXml = true
                    "[Content_Types].xml" -> hasContentTypes = true
                    "_rels/.rels" -> hasRels = true
                }
                entry = zis.nextEntry
            }
        }

        assertTrue("Should contain word/document.xml", hasDocumentXml)
        assertTrue("Should contain [Content_Types].xml", hasContentTypes)
        assertTrue("Should contain _rels/.rels", hasRels)

        // Verify text extraction
        val extracted = DocxHelper.extractText(ByteArrayInputStream(docxBytes))
        assertTrue("Extracted text should contain 'Hello Docunova!'", extracted.contains("Hello Docunova!"))
        assertTrue("Extracted text should contain 'crash-proof DOCX generator'", extracted.contains("crash-proof DOCX generator"))
    }

    @Test
    fun testEmptyDocxDoesNotCrash() {
        val docxBytes = DocxHelper.createDocx("")
        assertTrue(docxBytes.isNotEmpty())

        val extracted = DocxHelper.extractText(ByteArrayInputStream(docxBytes))
        assertEquals("", extracted)
    }

    @Test
    fun testCorruptedStreamDoesNotCrash() {
        val corruptBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val extracted = DocxHelper.extractText(ByteArrayInputStream(corruptBytes))
        assertEquals("", extracted)
    }
}

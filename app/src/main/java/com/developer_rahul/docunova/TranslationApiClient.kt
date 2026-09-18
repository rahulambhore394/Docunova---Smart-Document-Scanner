package com.developer_rahul.docunova

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight, fast translation client using public web APIs.
 * Eliminates heavy ML Kit offline model downloads (30MB-50MB per language).
 */
object TranslationApiClient {

    private const val TAG = "TranslationApiClient"
    private const val GOOGLE_TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
    private const val MYMEMORY_URL = "https://api.mymemory.translated.net/get"

    suspend fun translate(text: String, targetLanguageCode: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")

        // 1. Primary: Google Translate endpoint (auto-detects source language, instant, handles formatting)
        try {
            val translated = translateViaGoogle(text, targetLanguageCode)
            if (translated.isNotBlank()) {
                return@withContext Result.success(translated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Translate API failed, trying fallback: ${e.message}")
        }

        // 2. Fallback: MyMemory Translation API
        try {
            val fallbackTranslated = translateViaMyMemory(text, targetLanguageCode)
            if (fallbackTranslated.isNotBlank()) {
                return@withContext Result.success(fallbackTranslated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MyMemory API fallback failed: ${e.message}")
        }

        Result.failure(Exception("Translation failed. Please check your network connection and try again."))
    }

    private fun translateViaGoogle(text: String, targetLanguageCode: String): String {
        val chunks = splitTextIntoChunks(text, 2500)
        val result = StringBuilder()

        for (chunk in chunks) {
            val urlString = "$GOOGLE_TRANSLATE_URL?client=gtx&sl=auto&tl=$targetLanguageCode&dt=t"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            }

            val postData = "q=" + URLEncoder.encode(chunk, "UTF-8")
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }

                val jsonArray = JSONArray(response)
                val sentencesArray = jsonArray.optJSONArray(0)
                if (sentencesArray != null) {
                    for (i in 0 until sentencesArray.length()) {
                        val sentenceObj = sentencesArray.optJSONArray(i) ?: continue
                        result.append(sentenceObj.optString(0, ""))
                    }
                }
            } else {
                throw Exception("HTTP $responseCode from Google Translate API")
            }
        }

        return result.toString()
    }

    private fun translateViaMyMemory(text: String, targetLanguageCode: String): String {
        val chunks = splitTextIntoChunks(text, 450)
        val result = StringBuilder()

        for (chunk in chunks) {
            val encodedQuery = URLEncoder.encode(chunk.trim(), "UTF-8")
            val url = URL("$MYMEMORY_URL?q=$encodedQuery&langpair=autodetect|$targetLanguageCode")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "DocuNovaApp/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }
                val jsonObj = JSONObject(response)
                val responseData = jsonObj.optJSONObject("responseData")
                val translated = responseData?.optString("translatedText") ?: ""
                result.append(translated).append("\n")
            } else {
                throw Exception("HTTP ${connection.responseCode} from MyMemory API")
            }
        }
        return result.toString().trim()
    }

    private fun splitTextIntoChunks(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n")
        var currentChunk = StringBuilder()

        for (para in paragraphs) {
            if (currentChunk.length + para.length + 1 > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
                if (para.length > maxChunkSize) {
                    var start = 0
                    while (start < para.length) {
                        val end = minOf(start + maxChunkSize, para.length)
                        chunks.add(para.substring(start, end))
                        start = end
                    }
                } else {
                    currentChunk.append(para).append("\n")
                }
            } else {
                currentChunk.append(para).append("\n")
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }
}

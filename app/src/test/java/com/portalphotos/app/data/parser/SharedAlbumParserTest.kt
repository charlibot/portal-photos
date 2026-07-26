package com.portalphotos.app.data.parser

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class SharedAlbumParserTest {

    @Test
    fun testInspectVideoQualityFormats() = runBlocking {
        val testUrl = "https://photos.app.goo.gl/EXAMPLE_ALBUM_LINK"
        val client = OkHttpClient.Builder().followRedirects(true).build()

        val pageReq = Request.Builder()
            .url(testUrl)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10)")
            .build()

        try {
            val html = client.newCall(pageReq).execute().use { it.body?.string() ?: "" }
            val regex = Regex("""(https://lh3\.googleusercontent\.com/pw/[a-zA-Z0-9\-_]+)""")
            val baseUrls = regex.findAll(html).map { it.value }.distinct().toList()
            val formats = listOf("m18", "m22", "m37", "dv")

            println("================ GOOGLE VIDEO QUALITY FORMAT INSPECTION ================")
            baseUrls.forEachIndexed { i, baseUrl ->
                println("\n--- Item [$i] ---")
                formats.forEach { fmt ->
                    val streamUrl = "$baseUrl=$fmt"
                    val req = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .head()
                        .build()

                    try {
                        client.newCall(req).execute().use { res ->
                            val code = res.code
                            val length = res.header("Content-Length")?.toLongOrNull() ?: -1L
                            val type = res.header("Content-Type") ?: ""
                            println("  Format =$fmt -> HTTP $code | Length: $length B | Type: $type")
                        }
                    } catch (e: Exception) {
                        println("  Format =$fmt -> Exception: ${e.message}")
                    }
                }
            }
            println("=========================================================================")
        } catch (e: Exception) {
            println("Test URL is dummy placeholder; network request skipped as expected.")
        }
    }
}

package com.portalphotos.app.data.server

import com.portalphotos.app.data.repository.AlbumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LocalWebServer(
    private val repository: AlbumRepository,
    private val port: Int = 12345
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch { handleClientSocket(socket) }
                }
            } catch (e: Exception) {
                // Server stopped or socket error
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private suspend fun handleClientSocket(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return socket.close()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()

            val method = parts[0].uppercase()
            val path = parts[1]

            // Read HTTP headers
            var contentLength = 0
            var line: String?
            while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Read request body if present
            var body = ""
            if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = input.read(charBuffer, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                body = String(charBuffer, 0, read)
            }

            // Dispatch route handling
            when {
                method == "GET" && path == "/" -> {
                    val html = getWebPortalHtml()
                    sendHttpResponse(output, 200, "text/html; charset=utf-8", html)
                }
                method == "GET" && path.startsWith("/api/albums") -> {
                    val albums = repository.allAlbums.first()
                    val json = buildString {
                        append("[")
                        albums.forEachIndexed { i, album ->
                            append("""{"id":"${album.id}","title":"${escapeJson(album.title)}","url":"${escapeJson(album.shareUrl)}","count":${album.itemCount},"selected":${album.isSelected}}""")
                            if (i < albums.size - 1) append(",")
                        }
                        append("]")
                    }
                    sendHttpResponse(output, 200, "application/json", json)
                }
                method == "POST" && path == "/api/albums/add" -> {
                    val url = parseFormParam(body, "url") ?: parseJsonParam(body, "url")
                    if (url.isNullOrBlank()) {
                        sendHttpResponse(output, 400, "application/json", """{"success":false,"error":"Missing URL"}""")
                    } else {
                        // Non-blocking async addition: Acknowledge immediately & process in background
                        repository.addAlbumUrlAsync(url)
                        sendHttpResponse(output, 200, "application/json", """{"success":true,"message":"Album added! Fetching photos in background..."}""")
                    }
                }
                method == "POST" && path == "/api/albums/delete" -> {
                    val albumId = parseFormParam(body, "id") ?: parseJsonParam(body, "id")
                    if (albumId.isNullOrBlank()) {
                        sendHttpResponse(output, 400, "application/json", """{"success":false,"error":"Missing album ID"}""")
                    } else {
                        repository.deleteAlbum(albumId)
                        sendHttpResponse(output, 200, "application/json", """{"success":true}""")
                    }
                }
                else -> {
                    sendHttpResponse(output, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            // Socket error
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 400) "Bad Request" else "Error"
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun parseFormParam(body: String, paramName: String): String? {
        val pairs = body.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2 && parts[0].trim() == paramName) {
                return URLDecoder.decode(parts[1].trim(), "UTF-8")
            }
        }
        return null
    }

    private fun parseJsonParam(body: String, paramName: String): String? {
        val regex = Regex(""""$paramName"\s*:\s*"([^"]+)"""")
        return regex.find(body)?.groupValues?.get(1)
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }

    private fun getWebPortalHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Portal Photos - Web Manager</title>
                <style>
                    :root { --bg: #0f172a; --card: #1e293b; --primary: #38bdf8; --text: #f8fafc; --text-sub: #94a3b8; }
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px; display: flex; justify-content: center; }
                    .container { max-width: 600px; width: 100%; }
                    h1 { font-size: 24px; font-weight: 700; color: var(--primary); margin-bottom: 4px; }
                    p.sub { color: var(--text-sub); font-size: 14px; margin-top: 0; margin-bottom: 24px; }
                    .card { background: var(--card); border-radius: 16px; padding: 20px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); margin-bottom: 24px; }
                    label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 8px; }
                    .input-group { display: flex; gap: 8px; }
                    input[type="text"] { flex: 1; padding: 12px 16px; border-radius: 10px; border: 1px solid #334155; background: #0f172a; color: #fff; font-size: 14px; outline: none; }
                    input[type="text"]:focus { border-color: var(--primary); }
                    button { background: var(--primary); color: #0f172a; font-weight: 700; border: none; padding: 12px 20px; border-radius: 10px; cursor: pointer; transition: opacity 0.2s; }
                    button:hover { opacity: 0.9; }
                    .album-list { display: flex; flex-direction: column; gap: 12px; margin-top: 12px; }
                    .album-item { display: flex; align-items: center; justify-content: space-between; background: #0f172a; padding: 14px 16px; border-radius: 12px; }
                    .album-title { font-weight: 600; font-size: 15px; }
                    .album-count { font-size: 12px; color: var(--text-sub); margin-top: 2px; }
                    .btn-del { background: #ef4444; color: #fff; padding: 6px 12px; font-size: 12px; border-radius: 6px; }
                    .status { font-size: 13px; margin-top: 8px; font-weight: 500; }
                    .syncing-badge { color: #f59e0b; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Portal Photos Manager</h1>
                    <p class="sub">Add & manage Google Photos albums on your Meta Portal</p>
                    
                    <div class="card">
                        <label for="url">Add Google Photos Shared Album</label>
                        <div class="input-group">
                            <input type="text" id="url" placeholder="https://photos.app.goo.gl/..." />
                            <button onclick="addAlbum()">Add Link</button>
                        </div>
                        <div id="status" class="status"></div>
                    </div>

                    <div class="card">
                        <label>Saved Albums on Portal</label>
                        <div id="albums" class="album-list">Loading...</div>
                    </div>
                </div>

                <script>
                    let pollInterval = null;

                    async function loadAlbums() {
                        try {
                            const res = await fetch('/api/albums');
                            const data = await res.json();
                            const container = document.getElementById('albums');
                            if (data.length === 0) {
                                container.innerHTML = '<div style="color:var(--text-sub); font-size:14px;">No albums added yet. Paste a link above!</div>';
                                return;
                            }
                            let hasSyncing = false;
                            container.innerHTML = data.map(a => {
                                const isSyncing = a.count === 0 || a.title === 'Fetching album...';
                                if (isSyncing) hasSyncing = true;
                                const countText = isSyncing 
                                    ? '<span class="syncing-badge">⏳ Syncing in background...</span>' 
                                    : a.count + ' items';

                                return `
                                    <div class="album-item">
                                        <div>
                                            <div class="album-title">${'$'}{a.title}</div>
                                            <div class="album-count">${'$'}{countText}</div>
                                        </div>
                                        <button class="btn-del" onclick="deleteAlbum('${'$'}{a.id}')">Delete</button>
                                    </div>
                                `;
                            }).join('');

                            if (hasSyncing && !pollInterval) {
                                pollInterval = setInterval(loadAlbums, 2000);
                            } else if (!hasSyncing && pollInterval) {
                                clearInterval(pollInterval);
                                pollInterval = null;
                            }
                        } catch(e) {
                            document.getElementById('albums').innerText = 'Failed to load albums';
                        }
                    }

                    async function addAlbum() {
                        const urlInput = document.getElementById('url');
                        const url = urlInput.value.trim();
                        const status = document.getElementById('status');
                        if (!url) return;
                        status.style.color = '#38bdf8';
                        status.innerText = '⚡ Added! Syncing photos in background...';
                        try {
                            const res = await fetch('/api/albums/add', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'url=' + encodeURIComponent(url)
                            });
                            const data = await res.json();
                            if (data.success) {
                                status.style.color = '#22c55e';
                                status.innerText = '✓ Album added! Syncing in background...';
                                urlInput.value = '';
                                loadAlbums();
                            } else {
                                status.style.color = '#ef4444';
                                status.innerText = data.error || 'Failed to add album';
                            }
                        } catch(e) {
                            status.style.color = '#ef4444';
                            status.innerText = 'Error connecting to Portal server';
                        }
                    }

                    async function deleteAlbum(id) {
                        if (!confirm('Delete this album from Portal?')) return;
                        await fetch('/api/albums/delete', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: 'id=' + encodeURIComponent(id)
                        });
                        loadAlbums();
                    }

                    loadAlbums();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

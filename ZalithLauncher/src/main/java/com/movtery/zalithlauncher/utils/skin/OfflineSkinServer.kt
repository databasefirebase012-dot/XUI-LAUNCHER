package com.movtery.zalithlauncher.utils.skin

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.movtery.zalithlauncher.utils.path.LibPath
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small loopback Yggdrasil session server for custom skins on local/offline accounts.
 * It is intentionally bound to 127.0.0.1 only and serves one player's profile.
 */
object OfflineSkinServer {
    @Volatile private var activeServer: ServerSocket? = null
    @Volatile private var activeThread: Thread? = null

    @JvmStatic
    @Synchronized
    fun start(context: Context, account: MinecraftAccount): String? {
        if (!"LOCAL".equals(account.accountType, true)) return null

        val profileId = account.profileId?.replace("-", "")?.lowercase(Locale.ROOT) ?: return null
        if (profileId.length != 32 || profileId.all { it == '0' }) return null

        val skinFile = File(PathManager.DIR_USER_SKIN, "$profileId.png")
        if (!skinFile.isFile || skinFile.length() <= 0L) return null
        val skinBytes = skinFile.readBytes()
        val bitmap = BitmapFactory.decodeByteArray(skinBytes, 0, skinBytes.size) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val isValid = width == 64 && (height == 64 || height == 32)
        val isSlim = if (width == 64 && height == 64) {
            isTransparent(bitmap, 50, 51, 16, 19) &&
                isTransparent(bitmap, 54, 55, 20, 31) &&
                isTransparent(bitmap, 42, 43, 48, 51) &&
                isTransparent(bitmap, 46, 47, 52, 63)
        } else false
        bitmap.recycle()
        if (!isValid) return null

        ensureAuthlibInjector(context)

        runCatching { activeServer?.close() }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        activeServer = server
        val skinHash = MessageDigest.getInstance("SHA-256").digest(skinBytes)
            .joinToString("") { "%02x".format(it) }
        val running = AtomicBoolean(true)
        val playerName = account.username
        val playerUuid = profileId
        val thread = Thread({
            while (running.get() && !server.isClosed) {
                try {
                    val socket = server.accept()
                    Thread({
                        try {
                            socket.use { handleRequest(it, playerName, playerUuid, skinBytes, skinHash, isSlim, keyPair) }
                        } catch (_: Exception) {
                        }
                    }, "XuiOfflineSkinRequest").apply { isDaemon = true; start() }
                } catch (_: Exception) {
                    if (server.isClosed) break
                }
            }
        }, "XuiOfflineSkinServer").apply { isDaemon = true; start() }
        activeThread = thread
        return "http://127.0.0.1:${server.localPort}/"
    }

    private fun ensureAuthlibInjector(context: Context) {
        val jar = LibPath.AUTHLIB_INJECTOR
        if (jar.isFile && jar.length() > 0L) return
        jar.parentFile?.mkdirs()
        context.assets.open("components/other_login/authlib-injector.jar").use { input ->
            jar.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun isTransparent(bitmap: android.graphics.Bitmap, left: Int, right: Int, top: Int, bottom: Int): Boolean {
        for (y in top..bottom) for (x in left..right) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) != 0) return false
        }
        return true
    }

    private data class HttpRequest(val method: String, val path: String, val body: ByteArray)

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) return if (out.size() == 0) null else out.toString("ISO-8859-1")
            if (value == 10) break
            if (value != 13) out.write(value)
            if (out.size() > 16384) throw IllegalStateException("HTTP header too large")
        }
        return out.toString("ISO-8859-1")
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val split = line.split(":", limit = 2)
            if (split.size == 2 && split[0].equals("Content-Length", true)) {
                contentLength = split[1].trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength < 0 || contentLength > 1024 * 1024) return null
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val count = input.read(body, offset, contentLength - offset)
            if (count <= 0) break
            offset += count
        }
        return HttpRequest(parts[0], parts[1], body.copyOf(offset))
    }

    private fun handleRequest(
        socket: java.net.Socket,
        name: String,
        uuid: String,
        skin: ByteArray,
        hash: String,
        slim: Boolean,
        keyPair: KeyPair
    ) {
        socket.soTimeout = 5000
        val request = readRequest(socket.getInputStream()) ?: return
        val path = request.path.substringBefore("?")
        val query = request.path.substringAfter("?", "")
        when {
            request.method == "GET" && path == "/" -> respondJson(socket, root(keyPair))
            request.method == "GET" && path == "/status" -> respondJson(socket, JSONObject().put("user.count", 1).put("token.count", 0))
            request.method == "POST" && path == "/api/profiles/minecraft" -> {
                val requested = JSONArray(String(request.body, StandardCharsets.UTF_8))
                val result = JSONArray()
                for (i in 0 until requested.length()) {
                    if (requested.optString(i).equals(name, true)) {
                        result.put(JSONObject().put("id", uuid).put("name", name))
                    }
                }
                respondJson(socket, result)
            }
            request.method == "POST" && path == "/sessionserver/session/minecraft/join" ->
                respond(socket, 204, "No Content", ByteArray(0), "text/plain")
            request.method == "GET" && path == "/sessionserver/session/minecraft/hasJoined" -> {
                val username = query.split("&").mapNotNull {
                    val pair = it.split("=", limit = 2)
                    if (pair.size == 2 && pair[0] == "username") URLDecoder.decode(pair[1], "UTF-8") else null
                }.firstOrNull()
                if (username.equals(name, true)) respondJson(socket, profile(name, uuid, hash, skin, slim, keyPair))
                else respondJson(socket, JSONObject())
            }
            request.method == "GET" && path.startsWith("/sessionserver/session/minecraft/profile/") -> {
                val requestedUuid = path.substringAfterLast("/").replace("-", "")
                if (requestedUuid.equals(uuid, true)) respondJson(socket, profile(name, uuid, hash, skin, slim, keyPair))
                else respond(socket, 404, "Not Found", ByteArray(0), "text/plain")
            }
            request.method == "GET" && path == "/textures/$hash" ->
                respond(socket, 200, "OK", skin, "image/png", "Cache-Control: public, max-age=3600\r\n")
            else -> respond(socket, 404, "Not Found", ByteArray(0), "text/plain")
        }
    }

    private fun root(keyPair: KeyPair): JSONObject {
        val encodedKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val pem = "-----BEGIN PUBLIC KEY-----\n$encodedKey\n-----END PUBLIC KEY-----"
        return JSONObject()
            .put("skinDomains", JSONArray().put("127.0.0.1").put("localhost"))
            .put("meta", JSONObject()
                .put("serverName", "XUI_Offline")
                .put("implementationName", "XUI Launcher")
                .put("implementationVersion", "1.0")
                .put("feature.non_email_login", true))
            .put("signaturePublickey", pem)
    }

    private fun profile(
        name: String,
        uuid: String,
        hash: String,
        skin: ByteArray,
        slim: Boolean,
        keyPair: KeyPair
    ): JSONObject {
        val textures = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("profileId", uuid)
            .put("profileName", name)
        val textureEntry = JSONObject().put("url", "http://127.0.0.1:${activeServer?.localPort}/textures/$hash")
        if (slim) textureEntry.put("metadata", JSONObject().put("model", "slim"))
        textures.put("textures", JSONObject().put("SKIN", textureEntry))
        val value = Base64.encodeToString(textures.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keyPair.private)
        signer.update(value.toByteArray(StandardCharsets.UTF_8))
        val signature = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
        return JSONObject()
            .put("id", uuid)
            .put("name", name)
            .put("properties", JSONArray().put(JSONObject()
                .put("name", "textures")
                .put("value", value)
                .put("signature", signature)))
    }

    private fun respondJson(socket: java.net.Socket, json: Any) {
        respond(socket, 200, "OK", json.toString().toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8")
    }

    private fun respond(
        socket: java.net.Socket,
        code: Int,
        reason: String,
        body: ByteArray,
        contentType: String,
        extraHeaders: String = ""
    ) {
        val output = socket.getOutputStream()
        val header = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            extraHeaders + "\r\n"
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }
}

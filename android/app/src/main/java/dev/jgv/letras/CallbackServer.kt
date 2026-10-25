package dev.jgv.letras

import android.net.Uri
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Servidor HTTP de una sola página en 127.0.0.1: recibe la redirección de Spotify tras el login.
 * Solo escucha en loopback, así que nada de la red de la casa puede hablarle.
 */
class CallbackServer(
    private val port: Int,
    private val onCallback: (code: String?, state: String?, error: String?) -> Unit,
) {
    @Volatile
    private var socket: ServerSocket? = null

    val isRunning get() = socket?.isClosed == false

    fun start() {
        if (isRunning) return
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        }
        socket = server
        thread(name = "spotify-callback", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use(::handle)
                } catch (_: IOException) {
                    // accept() falla al cerrar el socket desde stop(): fin normal.
                }
            }
        }
    }

    fun stop() {
        socket?.close()
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val requestLine = client.getInputStream().bufferedReader().readLine() ?: return
        // "GET /callback?code=...&state=... HTTP/1.1"
        val path = requestLine.split(' ').getOrNull(1) ?: return
        val uri = Uri.parse("http://127.0.0.1$path")
        val isCallback = uri.path == "/callback"
        val body = if (isCallback) PAGE_OK else "No encontrado"
        val status = if (isCallback) "200 OK" else "404 Not Found"
        val bytes = body.toByteArray()
        client.getOutputStream().apply {
            write(
                ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
            )
            write(bytes)
            flush()
        }
        if (isCallback) {
            onCallback(uri.getQueryParameter("code"), uri.getQueryParameter("state"), uri.getQueryParameter("error"))
        }
    }

    private companion object {
        const val PAGE_OK = "<!doctype html><meta charset=utf-8>" +
            "<meta name=viewport content='width=device-width'>" +
            "<body style='background:#0E0E12;color:#F5F5F7;font:20px sans-serif;text-align:center;padding-top:30vh'>" +
            "Listo. Ya puedes volver a <b>Letras</b>.</body>"
    }
}

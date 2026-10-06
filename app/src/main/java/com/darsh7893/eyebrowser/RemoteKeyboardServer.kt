package com.darsh7893.eyebrowser

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.Collections

/**
 * Serves the phone keyboard page over plain HTTP and relays keystrokes
 * back to the TV through a WebSocket. Everything stays on the local
 * Wi-Fi network — no internet or accounts involved.
 */
class RemoteKeyboardServer(port: Int, private val listener: Listener) : NanoWSD(port) {

    interface Listener {
        fun onRemoteText(text: String)
        fun onRemoteEnter()
    }

    private val sockets = Collections.synchronizedSet(mutableSetOf<WebSocket>())

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = KBWebSocket(handshake)

    override fun serve(session: IHTTPSession): Response {
        // Let NanoWSD perform the WebSocket handshake; anything else gets the page.
        return if ("websocket".equals(session.headers["upgrade"], ignoreCase = true)) {
            super.serve(session)
        } else {
            newFixedLengthResponse(Response.Status.OK, "text/html", KeyboardPage.HTML)
        }
    }

    private inner class KBWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {

        override fun onOpen() {
            sockets.add(this)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) {
            sockets.remove(this)
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                val json = JSONObject(message.textPayload)
                when (json.optString("t")) {
                    "text" -> listener.onRemoteText(json.optString("v"))
                    "enter" -> listener.onRemoteEnter()
                }
                // Unknown message types are ignored.
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring malformed keyboard frame", e)
            }
        }

        override fun onPong(pong: WebSocketFrame) {
            // Not used.
        }

        override fun onException(e: IOException) {
            Log.w(TAG, "Keyboard socket error", e)
        }
    }

    companion object {
        private const val TAG = "RemoteKeyboard"
    }
}

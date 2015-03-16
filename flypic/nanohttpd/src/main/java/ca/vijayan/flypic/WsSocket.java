package ca.vijayan.flypic;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONStringer;

import java.io.IOException;
import java.net.SocketException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.WebSocket;
import fi.iki.elonen.WebSocketFrame;

public class WsSocket extends WebSocket {

    private static final int SOCKET_TIMEOUT = 60 * 1000;

    public WsSocket(NanoHTTPD.IHTTPSession handshake) {
        super(handshake);
        try {
            handshake.getSocket().setSoTimeout(SOCKET_TIMEOUT);
        } catch(SocketException exc) {
            onException(exc);
        }

        Log.d("PicView.WsSocket", "Opening new socket");
    }

    @Override
    protected void onOpen() {
        JSONStringer stringer = new JSONStringer();
        try {
            stringer.object();
            stringer.key("server");
            stringer.value(1);
            stringer.key("zoom");
            stringer.value("ZOOMZOOM");
            stringer.endObject();
        } catch(JSONException exc) {
        }


        try {
            send(stringer.toString());
        } catch(IOException exc) {
            onException(exc);
        }
    }

    @Override
    protected void onMessage(WebSocketFrame message) {
        Log.d("PicVIew.WsSocket", "Got message " + message);
    }

    @Override
    protected void onPong(WebSocketFrame pong) {
    }

    @Override
    protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
        Log.d("FlyPic.WsSocket",
                "C [" + (initiatedByRemote ? "Remote" : "Self") + "] " +
                (code != null ? code : "UnknownCloseCode[" + code + "]") +
                (reason != null && !reason.isEmpty() ? ": " + reason : ""));
    }

    @Override
    protected void onException(IOException e) {
        e.printStackTrace();
    }

}

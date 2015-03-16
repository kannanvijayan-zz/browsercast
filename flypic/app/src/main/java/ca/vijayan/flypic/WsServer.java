package ca.vijayan.flypic;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONStringer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoWebSocketServer;
import fi.iki.elonen.WebSocket;

/**
 * Created by kannanvijayan on 2015-02-17.
 */
public class WsServer extends NanoWebSocketServer {
    Context mCx;
    Thread mThread;
    List<String> mSendQueue;
    WsSocket mSocket;
    boolean mStop;
    PicView mPicView;

    public WsServer(Context cx, String host, int port, PicView picView) {
        super(host, port);
        mCx = cx;
        mThread = createThread();
        mSendQueue = new ArrayList<String>();
        mStop = false;
        mPicView = picView;
    }

    @Override
    public WebSocket openWebSocket(IHTTPSession handshake) {
        mSocket = new WsSocket(handshake);
        return mSocket;
    }

    public void startInThread() {
        mThread.start();
    }

    Thread createThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                runInThread();
            }
        });
    }

    private void runInThread() {
        try {
            start();
        } catch (IOException exc) {
            Log.e("PicView.WsServer", "WsServer server threw IO error.");
        }

        synchronized (this) {
            for (;;) {
                try {
                    this.wait(1000);
                } catch (InterruptedException exc) {
                }

                if (mStop) {
                    stop();
                    break;
                }
            }
        }
    }

    void move(int delta) {
        if (mSocket == null)
            return;
        JSONStringer json = new JSONStringer();
        try {
            json.object();
            json.key("type");
            json.value("move");
            json.key("delta");
            json.value(delta);
            json.endObject();
            mSocket.send(json.toString());
        } catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }
}

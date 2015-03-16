package ca.vijayan.flypic;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONStringer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;


public class HttpServer extends NanoHTTPD {
    Context mCx;
    Thread mThread;
    boolean mStop;
    PicView mPicView;
    ImageStore mImageStore;

    public HttpServer(Context cx, String host, int port, PicView picView, ImageStore imageStore) {
        super(host, port);
        mCx = cx;
        mThread = createThread();
        mStop = false;
        mPicView = picView;
        mImageStore = imageStore;
    }

    @Override
    public Response serve(final IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        Log.i("PicViewHTTPServer", method + " '" + uri + "' ");

        if (uri.equals("/")) {
            return serveResource(session, R.raw.index, "text/html");
        }

        if (uri.equals("/info")) {
            return serveInfo(session);
        }

        if (uri.equals("/jquery.js")) {
            return serveResource(session, R.raw.jquery, "application/javascript");
        }

        if (uri.equals("/picview.js")) {
            return serveResource(session, R.raw.picview, "application/javascript");
        }

        if (uri.equals("/pics")) {
            return listPics(session);
        }

        if (uri.startsWith("/pic/")) {
            return showPic(session, uri.substring(5));
        }

        return serveDefault(session);
    }

    private Response serveResource(IHTTPSession session, int resourceId, String mimeType) {
        InputStream is = mCx.getResources().openRawResource(resourceId);
        return new Response(Response.Status.OK, mimeType, is);
    }

    private Response serveInfo(IHTTPSession session) {
        JSONStringer stringer = new JSONStringer();
        try {
            stringer.object();
            stringer.key("wsd");
            stringer.value(mPicView.getWsdUrl());
            stringer.endObject();
        } catch (JSONException exc) {
            // Do nothing.
        }
        return new Response(Response.Status.OK, "text/json", stringer.toString());
    }

    private Response listPics(IHTTPSession session) {
        JSONStringer stringer = new JSONStringer();
        try {
            stringer.array();
            for (String name : mImageStore.imageNames()) {
                stringer.value(name);
            }
            stringer.endArray();
        } catch (JSONException exc) {
            // Do nothing.
        }
        return new Response(Response.Status.OK, "text/json", stringer.toString());
    }

    private Response showPic(IHTTPSession session, String path) {
        Log.d("FlyPic.HttpServer", "showPic called with '" + path + "'");
        int imageNo = mImageStore.imageNumber(path);
        Log.d("FlyPic.HttpServer", "got imageNumber '" + imageNo + "'");

        try {
            InputStream picStream = new FileInputStream(mImageStore.imageFile(imageNo));
            Log.d("FlyPic.HttpServer", "Opened pic");
            return new Response(Response.Status.OK, "image/jpeg", picStream);
        } catch(IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    private Response serveDefault(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<body>");
        sb.append("    <h1>Request Info</h1>");
        sb.append("    <h2>Method = " + method + "</h2>");
        sb.append("    <h2>URI = " + uri + "</h2>");
        sb.append("    <h2>Params</h2>");
        for (Map.Entry<String, String> ent : session.getParms().entrySet()) {
            sb.append("        <h3>" + ent.getKey() + " => " + ent.getValue() + "</h3>");
        }
        sb.append("    <h2>Pictures</h2>");
        for (String picName : mImageStore.imageNames()) {
            sb.append("        <h3>" + picName + "</h3>");
        }
        sb.append("</body>");
        sb.append("</html>");

        return new NanoHTTPD.Response(Response.Status.NOT_FOUND, "text/html", sb.toString());
    }

    public void startInThread() {
        mThread.start();
    }

    private Thread createThread() {
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
            Log.e("PicView", "Http server threw IO error.");
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

}

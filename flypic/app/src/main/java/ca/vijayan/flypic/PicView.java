package ca.vijayan.flypic;

import fi.iki.elonen.NanoHTTPD;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class PicView extends Activity {
    private String mIpAddr;
    private int mWsPort;
    private WsServer mWsd;
    private int mHttpPort;
    private HttpServer mHttpd;

    private ImageStore mImageStore;

    private FrameLayout mFrameLayout;
    private ImageView mImageViewLeft;
    private ImageView mImageView;
    private ImageView mImageViewRight;

    private int mViewHeight;
    private int mViewWidth;
    private GestureDetectorCompat mGestureDetector;

    private boolean mSawFling;
    private float mFlingVx;
    private float mFlingVy;

    private boolean mTouchDown;
    private float mTouchX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pic_view);

        mImageStore = new ImageStore();

        mIpAddr = getIpAddress();
        initializeWsServer();
        initializeHttpServer();

        Intent intent = getIntent();
        String host = intent.getStringExtra("host");
        int port = intent.getIntExtra("port", -1);
        if (port == -1) {
            Log.e("PicView", "Port extra parameter not given to activity!");
            return;
        }
        registerWebcaster(host, port);

        initializeGestureDetector();

        mFrameLayout = (FrameLayout) findViewById(R.id.picViewLayout);
        mImageViewLeft = new ImageView(this);
        mImageView = new ImageView(this);
        mImageViewRight = new ImageView(this);
        mFrameLayout.post(new Runnable() {
            @Override
            public void run() {
                initializeView();
            }
        });
    }

    private void initializeGestureDetector() {
        mSawFling = false;
        mFlingVx = 0.0f;
        mFlingVy = 0.0f;
        mTouchDown = false;
        mTouchX = 0;
        mGestureDetector = new GestureDetectorCompat(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {

            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                mSawFling = true;
                mFlingVx = velocityX;
                mFlingVy = velocityY;
                //Log.d("GESTURE", "onFling: vx=" + velocityX + " vy=" + velocityY);
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mSawFling = false;
        this.mGestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            Log.d("Gesture", "Got touch at " + event.getX(0));
            mTouchDown = true;
            mTouchX = event.getX(0);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            Log.d("Gesture", "Got move at " + event.getX(0));
            float currentX = event.getX(0);
            float deltaX = currentX - mTouchX;
            Log.d("Gesture", "Image left=" + mImageView.getLeft());
            Log.d("Gesture", "currentX=" + currentX + " Deltax=" + deltaX);
            move((int)deltaX);
            mTouchX = currentX;
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            Log.d("Gesture", "Got up at " + event.getX(0));
            if (mSawFling) {
                Log.d("Gesture", "SawFling vx=" + mFlingVx + " vy=" + mFlingVy);
            }
            return true;
        }
        return true;
    }

    void move(int delta) {
        mWsd.move(delta);
        mImageViewLeft.setLeft(mImageViewLeft.getLeft() + delta);
        mImageView.setLeft(mImageView.getLeft() + delta);
        mImageViewRight.setLeft(mImageViewRight.getLeft() + delta);
    }

    private void initializeView() {
        mViewHeight = mFrameLayout.getMeasuredHeight();
        mViewWidth = mFrameLayout.getMeasuredWidth();
        Log.d("PicView", "Layout w=" + mViewWidth + " h=" + mViewHeight);

        configureImageView(mImageView);
        configureImageView(mImageViewLeft);
        configureImageView(mImageViewRight);

        if (mImageStore.numImages() > 0) {
            int curImage = mImageStore.currentImage();
            mImageView.setImageBitmap(mImageStore.imageBitmap(curImage));
            mImageViewLeft.setImageBitmap(curImage > 0 ?
                    mImageStore.imageBitmap(curImage - 1) : null);
            mImageViewRight.setImageBitmap(curImage < mImageStore.numImages() - 1 ?
                    mImageStore.imageBitmap(curImage + 1) : null);
        }

        mFrameLayout.addView(mImageViewLeft);
        mFrameLayout.addView(mImageView);
        mFrameLayout.addView(mImageViewRight);

        mFrameLayout.post(new Runnable() {
            @Override
            public void run() {
                mImageViewLeft.setLeft(-mViewWidth);
                mImageViewRight.setLeft(mViewWidth);
            }
        });
    }

    void configureImageView(ImageView view) {
        view.setLayoutParams(
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setAdjustViewBounds(true);
    }

    private void initializeHttpServer() {
        mHttpPort = 8080;
        // Start httpd server.
        mHttpd = new HttpServer(this, mIpAddr, mHttpPort, this, mImageStore);
        mHttpd.startInThread();
    }

    private void initializeWsServer() {
        mWsPort = 8081;
        mWsd = new WsServer(this, mIpAddr, mWsPort, this);
        mWsd.startInThread();
    }

    private String getIpAddress() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddr = wm.getConnectionInfo().getIpAddress();
        return String.format("%d.%d.%d.%d",
                (ipAddr & 0xff), (ipAddr >> 8 & 0xff),
                (ipAddr >> 16 & 0xff), (ipAddr >> 24 & 0xff));
    }

    private String getRegisterUrl(String host, int port) {
        String hostPort = host + ":" + port;
        String addr = "yahoo.com";
        String path = "browsercast/register";
        return "http://" + hostPort + "/" + path + "?addr=" + mIpAddr + "&" + "port=" + mHttpPort;
    }

    public String getWsdUrl() {
        return "ws://" + mIpAddr + ":" + mWsPort;
    }

    private void registerWebcaster(String host, int port) {
        String registerUrl = getRegisterUrl(host, port);
        Log.d("PicView", "Using registerUrl " + registerUrl);
        final HttpGet getReq = new HttpGet(registerUrl);
        final HttpClient client = new DefaultHttpClient();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpResponse response = null;
                try {
                    client.execute(getReq);
                } catch(ClientProtocolException exc) {
                    Log.e("PicView", exc.toString());
                } catch(IOException exc) {
                    Log.e("PicView", exc.toString());
                }
                Log.i("PicView", "Registered with pic handler.");
            }
        }).start();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

}

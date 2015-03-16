package ca.vijayan.flypic;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to do service discovery and call callback with information about it.
 */
public class BrowserCastServiceDiscovery {
    private Callback mCallback;
    private Map<String, NsdServiceInfo> mKnownServices;
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;

    public interface Callback {
        public void serviceFound(String info);
        public void serviceLost(String info);
        public void failure(String reason);
        public void error(String message);
    }

    public BrowserCastServiceDiscovery(Context cx, Callback cb) {
        mCallback = cb;
        mKnownServices = new HashMap<String, NsdServiceInfo>();
        mNsdManager = (NsdManager) cx.getSystemService(Context.NSD_SERVICE);
        initializeDiscoveryListener();
        initializeResolveListener();
    }

    public void startDiscovery() {
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopDiscovery() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    public NsdServiceInfo getServiceForName(String name) {
        return mKnownServices.get(name);
    }

    private void initializeDiscoveryListener() {
        final BrowserCastServiceDiscovery self = this;

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                log("Start discovery failed for '" + serviceType + "' - " + errorCode);
                mCallback.error("Failed to start service discovery.");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                log("Start discovery failed for '" + serviceType + "' - " + errorCode);
                mCallback.error("Failed to stop service discovery.");
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                log("Discovery started for '" + serviceType + "'");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                log("Discovery started for '" + serviceType + "'");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                log("Found service " + ShortServiceName(serviceInfo));
                mNsdManager.resolveService(serviceInfo, mResolveListener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                log("Lost service " + ShortServiceName(serviceInfo));
                removeKnownService(serviceInfo);
            }
        };
    }

    private void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                log("Resolve failed for service " + ShortServiceName(serviceInfo));
                mCallback.failure("Failed to resolve service.");
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                log("Resolved service " + serviceInfo);
                addKnownService(serviceInfo);
            }
        };
    }

    private void addKnownService(NsdServiceInfo info) {
        // If key already exists in service info, and host/port matches, leave alone.
        log("addKnownService " + info);
        String name = info.getServiceName();
        boolean alreadyContained = mKnownServices.containsKey(name);
        mKnownServices.put(name, info);
        if (alreadyContained) {
            return;
        }

        mCallback.serviceFound(info.getServiceName());
    }

    private void removeKnownService(NsdServiceInfo info) {
        log("removeKnownService " + info);
        String name = info.getServiceName();
        // If key already exists in service info, and host/port matches, leave alone.
        if (!mKnownServices.containsKey(name)) {
            return;
        }

        mKnownServices.remove(info.getServiceName());
        mCallback.serviceLost(info.getServiceName());
    }

    public static boolean SameService(NsdServiceInfo a, NsdServiceInfo b) {
        return a.getServiceName().equals(b.getServiceName());
    }

    public static String ShortServiceName(NsdServiceInfo info) {
        return "(" + info.getServiceName() + "/" + info.getServiceType() + ")";
    }
    public static String LongServiceName(NsdServiceInfo info) {
        return "(" + info.getServiceName() + "/" + info.getServiceType() + " - " +
                info.getHost() + ":" + info.getPort() + ")";
    }

    static final String SERVICE_TYPE = "_browsercast._tcp";
    static final String LOGTAG = "BrowserCastServiceDiscovery";
    static void log(String message) {
        Log.i(LOGTAG, message);
    }
}

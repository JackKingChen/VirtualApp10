package com.nd.hardcasting.castlib.cast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

//import android.net.wifi.p2p.WifiP2pWfdInfo;
import com.nd.hardcasting.castlib.cast.RarpImpl;

public class WfdSinkController {
    private static final String TAG = "WfdSinkController";
    private static final boolean DEBUG = true;

    private static final int DEFAULT_CONTROL_PORT = 7236;
    private static final int MAX_THROUGHPUT = 50;

    private final Context mContext;

    private final WifiP2pManager mWifiP2pManager;
    private final Channel mWifiP2pChannel;

    public CastManager mCastManager;
    
    private boolean mWifiP2pEnabled = false;
    private boolean mWfdEnabled = false;
    private boolean mWfdEnabling = false;
    private NetworkInfo mNetworkInfo;
    private WifiP2pDeviceList mPeers;

    private Timer mArpTableObservationTimer;
    //private int mArpRetryCount = 0;
    private final int MAX_ARP_RETRY_COUNT = 60;
    private int mP2pControlPort = -1;
    private String mP2pInterfaceName;
    private String mlastIp="";
    private boolean isStart = false;
    private String mMiracastName="";
    public WfdSinkController(CastManager castManager,Context context) {
        this.mCastManager = castManager;
        mContext = context;

        mWifiP2pManager = (WifiP2pManager)context.getSystemService(Context.WIFI_P2P_SERVICE);
        mWifiP2pChannel = mWifiP2pManager.initialize(context, mContext.getMainLooper(), null);
    }
    protected void finalize(){
        Log.d(TAG, "finalize isStart: " + isStart);
        if(isStart){
            Stop();
        }
    }
    public void Start() {
        if(!isStart) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            mContext.registerReceiver(mWifiP2pReceiver, intentFilter, null, null);
            isStart = true;
        }
    }
    public void Stop() {
        mContext.unregisterReceiver(mWifiP2pReceiver);
        mWifiP2pEnabled = false;
        updateWfdEnableState();
        isStart = false;
    }
    public void setSinkName(String miracastName){
        mMiracastName = miracastName;
        try {
            Object Object_setDeviceName = mWifiP2pManager.getClass().getMethod("setDeviceName", mWifiP2pChannel.getClass(), String.class, WifiP2pManager.ActionListener.class );
            ((Method)Object_setDeviceName).invoke(mWifiP2pManager, new Object[] { mWifiP2pChannel, mMiracastName, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    if (DEBUG) {
                        Log.d(TAG, "Successfully setDeviceName.");
                    }
                }
                @Override
                public void onFailure(int reason) {
                    if (DEBUG) {
                        Log.d(TAG, "Failed to setDeviceName with reason " + reason + ".");
                    }
                }
            } });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
    private void startRegistration() {
        Map record = new HashMap();
        record.put("listenport", String.valueOf(7236));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);
        mWifiP2pManager.addLocalService(mWifiP2pChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            }
        });
    }
    private void handleStateChanged(boolean enabled) {
        mWifiP2pEnabled = enabled;
        updateWfdEnableState();
    }
    //------------------------------
    class ArpTableObservationTask extends TimerTask {
        @Override
        public void run() {
            RarpImpl rarp = new RarpImpl();
            String source_ip = rarp.execRarp(mP2pInterfaceName, macAddr);
            Log.d(TAG, "## run: source_ip="+source_ip);
            if (source_ip!=null){
                Log.d(TAG, "## run: nativeMiracastIsActive source_ip="+source_ip);
                if (mCastManager.nativeMiracastIsActive(source_ip))
                {
                    Log.d(TAG, "## run: mArpTableObservationTimer.cancel1 source_ip="+source_ip);
                    mArpTableObservationTimer.cancel();
                    mArpTableObservationTimer = null;
                    return;
                }
                Log.d(TAG, "## run: nativeMiracastIsActive END source_ip="+source_ip);

            }
            if (source_ip == null) {
                Log.d(TAG, "## retry:" + mArpRetryCount);
                if (++mArpRetryCount > MAX_ARP_RETRY_COUNT) {
                    Log.d(TAG, "## run: mArpTableObservationTimer.cancel2 source_ip="+source_ip);
                    mArpTableObservationTimer.cancel();
                    return;
                }
                return;
            }

            mCastManager.nativeMiracastAccept(source_ip, mP2pControlPort);
            mArpTableObservationTimer.cancel();
            mArpTableObservationTimer = null;
        }
        String macAddr = "";
        int mArpRetryCount = 0;
    }

    private boolean isWifiDisplaySource(WifiP2pDevice dev) {
        try {
            if(dev == null)
                return false;

            Field refDeviceWfdInfo = dev.getClass().getDeclaredField("wfdInfo");
            refDeviceWfdInfo.setAccessible(true);
            Object refWfdInfo = refDeviceWfdInfo.get(dev);
            if(refWfdInfo==null){
                mP2pControlPort = DEFAULT_CONTROL_PORT;
                return false;
            }
            Method refIsWfdEnabled = refWfdInfo.getClass().getDeclaredMethod("isWfdEnabled", (Class[])null);
            Method refGetDeviceType = refWfdInfo.getClass().getDeclaredMethod("getDeviceType", (Class[])null);
            Method refGetControlPort = refWfdInfo.getClass().getDeclaredMethod("getControlPort", (Class[])null);

            Object ro = refIsWfdEnabled.invoke(refWfdInfo,(Object[])null);
            if(!(Boolean.parseBoolean(String.valueOf(ro))))
                return false;
            ro = refGetDeviceType.invoke(refWfdInfo, (Object[])null);
            int type = Integer.parseInt(String.valueOf(ro));
            ro = refGetControlPort.invoke(refWfdInfo, (Object[])null);
            mP2pControlPort = Integer.parseInt(String.valueOf(ro));
            if(mP2pControlPort==0)mP2pControlPort = DEFAULT_CONTROL_PORT;
            Field refWfdSource = refWfdInfo.getClass().getDeclaredField("WFD_SOURCE");
            Field refSourceOrPrimarySink = refWfdInfo.getClass().getDeclaredField("SOURCE_OR_PRIMARY_SINK");

            refWfdSource.setAccessible(true);
            refSourceOrPrimarySink.setAccessible(true);

            int WFD_SOURCE = Integer.parseInt(String.valueOf(refWfdSource.get(refWfdInfo)));
            int SOURCE_OR_PRIMARY_SINK = Integer.parseInt(String.valueOf(refSourceOrPrimarySink.get(refWfdInfo)));

            return (type == WFD_SOURCE) || (type == SOURCE_OR_PRIMARY_SINK);
        } catch (IllegalAccessException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (NoSuchFieldException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, ex.getMessage());
        } catch (InvocationTargetException ex) {
            Log.e(TAG, ex.getMessage());
        }
        return false;
    }

    private void invokeSink2nd() {
        mWifiP2pManager.requestConnectionInfo(mWifiP2pChannel, new WifiP2pManager.ConnectionInfoListener() {
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                if (info == null) {
                    Log.d(TAG, "## invokeSink2nd info == null ");
                    return;
                }

                if (!info.groupFormed) {
                    Log.d(TAG, "## invokeSink2nd groupFormed == false ");
                    return;
                }

                if (info.isGroupOwner) {
                    Log.d(TAG, "## invokeSink2nd isGroupOwner == true ");
                    return;
                } else {
                    String source_ip = info.groupOwnerAddress.getHostAddress();
                    Log.d(TAG, "## invokeSink2nd mCastManager.nativeMiracastAccept ");
                    mCastManager.nativeMiracastAccept(source_ip, mP2pControlPort);
                    //AdhocMiracastApi.getInstance().addMiracastClient(source_ip, mP2pControlPort);
                }
            }
        });
    }
    //----------------------
    private void handleConnectionChanged(NetworkInfo networkInfo) {
        Log.e(TAG, "## handleConnectionChanged:\n");
        mNetworkInfo = networkInfo;
        if (mWfdEnabled && networkInfo.isConnected()) {
            Log.e(TAG, "## handleConnectionChanged: networkInfo.isConnected\n");
            mWifiP2pManager.requestGroupInfo(mWifiP2pChannel, new GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup info) {
                    if (DEBUG) {
                        Log.d(TAG, "## Received group info: " + describeWifiP2pGroup(info));
                    }
                    if(info==null){
                        return;
                    }

                    mP2pControlPort = -1;
                    // Miracast device filtering
                    String macAddr = "";
                    Collection<WifiP2pDevice> p2pdevs = info.getClientList();
                    for (WifiP2pDevice dev : p2pdevs) {
                        boolean b = isWifiDisplaySource(dev);
                        if (!b && dev.status != 0) {
                            continue;
                        }else{
                            macAddr = dev.deviceAddress;
                        }
                    }
                    if (mP2pControlPort == -1) {
                        Log.e(TAG, "## handleConnectionChanged: mP2pControlPort == -1\n");
                        return;
                        //mP2pControlPort = 7236;
                    }

                    // connect
                    if (info.isGroupOwner()) {
                        Log.e(TAG, "## handleConnectionChanged: info.isGroupOwner\n");
                        mP2pInterfaceName = info.getInterface();

                        mArpTableObservationTimer = new Timer();
                        ArpTableObservationTask task = new ArpTableObservationTask();
                        task.mArpRetryCount = 0;
                        task.macAddr = macAddr;
                        mArpTableObservationTimer.scheduleAtFixedRate(task, 1000, 1*1000);
                    } else {
                        Log.e(TAG, "## handleConnectionChanged: invokeSink2nd\n");
                        Log.d(TAG, "## invokeSink2nd ");
                        invokeSink2nd();
                    }
                }
            });
            mWifiP2pManager.requestConnectionInfo(mWifiP2pChannel, new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    Log.d(TAG,"## onConnectionInfoAvailable");
                    Log.d(TAG,"## isGroupOwner：" + info.isGroupOwner);
                    Log.d(TAG,"## groupFormed：" + info.groupFormed);
                    if (info.groupFormed && info.isGroupOwner) {
                    }
                }
            });
        } else {
            disconnect();
        }
    }

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                WifiP2pManager.ActionListener listen_creategroup = new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "## createGroup onSuccess");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "## createGroup onFailure: " + reason);
                        Message msg = new Message();
                        msg.what = 2;
                        handler.sendMessage(msg);
                    }
                };
                mWifiP2pManager.createGroup(mWifiP2pChannel, listen_creategroup);
            }
            if (msg.what == 2) {
                WifiP2pManager.ActionListener listen_removegroup = new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "## removeGroup onSuccess");
                        Message msg = new Message();
                        msg.what = 1;
                        handler.sendMessage(msg);
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "## removeGroup onFailure: " + reason);
                    }
                };
                mWifiP2pManager.removeGroup(mWifiP2pChannel, listen_removegroup);
            }
        }
    };
    public void updateWfdEnableState() {
        if (mWifiP2pEnabled) {
            // WFD should be enabled.
            Log.d(TAG, "updateWfdEnableState mWfdEnabled:"+mWfdEnabled+",mWfdEnabling:"+mWfdEnabling);
            if (!mWfdEnabled && !mWfdEnabling) {
                mWfdEnabling = true;
                try {
                    Object localObject1 = mWifiP2pManager.getClass();
                    Class localClass = getClass().getClassLoader().loadClass("android.net.wifi.p2p.WifiP2pWfdInfo");
                    localObject1 = mWifiP2pManager.getClass().getMethod("setWFDInfo", mWifiP2pChannel.getClass(), localClass, WifiP2pManager.ActionListener.class );
                    Log.v("wfdsinkemu", "setWFDInfo find.");
                    Object localObject2 = ((Class)localClass).getConstructor(new Class[] { Integer.TYPE, Integer.TYPE, Integer.TYPE });
                    Log.v("wfdsinkemu", "WifiP2pWfdInfo constructor find.");
                    localObject2 = ((Constructor)localObject2).newInstance(new Object[] { Integer.valueOf(17), Integer.valueOf(DEFAULT_CONTROL_PORT), Integer.valueOf(50) });
                    Log.v("wfdsinkemu", "WifiP2pWfdInfo constructor success.");
                    WifiP2pManager.ActionListener listener1 = new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            if (DEBUG) {
                                Log.d(TAG, "Successfully set WFD info.");
                            }
                            if (mWfdEnabling) {
                                mWfdEnabling = false;
                                mWfdEnabled = true;
                                // reportFeatureState();
                            }
                        }

                        @Override
                        public void onFailure(int reason) {
                            if (DEBUG) {
                                Log.d(TAG, "Failed to set WFD info with reason " + reason + ".");
                            }
                            mWfdEnabling = false;
                        }
                    };
                    ((Method)localObject1).invoke(mWifiP2pManager, new Object[] { mWifiP2pChannel, localObject2, listener1 });

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
            }
        } else {
            // WFD should be disabled.
            mWfdEnabling = false;
            mWfdEnabled = false;

            mWifiP2pManager.removeGroup(mWifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "removeGroup onSuccess");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "removeGroup onFailure: " + reason);
                }
            });
        }
    }
    private void disconnect() {
    }

    private final BroadcastReceiver mWifiP2pReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                // This broadcast is sticky so we'll always get the initial Wifi P2PD state
                // on startup.
                boolean enabled = (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED)) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                if (DEBUG) {
                    Log.d(TAG, "Received WIFI_P2P_STATE_CHANGED_ACTION: enabled="
                            + enabled);
                }
                handleStateChanged(enabled);

            } else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                if (DEBUG) {
                    Log.d(TAG, "Received WIFI_P2P_PEERS_CHANGED_ACTION.");
                }
                // handlePeersChanged();
                mWifiP2pManager.requestPeers(mWifiP2pChannel, new WifiP2pManager.PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        mPeers = peers;
                        WifiP2pDevice device = null;
                        Log.d(TAG, "onPeersAvailable,size:" + peers.getDeviceList().size());
                        for (WifiP2pDevice wifiP2pDevice : peers.getDeviceList()) {
                            Log.d(TAG, wifiP2pDevice.toString());
                            device = wifiP2pDevice;
                        }
                    }
                });

            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo)intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
                if (DEBUG) {
                    Log.d(TAG, "Received WIFI_P2P_CONNECTION_CHANGED_ACTION: networkInfo="
                            + networkInfo);
                }
                handleConnectionChanged(networkInfo);

            }
        }
    };
    
    private static String describeWifiP2pGroup(WifiP2pGroup group) {
        return group != null ? group.toString().replace('\n', ',') : "null";
    }
}

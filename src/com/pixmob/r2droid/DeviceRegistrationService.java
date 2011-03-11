/*
 * Copyright (C) 2011 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixmob.r2droid;

import static com.pixmob.r2droid.Constants.AUTH_FAILED_ERROR;
import static com.pixmob.r2droid.Constants.AUTH_PENDING;
import static com.pixmob.r2droid.Constants.C2DM_PHONE_REGISTRATION_ERROR;
import static com.pixmob.r2droid.Constants.C2DM_SENDER_ID;
import static com.pixmob.r2droid.Constants.CONNECTED_EVENT;
import static com.pixmob.r2droid.Constants.CONNECTING_EVENT;
import static com.pixmob.r2droid.Constants.DEV;
import static com.pixmob.r2droid.Constants.DEVICE_REGISTRATION_ERROR;
import static com.pixmob.r2droid.Constants.DEVICE_UNREGISTRATION_ERROR;
import static com.pixmob.r2droid.Constants.DISCONNECTED_EVENT;
import static com.pixmob.r2droid.Constants.DISCONNECTING_EVENT;
import static com.pixmob.r2droid.Constants.NETWORK_ERROR;
import static com.pixmob.r2droid.Constants.TAG;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpGet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.c2dm.C2DMessaging;
import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.appengine.client.AppEngineClient;

/**
 * Device registration service.
 * @author Pixmob
 */
public class DeviceRegistrationService extends Service {
    public static final String ACTION_CONNECT = "com.pixmob.r2droid.intent.action.CONNECT";
    public static final String ACTION_DISCONNECT = "com.pixmob.r2droid.intent.action.DISCONNECT";
    public static final String ACTION_UPDATE_UI = "com.pixmob.r2droid.intent.action.UPDATE_UI";
    public static final String ACTION_C2DM_REGISTERED = "com.pixmob.r2droid.intent.action.C2DM_REGISTERED";
    public static final String ACTION_C2DM_UNREGISTERED = "com.pixmob.r2droid.intent.action.C2DM_UNREGISTERED";
    public static final String ACTION_C2DM_ERROR = "com.pixmob.r2droid.intent.action.C2DM_ERROR";
    public static final String KEY_EVENT = "event";
    public static final String KEY_ERROR = "error";
    public static final int STATUS_UPDATE_DONE = 1;
    private static final int STATUS_UPDATE_FOREGROUND = 2;
    private static final int HTTP_SC_OK = 200;
    private final BlockingQueue<String> pendingActions = new ArrayBlockingQueue<String>(
            2);
    private Thread actionDispatcher;
    private AndroidHttpClient httpClient;
    private AppEngineClient gaeClient;
    private PendingIntent dashboardIntent;
    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        httpClient = AndroidHttpClient.newInstance(
            getString(R.string.http_user_agent), this);
        gaeClient = new AppEngineClient(this, getString(R.string.central_host),
                httpClient);
        
        dashboardIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                DashboardActivity.class), 0);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            DeviceRegistrationService.class.getName());
        
        actionDispatcher = new ActionDispatcher();
        actionDispatcher.start();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (actionDispatcher != null) {
            actionDispatcher.interrupt();
            actionDispatcher = null;
        }
        if (gaeClient != null) {
            gaeClient.close();
            gaeClient = null;
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
        nm = null;
        dashboardIntent = null;
        wakeLock = null;
        pendingActions.clear();
    }
    
    private boolean configureClient() {
        final String account = Preferences.getAccount(getApplicationContext());
        if (account == null) {
            return false;
        }
        gaeClient.setAccount(account);
        return true;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        if (ACTION_C2DM_ERROR.equals(action)) {
            onC2DMError(intent.getStringExtra(KEY_ERROR));
        } else if (action != null) {
            addActionToQueue(action);
        }
        return START_NOT_STICKY;
    }
    
    private void handleAction(String action) {
        startForeground();
        
        if (ACTION_CONNECT.equals(action)) {
            try {
                connect();
            } finally {
                stopForeground(false);
            }
        } else if (ACTION_DISCONNECT.equals(action)) {
            try {
                disconnect();
            } finally {
                stopForeground(false);
            }
        } else if (ACTION_C2DM_REGISTERED.equals(action)) {
            try {
                onC2DMRegistered();
            } finally {
                stopForeground(true);
            }
        } else if (ACTION_C2DM_UNREGISTERED.equals(action)) {
            try {
                onC2DMUnregistered();
            } finally {
                stopForeground(true);
            }
        } else {
            Log.wtf(TAG, "Unsupported action: " + action);
        }
    }
    
    private void startForeground() {
        nm.cancel(STATUS_UPDATE_DONE);
        
        final Notification notification = new Notification(
                R.drawable.ic_stat_icon, getString(R.string.updating_device),
                System.currentTimeMillis());
        notification.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.updating_device), dashboardIntent);
        startForeground(STATUS_UPDATE_FOREGROUND, notification);
    }
    
    private void fireEvent(int event, String error) {
        final Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra(KEY_EVENT, event);
        intent.putExtra(KEY_ERROR, error);
        sendBroadcast(intent);
        
        if (event == CONNECTED_EVENT) {
            Preferences.setOnline(getApplicationContext(), true);
        } else if (event == DISCONNECTED_EVENT) {
            Preferences.setOnline(getApplicationContext(), false);
        }
    }
    
    private void connect() {
        fireEvent(CONNECTING_EVENT, null);
        if (DEV) {
            Log.i(TAG, "Registering device to C2DM");
        }
        final boolean serviceAvailable = C2DMessaging.register(this,
            C2DM_SENDER_ID);
        if (!serviceAvailable) {
            fireEvent(DISCONNECTED_EVENT, C2DM_PHONE_REGISTRATION_ERROR);
        }
    }
    
    private void disconnect() {
        fireEvent(DISCONNECTING_EVENT, null);
        final boolean serviceAvailable = C2DMessaging.unregister(this);
        if (!serviceAvailable) {
            fireEvent(CONNECTED_EVENT, C2DM_PHONE_REGISTRATION_ERROR);
        }
    }
    
    private void onC2DMRegistered() {
        final String regId = Preferences
                .getRegistrationId(getApplicationContext());
        if (DEV) {
            Log.i(TAG, "Device registered to C2DM with id " + regId);
        }
        
        int event = CONNECTED_EVENT;
        String error = null;
        
        // TODO get device name
        final String deviceName = "Unknown Device";
        try {
            final String url = "https://r2droidhq.appspot.com/api/1/register?regid="
                    + urlEncode(regId) + "&name=" + urlEncode(deviceName);
            if (DEV) {
                Log.d(TAG, "Register URL: " + url);
            }
            final HttpGet req = new HttpGet(url);
            if (!configureClient()) {
                event = DISCONNECTED_EVENT;
                error = AUTH_FAILED_ERROR;
            } else {
                final int statusCode = gaeClient.execute(req).getStatusLine()
                        .getStatusCode();
                if (statusCode != HTTP_SC_OK) {
                    if (DEV) {
                        Log.w(TAG, "Failed to register device: statusCode="
                                + statusCode);
                    }
                    event = DISCONNECTED_EVENT;
                    error = DEVICE_REGISTRATION_ERROR;
                }
            }
        } catch (AppEngineAuthenticationException e) {
            if (e.isAuthenticationPending()) {
                if (DEV) {
                    Log.i(TAG,
                        "User must give permission to use authentication token: "
                                + "registration aborted");
                }
                event = DISCONNECTED_EVENT;
                error = AUTH_PENDING;
            } else {
                if (DEV) {
                    Log.w(TAG, "Authentication error", e);
                }
                event = DISCONNECTED_EVENT;
                error = AUTH_FAILED_ERROR;
            }
        } catch (IOException e) {
            if (DEV) {
                Log.w(TAG, "Network error", e);
            }
            event = DISCONNECTED_EVENT;
            error = NETWORK_ERROR;
        } catch (Exception e) {
            Log.wtf(TAG, "Unexpected error", e);
            event = DISCONNECTED_EVENT;
            error = DEVICE_REGISTRATION_ERROR;
        }
        
        fireEvent(event, error);
        
        int ticketRes = R.string.device_is_online;
        if (event != CONNECTED_EVENT) {
            ticketRes = R.string.updating_device_failed;
        }
        final Notification notification = new Notification(
                R.drawable.ic_stat_icon, getString(ticketRes), System
                        .currentTimeMillis());
        notification.setLatestEventInfo(this, getString(R.string.app_name),
            getString(ticketRes), dashboardIntent);
        nm.notify(STATUS_UPDATE_DONE, notification);
    }
    
    private void onC2DMUnregistered() {
        final String regId = Preferences
                .getRegistrationId(getApplicationContext());
        if (DEV) {
            Log.i(TAG, "Device unregistered from C2DM");
        }
        
        int event = DISCONNECTED_EVENT;
        String error = null;
        
        try {
            final String url = "https://r2droidhq.appspot.com/api/1/unregister?regid="
                    + urlEncode(regId);
            if (DEV) {
                Log.d(TAG, "Unregister URL: " + url);
            }
            final HttpGet req = new HttpGet(url);
            if (!configureClient()) {
                event = CONNECTED_EVENT;
                error = AUTH_FAILED_ERROR;
            } else {
                final int statusCode = gaeClient.execute(req).getStatusLine()
                        .getStatusCode();
                if (statusCode == HTTP_SC_OK) {
                    Preferences.setAccount(getApplicationContext(), null);
                } else {
                    if (DEV) {
                        Log.w(TAG, "Failed to unregister device: statusCode="
                                + statusCode);
                    }
                    event = CONNECTED_EVENT;
                    error = DEVICE_UNREGISTRATION_ERROR;
                }
            }
        } catch (AppEngineAuthenticationException e) {
            if (e.isAuthenticationPending()) {
                if (DEV) {
                    Log.i(TAG,
                        "User must give permission to use authentication token: "
                                + "unregistration aborted", e);
                }
                event = CONNECTED_EVENT;
                error = AUTH_PENDING;
            } else {
                if (DEV) {
                    Log.w(TAG, "Authentication error", e);
                }
                event = CONNECTED_EVENT;
                error = AUTH_FAILED_ERROR;
            }
        } catch (IOException e) {
            if (DEV) {
                Log.w(TAG, "Network error", e);
            }
            event = CONNECTED_EVENT;
            error = NETWORK_ERROR;
        } catch (Exception e) {
            Log.wtf(TAG, "Unexpected error", e);
            event = CONNECTED_EVENT;
            error = DEVICE_UNREGISTRATION_ERROR;
        }
        
        fireEvent(event, error);
        
        int ticketRes = R.string.device_is_offline;
        if (event != DISCONNECTED_EVENT) {
            ticketRes = R.string.updating_device_failed;
        }
        final Notification notification = new Notification(
                R.drawable.ic_stat_icon, getString(ticketRes), System
                        .currentTimeMillis());
        notification.setLatestEventInfo(this, getString(R.string.app_name),
            getString(ticketRes), dashboardIntent);
        nm.notify(STATUS_UPDATE_DONE, notification);
    }
    
    private void onC2DMError(String error) {
        if (DEV) {
            Log.w(TAG, "C2DM error: " + error);
        }
        final boolean online = Preferences.isOnline(getApplicationContext());
        fireEvent(online ? CONNECTED_EVENT : DISCONNECTED_EVENT, error);
        stopForeground(true);
    }
    
    private void addActionToQueue(String action) {
        if (action != null) {
            try {
                pendingActions.put(action);
            } catch (InterruptedException e) {
                if (DEV) {
                    Log.d(TAG, "Failed to queue action: " + action, e);
                }
            }
        }
    }
    
    private static String urlEncode(String str) {
        String encoded = str;
        try {
            encoded = URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "UTF-8 encoding is unavailable", e);
        }
        return encoded;
    }
    
    /**
     * Internal action dispatcher.
     * @author Pixmob
     */
    private class ActionDispatcher extends Thread {
        public ActionDispatcher() {
            super("R2droid Action Dispatcher");
        }
        
        @Override
        public void run() {
            boolean running = true;
            
            String nextAction = null;
            while (running) {
                try {
                    nextAction = pendingActions.poll(30, TimeUnit.SECONDS);
                    if (nextAction == null) {
                        if (DEV) {
                            Log.d(TAG, "No action was made recently: "
                                    + "action dispatcher is being stopped");
                        }
                        running = false;
                        stopSelf();
                    } else {
                        if (DEV) {
                            Log.d(TAG, "Handling action: " + nextAction);
                        }
                        wakeLock.acquire();
                        try {
                            handleAction(nextAction);
                        } finally {
                            wakeLock.release();
                        }
                    }
                } catch (InterruptedException e) {
                    if (DEV) {
                        Log.d(TAG, "Action dispatcher was interrupted");
                    }
                    running = false;
                } catch (Exception e) {
                    if (DEV) {
                        Log.w(TAG, "Error when dispatching action: "
                                + nextAction, e);
                    }
                }
            }
        }
    }
}

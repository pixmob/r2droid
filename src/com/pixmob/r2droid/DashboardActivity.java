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
import static com.pixmob.r2droid.Constants.C2DM_SERVICE_NOT_AVAILABLE_ERROR;
import static com.pixmob.r2droid.Constants.CONNECTED_EVENT;
import static com.pixmob.r2droid.Constants.CONNECTING_EVENT;
import static com.pixmob.r2droid.Constants.DEV;
import static com.pixmob.r2droid.Constants.DEVICE_REGISTRATION_ERROR;
import static com.pixmob.r2droid.Constants.DEVICE_UNREGISTRATION_ERROR;
import static com.pixmob.r2droid.Constants.DISCONNECTED_EVENT;
import static com.pixmob.r2droid.Constants.DISCONNECTING_EVENT;
import static com.pixmob.r2droid.Constants.NETWORK_ERROR;
import static com.pixmob.r2droid.Constants.TAG;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dashboard activity. This activity is shown when the application starts.
 * @author Pixmob
 */
public class DashboardActivity extends Activity {
    private static final Map<String, Integer> ERROR_STRINGS = new HashMap<String, Integer>(
            5);
    private static final int SELECT_ACCOUNT_REQUEST = 1337;
    private static final int PROGRESS_DIALOG = 1;
    private static final int ERROR_DIALOG = 2;
    private State state;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (ERROR_STRINGS.isEmpty()) {
            ERROR_STRINGS.put(C2DM_PHONE_REGISTRATION_ERROR,
                R.string.c2dm_phone_registration_error);
            ERROR_STRINGS.put(C2DM_SERVICE_NOT_AVAILABLE_ERROR,
                R.string.c2dm_service_not_available_error);
            ERROR_STRINGS.put(NETWORK_ERROR, R.string.network_error);
            ERROR_STRINGS.put(AUTH_FAILED_ERROR, R.string.auth_failed_error);
            ERROR_STRINGS.put(AUTH_PENDING, R.string.auth_pending);
            ERROR_STRINGS.put(DEVICE_REGISTRATION_ERROR,
                R.string.device_registration_error);
            ERROR_STRINGS.put(DEVICE_UNREGISTRATION_ERROR,
                R.string.device_unregistration_error);
        }
        
        setContentView(R.layout.dashboard);
        
        registerReceiver(onUpdateUIReceiver, new IntentFilter(
                DeviceRegistrationService.ACTION_UPDATE_UI));
        
        state = (State) getLastNonConfigurationInstance();
        if (state == null) {
            state = new State();
        }
        state.attach(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(onUpdateUIReceiver);
        state.detach();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        clearNotification();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        clearNotification();
    }
    
    private void clearNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(DeviceRegistrationService.STATUS_UPDATE_DONE);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }
    
    private void onEvent(final int event, final String error) {
        if (DEV) {
            Log.d(TAG, "Got event: event=" + event + ", error=" + error);
        }
        
        if (event == DISCONNECTED_EVENT) {
            final TextView accountName = (TextView) findViewById(R.id.account_name);
            accountName.setText(null);
        } else if (event == CONNECTED_EVENT) {
            final String account = Preferences
                    .getAccount(getApplicationContext());
            final TextView accountName = (TextView) findViewById(R.id.account_name);
            accountName.setText(account);
        }
        
        final ImageView statusIcon = (ImageView) findViewById(R.id.status_icon);
        int statusIconRes = R.drawable.offline;
        if (CONNECTED_EVENT == event || DISCONNECTING_EVENT == event) {
            statusIconRes = R.drawable.online;
        }
        statusIcon.setImageResource(statusIconRes);
        
        final TextView statusText = (TextView) findViewById(R.id.status_text);
        int statusTextRes = R.string.device_is_offline;
        if (CONNECTED_EVENT == event || DISCONNECTING_EVENT == event) {
            statusTextRes = R.string.device_is_online;
        }
        statusText.setText(statusTextRes);
        
        final View connectButton = findViewById(R.id.connect_button);
        final View disconnectButton = findViewById(R.id.disconnect_button);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        if (CONNECTED_EVENT == event) {
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
        } else if (CONNECTING_EVENT == event || DISCONNECTING_EVENT == event) {
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(false);
        }
        
        if (error != null) {
            final Bundle args = new Bundle();
            args.putString("error", error);
            showDialog(ERROR_DIALOG, args);
        } else if (CONNECTING_EVENT == event || DISCONNECTING_EVENT == event) {
            final Bundle args = new Bundle();
            args.putInt("event", event);
            showDialog(PROGRESS_DIALOG, args);
        } else {
            dismissDialogQuietly(PROGRESS_DIALOG);
        }
        
        if (event == DISCONNECTED_EVENT || event == CONNECTED_EVENT) {
            clearNotification();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (SELECT_ACCOUNT_REQUEST == requestCode) {
            String account = null;
            if (resultCode == RESULT_OK) {
                account = data
                        .getStringExtra(SelectAccountActivity.KEY_ACCOUNT);
            }
            if (account != null) {
                if (DEV) {
                    Log.i(TAG, "Selected account: " + account);
                }
            } else {
                if (DEV) {
                    Log.i(TAG, "No account was selected");
                }
                
                Toast.makeText(this, R.string.account_required,
                    Toast.LENGTH_LONG).show();
            }
            
            Preferences.setAccount(getApplicationContext(), account);
            final TextView accountName = (TextView) findViewById(R.id.account_name);
            accountName.setText(account);
            if (account != null) {
                connect();
            }
        } else {
            Log.wtf(TAG, "Unknown request code for result: " + resultCode);
        }
    }
    
    public void onConnect(View view) {
        String account = Preferences.getAccount(getApplicationContext());
        
        if (account == null) {
            if (DEV) {
                Log.i(TAG, "No account is selected");
            }
            
            startActivityForResult(
                new Intent(this, SelectAccountActivity.class),
                SELECT_ACCOUNT_REQUEST);
        } else {
            connect();
        }
    }
    
    private void connect() {
        startService(new Intent(DeviceRegistrationService.ACTION_CONNECT));
    }
    
    public void onDisconnect(View view) {
        startService(new Intent(DeviceRegistrationService.ACTION_DISCONNECT));
    }
    
    private void dismissDialogQuietly(int id) {
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
        }
    }
    
    @Override
    protected Dialog onCreateDialog(final int id, final Bundle args) {
        if (ERROR_DIALOG == id) {
            final String error = args.getString("error");
            final Integer messageRes = ERROR_STRINGS.get(error);
            final String messageStr;
            if (messageRes != null) {
                messageStr = getString(messageRes);
            } else {
                Log.wtf(TAG, "Missing message for error " + error);
                messageStr = String.format(getString(R.string.unknown_error),
                    error);
            }
            if (DEV) {
                Log.d(TAG, "Display dialog for error " + error);
            }
            return new AlertDialog.Builder(this).setTitle(R.string.error)
                    .setMessage(messageStr).setIcon(
                        R.drawable.alert_dialog_icon).setPositiveButton(
                        R.string.ok, errorClickListener).create();
        } else if (PROGRESS_DIALOG == id) {
            final int event = args.getInt("event");
            if (DEV) {
                Log.d(TAG, "Display dialog for event " + event);
            }
            final ProgressDialog d = new ProgressDialog(this);
            d.setTitle(R.string.please_wait);
            d.setIcon(R.drawable.alert_dialog_icon);
            d.setMessage(getString(R.string.updating_device));
            d.setCancelable(false);
            d.setIndeterminate(true);
            d.setOnDismissListener(progressDismissListener);
            return d;
        } else {
            Log.wtf(TAG, "Unsupported dialog id: " + id + ", args=" + args);
        }
        
        return null;
    }
    
    private final OnClickListener errorClickListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dismissDialogQuietly(PROGRESS_DIALOG);
        }
    };
    
    private final OnDismissListener progressDismissListener = new OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            dismissDialogQuietly(ERROR_DIALOG);
        }
    };
    
    private final BroadcastReceiver onUpdateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int event = intent.getIntExtra(
                DeviceRegistrationService.KEY_EVENT, DISCONNECTED_EVENT);
            final String error = intent
                    .getStringExtra(DeviceRegistrationService.KEY_ERROR);
            state.fireEvent(event, error);
        }
    };
    
    static class State {
        private static final int EVENT_UPDATED = 1;
        volatile DashboardActivity activity;
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (EVENT_UPDATED == msg.what) {
                    if (activity != null) {
                        activity.onEvent(msg.arg1, (String) msg.obj);
                    } else {
                        if (DEV) {
                            Log.w(TAG, "No activity: cannot deliver event "
                                    + msg.arg1 + " with error " + msg.obj);
                        }
                    }
                } else {
                    super.handleMessage(msg);
                }
            }
        };
        
        public void fireEvent(int event, String error) {
            final Message msg = new Message();
            msg.what = EVENT_UPDATED;
            msg.arg1 = event;
            msg.obj = error;
            handler.sendMessage(msg);
        }
        
        public void attach(final DashboardActivity activity) {
            if (DEV) {
                Log.d(TAG, "Attach state to new activity: " + activity);
            }
            this.activity = activity;
            
            if (Preferences.isOnline(activity.getApplicationContext())) {
                fireEvent(CONNECTED_EVENT, null);
            } else {
                fireEvent(DISCONNECTED_EVENT, null);
            }
        }
        
        public void detach() {
            activity = null;
        }
    }
}

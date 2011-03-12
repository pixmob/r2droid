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

import static com.pixmob.r2droid.Commands.ring;
import static com.pixmob.r2droid.Commands.say;
import static com.pixmob.r2droid.Commands.vibrate;
import static com.pixmob.r2droid.Constants.DEV;
import static com.pixmob.r2droid.Constants.TAG;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;

/**
 * Command executor service.
 * @author Pixmob
 */
public class CommandExecutorService extends ActionService {
    public static final String ACTION_EXECUTE = "com.pixmob.r2droid.intent.action.EXECUTE_COMMAND";
    public static final String KEY_COMMAND = "command";
    private static final String ACTION_CANCEL = "com.pixmob.r2droid.intent.action.CANCEL_COMMAND";
    private static final int STATUS_COMMAND_EXECUTION = 3;
    private Handler uiHandler;
    private PendingIntent cancelCommandIntent;
    
    public CommandExecutorService() {
        super("R2droid Command Executor");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        cancelCommandIntent = PendingIntent.getService(this, 0, new Intent(
                ACTION_CANCEL), 0);
        
        uiHandler = new UIHandler(this);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        uiHandler = null;
        cancelCommandIntent = null;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    protected boolean isActionCancelled(Intent intent) {
        return ACTION_CANCEL.equals(intent.getAction());
    }
    
    @Override
    protected void handleAction(Intent intent)
            throws ActionExecutionFailedException, InterruptedException {
        final String command = intent.getStringExtra(KEY_COMMAND);
        if (DEV) {
            Log.i(TAG, "Executing command: " + command);
        }
        final String msg = String.format(getString(R.string.executing_command),
            command);
        final Notification notification = new Notification(
                R.drawable.ic_stat_icon, msg, System.currentTimeMillis());
        notification.setLatestEventInfo(this, msg,
            getString(R.string.tap_to_cancel_command), cancelCommandIntent);
        startForeground(STATUS_COMMAND_EXECUTION, notification);
        
        try {
            if ("ring".equals(command)) {
                ring(this);
            } else if ("vibrate".equals(command)) {
                vibrate(this);
            } else if (command.startsWith("say ")) {
                final String text = command.substring("say ".length());
                if (text.length() > 0) {
                    say(this, text);
                }
            }
        } catch (ActionExecutionFailedException e) {
            throw e;
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new ActionExecutionFailedException(
                    "Command execution failed: " + command, e);
        } finally {
            if (DEV) {
                Log.i(TAG, "Command finished: " + command);
            }
            stopForeground(true);
        }
    }
    
    @Override
    protected void onActionError(Intent intent, Exception e) {
        final String command = intent.getStringExtra(KEY_COMMAND);
        if (DEV) {
            Log.w(TAG, "Command execution failed: " + command, e);
        }
        if (uiHandler != null) {
            final Message m = new Message();
            m.what = UIHandler.ERROR;
            m.obj = String.format(getString(R.string.command_execution_failed),
                command);
            uiHandler.sendMessage(m);
        } else {
            if (DEV) {
                Log.w(TAG, "No UIHandler: cannot error for command " + command);
            }
        }
    }
    
    private static class UIHandler extends Handler {
        public static final int ERROR = 1;
        private final Context context;
        
        public UIHandler(final Context context) {
            this.context = context;
        }
        
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ERROR) {
                Toast.makeText(context, (String) msg.obj, Toast.LENGTH_LONG)
                        .show();
            }
            super.handleMessage(msg);
        }
    }
}

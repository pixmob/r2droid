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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Command executor service.
 * @author Pixmob
 */
public class CommandExecutorService extends Service {
    public static final String ACTION_EXECUTE = "com.pixmob.r2droid.intent.action.EXECUTE_COMMAND";
    public static final String KEY_COMMAND = "command";
    private static final String ACTION_CANCEL = "com.pixmob.r2droid.intent.action.CANCEL_COMMAND";
    private static final int STATUS_COMMAND_EXECUTION = 3;
    private final BlockingQueue<String> pendingCommands = new ArrayBlockingQueue<String>(
            8);
    private final AtomicBoolean cancelCommand = new AtomicBoolean();
    private Handler uiHandler;
    private Thread executor;
    private PendingIntent cancelCommandIntent;
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        cancelCommandIntent = PendingIntent.getService(this, 0, new Intent(
                ACTION_CANCEL), 0);
        
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            CommandExecutorService.class.getName());
        
        uiHandler = new UIHandler(this);
        
        executor = new Executor();
        executor.start();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.interrupt();
            executor = null;
        }
        uiHandler = null;
        cancelCommandIntent = null;
        wakeLock = null;
        pendingCommands.clear();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : null;
        if (ACTION_EXECUTE.equals(action)) {
            final String command = intent.getStringExtra(KEY_COMMAND);
            if (command != null) {
                final boolean commandAdded = pendingCommands.offer(command);
                if (!commandAdded) {
                    if (DEV) {
                        Log.w(TAG, "Command queue is full");
                    }
                    Toast.makeText(this, R.string.command_queue_full,
                        Toast.LENGTH_LONG).show();
                } else {
                    if (DEV) {
                        Log.i(TAG, "Command queued for execution: " + command);
                    }
                }
            }
        } else if (ACTION_CANCEL.equals(action)) {
            if (DEV) {
                Log.i(TAG, "Canceling current command");
            }
            cancelCommand.set(true);
            executor.interrupt();
        }
        
        return START_NOT_STICKY;
    }
    
    private void executeCommand(String command)
            throws CommandExecutionFailedException, InterruptedException {
        final String msg = String.format(getString(R.string.executing_command),
            command);
        final Notification notification = new Notification(
                R.drawable.ic_stat_icon, msg, System.currentTimeMillis());
        notification.setLatestEventInfo(this, msg,
            getString(R.string.tap_to_cancel_command), cancelCommandIntent);
        startForeground(STATUS_COMMAND_EXECUTION, notification);
        
        cancelCommand.set(false);
        
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
        } catch (CommandExecutionFailedException e) {
            throw e;
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionFailedException(
                    "Command execution failed: " + command, e);
        } finally {
            stopForeground(true);
        }
    }
    private class Executor extends Thread {
        public Executor() {
            super("R2droid Command Executor");
        }
        
        @Override
        public void run() {
            boolean running = true;
            String nextCommand = null;
            
            while (running) {
                try {
                    nextCommand = pendingCommands.poll(60, TimeUnit.SECONDS);
                    if (nextCommand == null) {
                        if (DEV) {
                            Log.d(TAG, "No command was made recently: "
                                    + "command executor is being stopped");
                        }
                        running = false;
                        stopSelf();
                    } else {
                        if (DEV) {
                            Log.i(TAG, "Executing command: " + nextCommand);
                        }
                        wakeLock.acquire();
                        try {
                            executeCommand(nextCommand);
                        } finally {
                            if (DEV) {
                                Log.i(TAG, "Command finished: " + nextCommand);
                            }
                            wakeLock.release();
                        }
                    }
                } catch (InterruptedException e) {
                    if (Thread.interrupted() && !cancelCommand.get()) {
                        if (DEV) {
                            Log.d(TAG, "Command executor was interrupted");
                        }
                        running = false;
                    } else {
                        // the command was canceled as the result of
                        // interrupting the thread
                        if (DEV) {
                            Log.i(TAG, "Command was canceled: "
                                    + "rescheduling command executor");
                        }
                    }
                } catch (CommandExecutionFailedException e) {
                    if (DEV) {
                        Log.w(TAG, "Error when executing command: "
                                + nextCommand, e);
                    }
                    showCommandExecutionFailed(nextCommand);
                } catch (Exception e) {
                    if (DEV) {
                        Log.wtf(TAG, "Unexpected error", e);
                    }
                    showCommandExecutionFailed(nextCommand);
                }
            }
        }
        
        private void showCommandExecutionFailed(String command) {
            if (uiHandler != null) {
                final Message m = new Message();
                m.what = UIHandler.ERROR;
                m.obj = String.format(
                    getString(R.string.command_execution_failed), command);
                uiHandler.sendMessage(m);
            } else {
                if (DEV) {
                    Log.w(TAG, "No UIHandler: cannot error for command "
                            + command);
                }
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

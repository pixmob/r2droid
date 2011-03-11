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

import static com.pixmob.r2droid.Constants.C2DM_SENDER_ID;

import java.io.IOException;

import android.content.Context;
import android.content.Intent;

import com.google.android.c2dm.C2DMBaseReceiver;

/**
 * C2DM receiver. This class receives events from the C2DM handler.
 * @author Pixmob
 */
public class C2DMReceiver extends C2DMBaseReceiver {
    public C2DMReceiver() {
        super(C2DM_SENDER_ID);
    }
    
    @Override
    public void onError(Context context, String errorId) {
        final Intent intent = new Intent(
                DeviceRegistrationService.ACTION_C2DM_ERROR);
        intent.putExtra(DeviceRegistrationService.KEY_ERROR, errorId);
        startService(intent);
    }
    
    @Override
    protected void onMessage(Context context, Intent intent) {
        final String command = intent.getStringExtra("command");
        if (command != null) {
            final Intent commandIntent = new Intent(
                    CommandExecutorService.ACTION_EXECUTE);
            commandIntent.putExtra(CommandExecutorService.KEY_COMMAND, command);
            startService(commandIntent);
        }
    }
    
    @Override
    public void onRegistrered(Context context, String registrationId)
            throws IOException {
        Preferences.setRegistrationId(getApplicationContext(), registrationId);
        startService(new Intent(
                DeviceRegistrationService.ACTION_C2DM_REGISTERED));
    }
    
    @Override
    public void onUnregistered(Context context) {
        startService(new Intent(
                DeviceRegistrationService.ACTION_C2DM_UNREGISTERED));
    }
}

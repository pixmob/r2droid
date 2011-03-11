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

/**
 * Application constants.
 * @author Pixmob
 */
final class Constants {
    /**
     * Log tag. Use this constant for logging statements.
     */
    public static final String TAG = "R2droid";
    
    /**
     * Flag set to <code>true</code> when the application is running in
     * development mode.
     */
    public static final boolean DEV = true;
    
    public static final String C2DM_SENDER_ID = "r2droidapp@gmail.com";
    
    public static final int CONNECTING_EVENT = 3;
    public static final int DISCONNECTING_EVENT = 4;
    public static final int CONNECTED_EVENT = 1;
    public static final int DISCONNECTED_EVENT = 2;
    
    public static final String C2DM_SERVICE_NOT_AVAILABLE_ERROR = "SERVICE_NOT_AVAILABLE";
    public static final String C2DM_PHONE_REGISTRATION_ERROR = "PHONE_REGISTRATION_ERROR";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String AUTH_FAILED_ERROR = "AUTH_FAILED";
    public static final String DEVICE_REGISTRATION_ERROR = "DEVICE_REGISTRATION_ERROR";
    public static final String DEVICE_UNREGISTRATION_ERROR = "DEVICE_UNREGISTRATION_ERROR";
    public static final String AUTH_PENDING = "AUTH_PENDING";
    
    private Constants() {
    }
}

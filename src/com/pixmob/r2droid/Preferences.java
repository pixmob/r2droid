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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Application preferences.
 * @author Pixmob
 */
final class Preferences {
    private static final String ACCOUNT_PREF = "account";
    private static final String REG_ID_PREF = "regId";
    private static final String ONLINE_PREF = "online";
    
    private Preferences() {
    }
    
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("r2droid", Context.MODE_PRIVATE);
    }
    
    public static String getAccount(Context context) {
        return getPrefs(context).getString(ACCOUNT_PREF, null);
    }
    
    public static void setAccount(Context context, String account) {
        getPrefs(context).edit().putString(ACCOUNT_PREF, account).commit();
    }
    
    public static String getRegistrationId(Context context) {
        return getPrefs(context).getString(REG_ID_PREF, null);
    }
    
    public static void setRegistrationId(Context context, String regId) {
        getPrefs(context).edit().putString(REG_ID_PREF, regId).commit();
    }
    
    public static boolean isOnline(Context context) {
        return getPrefs(context).getBoolean(ONLINE_PREF, false);
    }
    
    public static void setOnline(Context context, boolean online) {
        getPrefs(context).edit().putBoolean(ONLINE_PREF, online).commit();
    }
}

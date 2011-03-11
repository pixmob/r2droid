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

import static com.pixmob.r2droid.Constants.DEV;
import static com.pixmob.r2droid.Constants.TAG;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.http.client.methods.HttpGet;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.Toast;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.appengine.client.AppEngineClient;

/**
 * Activity for selecting a Google account.
 * @author Pixmob
 */
public class SelectAccountActivity extends ListActivity {
    public static final String ACTION_PICK = "com.pixmob.r2droid.intent.action.pick";
    public static final String KEY_ACCOUNT = "account";
    private static final String STATE_SELECTED_ACCOUNT = "selectedAccountEmail";
    private static final int PROGRESS_DIALOG = 1;
    private static final int NETWORK_ERROR_DIALOG = 2;
    private static final int AUTH_ERROR_DIALOG = 3;
    private static final int AUTH_PENDING_DIALOG = 4;
    private AccountManager accountManager;
    private AccountAdapter accountAdapter;
    private String selectedAccount;
    private AccountVerifier accountVerifier;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_account);
        accountManager = AccountManager.get(this);
        accountAdapter = new AccountAdapter(this);
        setListAdapter(accountAdapter);
        
        // get a previously started account verifier
        accountVerifier = (AccountVerifier) getLastNonConfigurationInstance();
        if (accountVerifier != null) {
            // an account verifier is running
            // (the screen configuration may have changed):
            // attach this task to this context
            accountVerifier.context = this;
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return accountVerifier;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        initAccounts();
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        selectedAccount = ((Account) l.getItemAtPosition(position)).name;
        accountAdapter.notifyDataSetInvalidated();
        findViewById(R.id.sign_in_button).setEnabled(true);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        selectedAccount = savedInstanceState.getString(STATE_SELECTED_ACCOUNT);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_ACCOUNT, selectedAccount);
    }
    
    private void initAccounts() {
        selectedAccount = null;
        
        // get accounts
        final Account[] accounts = accountManager
                .getAccountsByType("com.google");
        if (accounts.length == 1) {
            // only one account: auto select it
            selectedAccount = accounts[0].name;
            finishWithResult();
        } else if (accounts.length == 0) {
            Toast.makeText(this, R.string.no_account_found, Toast.LENGTH_LONG)
                    .show();
            
            // found no account
            selectedAccount = null;
            finishWithResult();
        } else {
            Arrays.sort(accounts, AccountComparator.INSTANCE);
            
            // build UI from accounts
            accountAdapter.setNotifyOnChange(false);
            accountAdapter.clear();
            boolean foundSelectedAccount = false;
            for (final Account account : accounts) {
                accountAdapter.add(account);
                if (account.name.equals(selectedAccount)) {
                    foundSelectedAccount = true;
                }
            }
            if (!foundSelectedAccount) {
                selectedAccount = null;
            }
            accountAdapter.notifyDataSetChanged();
        }
    }
    
    private void finishWithResult() {
        final Intent i = new Intent(ACTION_PICK);
        if (selectedAccount == null) {
            setResult(RESULT_CANCELED, i);
        } else {
            i.putExtra(KEY_ACCOUNT, selectedAccount);
            setResult(RESULT_OK, i);
        }
        finish();
    }
    
    public void onSignIn(View view) {
        showDialog(PROGRESS_DIALOG);
        accountVerifier = new AccountVerifier();
        accountVerifier.context = this;
        accountVerifier.execute(selectedAccount);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (AUTH_PENDING_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.error)
                    .setMessage(R.string.auth_pending).setIcon(
                        R.drawable.alert_dialog_icon).create();
        }
        if (AUTH_ERROR_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.error)
                    .setMessage(R.string.auth_failed_error).setIcon(
                        R.drawable.alert_dialog_icon).create();
        }
        if (NETWORK_ERROR_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.error)
                    .setMessage(R.string.network_error).setIcon(
                        R.drawable.alert_dialog_icon).create();
        }
        if (PROGRESS_DIALOG == id) {
            final ProgressDialog d = new ProgressDialog(this);
            d.setTitle(R.string.please_wait);
            d.setMessage(getString(R.string.checking_account));
            d.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    accountVerifier.cancel(true);
                    accountVerifier = null;
                }
            });
            return d;
        }
        return super.onCreateDialog(id);
    }
    
    /**
     * {@link Account} comparator for sorting accounts by their name.
     * @author Pixmob
     */
    private static class AccountComparator implements Comparator<Account> {
        public static final Comparator<Account> INSTANCE = new AccountComparator();
        
        private AccountComparator() {
        }
        
        @Override
        public int compare(Account object1, Account object2) {
            return object1.name.compareTo(object2.name);
        }
    }
    
    /**
     * {@link Account} adapter.
     * @author Pixmob
     */
    private class AccountAdapter extends ArrayAdapter<Account> {
        public AccountAdapter(final Context context) {
            super(context, R.layout.account_row, R.id.account_name);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater inflater = getLayoutInflater();
            final View row = inflater.inflate(R.layout.account_row, null);
            row.setTag(row.findViewById(R.id.account_name));
            
            final Account account = getItem(position);
            final CheckedTextView ctv = (CheckedTextView) row.getTag();
            ctv.setChecked(selectedAccount == account.name);
            ctv.setText(account.name);
            
            return row;
        }
    }
    
    /**
     * Task for checking an account. The execution of this task may ask for user
     * authorization for using an account.
     * @author Pixmob
     */
    private static class AccountVerifier extends
            AsyncTask<String, Void, Integer> {
        SelectAccountActivity context;
        private AndroidHttpClient httpClient;
        private AppEngineClient gaeClient;
        
        @Override
        protected Integer doInBackground(String... params) {
            final String account = params[0];
            httpClient = AndroidHttpClient.newInstance(context
                    .getString(R.string.http_user_agent), context);
            final String host = context.getString(R.string.central_host);
            gaeClient = new AppEngineClient(context, host, httpClient, account);
            final HttpGet req = new HttpGet("http://" + host);
            
            if (DEV) {
                Log.i(TAG, "Checking authentication for account " + account);
            }
            
            int dialogId = -1;
            try {
                final int statusCode = gaeClient.execute(req).getStatusLine()
                        .getStatusCode();
                if (statusCode == 302) {
                    // success!
                    if (DEV) {
                        Log.i(TAG, "Authentication was successful");
                    }
                } else {
                    dialogId = AUTH_ERROR_DIALOG;
                    if (DEV) {
                        Log.w(TAG, "Authentication server "
                                + "is unavailable (statuscode=" + statusCode
                                + ")");
                    }
                }
            } catch (IOException e) {
                if (DEV) {
                    Log.w(TAG, "Network error while checking account", e);
                }
                dialogId = NETWORK_ERROR_DIALOG;
            } catch (AppEngineAuthenticationException e) {
                if (e.isAuthenticationPending()) {
                    if (DEV) {
                        Log.i(TAG, "Waiting for user authorization "
                                + "for using an account");
                    }
                    dialogId = AUTH_PENDING_DIALOG;
                } else {
                    if (DEV) {
                        Log.w(TAG, "Authentication failed "
                                + "while checking account", e);
                    }
                    dialogId = AUTH_ERROR_DIALOG;
                }
            }
            
            return dialogId;
        }
        
        @Override
        protected void onCancelled() {
            super.onCancelled();
            dispose();
            if (context != null) {
                context.dismissDialog(PROGRESS_DIALOG);
            }
        }
        
        @Override
        protected void onPostExecute(Integer dialogId) {
            super.onPostExecute(dialogId);
            dispose();
            if (context != null) {
                context.dismissDialog(PROGRESS_DIALOG);
                if (dialogId > -1) {
                    // something was wrong: a dialog is displayed
                    context.showDialog(dialogId);
                } else {
                    // authentication was successful: the activity is finished
                    context.finishWithResult();
                }
            }
        }
        
        private void dispose() {
            if (gaeClient != null) {
                gaeClient.close();
                gaeClient = null;
            }
            if (httpClient != null) {
                httpClient.close();
                httpClient = null;
            }
        }
    }
}

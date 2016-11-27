package info.nightscout.android.medtronic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import io.realm.Realm;
import io.realm.RealmResults;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * A login screen that offers login via username/password.
 */
public class GetHmacAndKeyActivity extends AppCompatActivity implements LoaderCallbacks<Cursor> {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    // TODO - Replace with Rx.Java
    private GetHmacAndKey mHmacAndKeyTask = null;

    // UI references.
    private EditText mUsernameView;
    private EditText mPasswordView;
    private EditText mHostnameView;
    private View mProgressView;
    private View mLoginFormView;
    private TextView mRegisteredStickView;
    private MenuItem mLoginMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Register USB");

        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mHostnameView = (EditText) findViewById(R.id.hostname);
        mHostnameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                attemptLogin();
                return true;
            }
        });


        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mRegisteredStickView = (TextView) findViewById(R.id.registered_usb_devices);

        showRegisteredSticks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_register_usb, menu);

        mLoginMenuItem = menu.findItem(R.id.action_menu_login);
        mLoginMenuItem.setIcon(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_cloud_download).color(Color.WHITE).actionBar());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_menu_login:
                attemptLogin();
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mHmacAndKeyTask != null || !checkOnline("Please connect to the Internet", "You must be online to register your USB stick.")) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);
        mHostnameView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();
        String hostname = mHostnameView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username address.
        if (TextUtils.isEmpty(username)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        }

        // Check for a  carelink server hostname (this is optional for a user to define)
        if (TextUtils.isEmpty(hostname)) {
            hostname = getString(R.string.server_hostname); // default
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mHmacAndKeyTask = new GetHmacAndKey(username, password, hostname);
            mHmacAndKeyTask.execute((Void) null);
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void showRegisteredSticks() {
        Realm realm = Realm.getDefaultInstance();

        RealmResults<ContourNextLinkInfo> results = realm.where(ContourNextLinkInfo.class).findAll();

        String deviceTableHtml = "<big><b>Registered Devices</b></big><br/>";

        for (ContourNextLinkInfo info : results) {
            String longSerial = info.getSerialNumber();
            String key = info.getKey();

            deviceTableHtml += String.format("<b>Serial Number:</b> %s %s<br/>", longSerial, key == null ? "&#x2718;" : "&#x2714;");
        }

        mRegisteredStickView.setText(Html.fromHtml(deviceTableHtml));
    }

    private boolean checkOnline(String title, String message) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        boolean isOnline = (netInfo != null && netInfo.isConnectedOrConnecting());

        if (!isOnline) {
            new AlertDialog.Builder(this, R.style.AppTheme)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        return isOnline;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class GetHmacAndKey extends AsyncTask<Void, Void, Boolean> {

        private final String mUsername;
        private final String mPassword;
        private final String mHostname;

        // Note: if AsyncTask declaration can be located and changed,
        // then we can pass status to onPostExecute() in return value
        // from doInBackground()
        // and not have to store it this way.
        private String mStatus = "success";

        GetHmacAndKey(String username, String password, String hostname) {
            mUsername = username;
            mPassword = password;
            mHostname = hostname;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            HttpResponse response;
            try {
                DefaultHttpClient client = new DefaultHttpClient();
                HttpPost loginPost = new HttpPost(mHostname + "/patient/j_security_check");
                List<NameValuePair> nameValuePairs = new ArrayList<>();
                nameValuePairs.add(new BasicNameValuePair("j_username", mUsername));
                nameValuePairs.add(new BasicNameValuePair("j_password", mPassword));
                nameValuePairs.add(new BasicNameValuePair("j_character_encoding", "UTF-8"));
                loginPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
                response = client.execute(loginPost);

                if (response.getStatusLine().getStatusCode() == 200) {
                    // Get the HMAC/keys for every serial we have seen
                    Realm realm = Realm.getDefaultInstance();

                    RealmResults<ContourNextLinkInfo> results = realm.where(ContourNextLinkInfo.class).findAll();
                    for (ContourNextLinkInfo info : results) {
                        String longSerial = info.getSerialNumber();

                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        ObjectOutputStream hmacRequest = new ObjectOutputStream(buffer);
                        hmacRequest.writeInt(0x1c);
                        hmacRequest.writeObject(longSerial.replaceAll("\\d+-", ""));

                        HttpPost hmacPost = new HttpPost(mHostname + "/patient/secure/SnapshotServer/");
                        hmacPost.setEntity(new ByteArrayEntity(buffer.toByteArray()));
                        hmacPost.setHeader("Content-type", "application/octet-stream");
                        response = client.execute(hmacPost);

                        ByteArrayInputStream inputBuffer = new ByteArrayInputStream(EntityUtils.toByteArray(response.getEntity()));
                        ObjectInputStream hmacResponse = new ObjectInputStream(inputBuffer);
                        byte[] hmacBytes = (byte[]) hmacResponse.readObject();
                        ArrayUtils.reverse(hmacBytes);
                        String hmac = MessageUtils.byteArrayToHexString(hmacBytes);

                        buffer.reset();
                        inputBuffer.reset();

                        ObjectOutputStream keyRequest = new ObjectOutputStream(buffer);
                        keyRequest.writeInt(0x1f);
                        keyRequest.writeObject(longSerial);

                        HttpPost keyPost = new HttpPost(mHostname + "/patient/secure/SnapshotServer/");
                        keyPost.setEntity(new ByteArrayEntity(buffer.toByteArray()));
                        keyPost.setHeader("Content-type", "application/octet-stream");
                        response = client.execute(keyPost);

                        inputBuffer = new ByteArrayInputStream(EntityUtils.toByteArray(response.getEntity()));
                        ObjectInputStream keyResponse = new ObjectInputStream(inputBuffer);
                        keyResponse.readInt(); // Throw away the first int. Not sure what it does
                        String key = MessageUtils.byteArrayToHexString((byte[]) keyResponse.readObject());

                        realm.beginTransaction();
                        info.setHmac(hmac);
                        info.setKey(key);
                        realm.commitTransaction();
                    }

                    return true;
                }

            } catch (ClientProtocolException e) {
                mStatus = getString(R.string.error_client_protocol_exception);
                return false;
            } catch (IOException e) {
                mStatus = getString(R.string.error_io_exception);
                return false;
            } catch (ClassNotFoundException e) {
                mStatus = getString(R.string.error_class_not_found_exception);
                return false;
            }
<<<<<<< HEAD
            mStatus = getString(R.string.error_http_response);
=======

            mStatus = getString(R.string.error_http_response) + "http response: " + response.getStatusLine();
>>>>>>> specify_hostname
            return false;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mHmacAndKeyTask = null;

            if (success) {
                showRegisteredSticks();
                mLoginMenuItem.setVisible(false);
                mLoginFormView.setVisibility(View.GONE);
                mProgressView.setVisibility(View.GONE);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);
            } else {
                showProgress(false);
                mPasswordView.setError(mStatus);
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mHmacAndKeyTask = null;
            showProgress(false);
        }
    }
}

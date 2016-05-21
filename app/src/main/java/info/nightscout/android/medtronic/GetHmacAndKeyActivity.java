package info.nightscout.android.medtronic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.data.CNLConfigContract;
import info.nightscout.android.medtronic.data.CNLConfigDbHelper;
import info.nightscout.android.medtronic.message.MessageUtils;

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

/**
 * A login screen that offers login via username/password.
 */
public class GetHmacAndKeyActivity extends Activity implements LoaderCallbacks<Cursor> {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private GetHmacAndKey mHmacAndKeyTask = null;

    // UI references.
    private EditText mUsernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private TextView mRegisteredStickView;
    private Button mCloseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button usernameSignInButton = (Button) findViewById(R.id.username_sign_in_button);
        usernameSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
        Button closeButton = (Button) findViewById(R.id.close_button);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });


        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mRegisteredStickView = (TextView)findViewById(R.id.registered_usb_devices);
        mCloseButton = (Button)findViewById(R.id.close_button);
        showRegisteredSticks();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mHmacAndKeyTask != null) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();

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

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mHmacAndKeyTask = new GetHmacAndKey(username, password);
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
        CNLConfigDbHelper configDbHelper = new CNLConfigDbHelper(getBaseContext());
        Cursor cursor = configDbHelper.getAllRows();

        String deviceTableHtml = "<big><b>Registered Devices</b></big><br/>";

        while( !cursor.isAfterLast() ) {
            String longSerial = cursor.getString(cursor.getColumnIndex(CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL));
            String key = cursor.getString(cursor.getColumnIndex(CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY));

            deviceTableHtml += String.format("<b>Serial Number:</b> %s<br/><b>Key:</b> %s<br/>", longSerial, key );

            cursor.moveToNext();
        }

        mRegisteredStickView.setText(Html.fromHtml( deviceTableHtml ));
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

        GetHmacAndKey(String username, String password ) {
            mUsername = username;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            try {
                DefaultHttpClient client = new DefaultHttpClient();
                HttpPost loginPost = new HttpPost("https://carelink.minimed.eu/patient/j_security_check");
                List<NameValuePair> nameValuePairs = new ArrayList<>();
                nameValuePairs.add(new BasicNameValuePair("j_username", mUsername));
                nameValuePairs.add(new BasicNameValuePair("j_password", mPassword));
                nameValuePairs.add(new BasicNameValuePair("j_character_encoding", "UTF-8"));
                loginPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
                HttpResponse response = client.execute(loginPost);

                if (response.getStatusLine().getStatusCode() == 200) {
                    // Get the HMAC/keys for every serial in the Config database
                    CNLConfigDbHelper configDbHelper = new CNLConfigDbHelper(getBaseContext());
                    Cursor cursor = configDbHelper.getAllRows();

                    while( !cursor.isAfterLast() ) {
                        String longSerial = cursor.getString(cursor.getColumnIndex(CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL));

                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        ObjectOutputStream hmacRequest = new ObjectOutputStream(buffer);
                        hmacRequest.writeInt(0x1c);
                        hmacRequest.writeObject(longSerial.replaceAll("\\d+-", ""));

                        HttpPost hmacPost = new HttpPost("https://carelink.minimed.eu/patient/secure/SnapshotServer/");
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

                        HttpPost keyPost = new HttpPost("https://carelink.minimed.eu/patient/secure/SnapshotServer/");
                        keyPost.setEntity(new ByteArrayEntity(buffer.toByteArray()));
                        keyPost.setHeader("Content-type", "application/octet-stream");
                        response = client.execute(keyPost);

                        inputBuffer = new ByteArrayInputStream(EntityUtils.toByteArray(response.getEntity()));
                        ObjectInputStream keyResponse = new ObjectInputStream(inputBuffer);
                        keyResponse.readInt(); // Throw away the first int. Not sure what it does
                        String key = MessageUtils.byteArrayToHexString((byte[]) keyResponse.readObject());

                        // TODO - return false if this returns 0? What would we do anyway?
                        configDbHelper.setHmacAndKey(longSerial, hmac, key);

                        cursor.moveToNext();
                    }

                    return true;
                }

            } catch (ClientProtocolException e) {
                return false;
            } catch (IOException e) {
                return false;
            } catch (ClassNotFoundException e) {
                return false;
            }

            return false;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mHmacAndKeyTask = null;

            if (success) {
                showRegisteredSticks();
                mCloseButton.setVisibility(View.VISIBLE);
                mLoginFormView.setVisibility(View.GONE);
                mProgressView.setVisibility(View.GONE);
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);
            } else {
                showProgress(false);
                mPasswordView.setError(getString(R.string.error_incorrect_password));
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
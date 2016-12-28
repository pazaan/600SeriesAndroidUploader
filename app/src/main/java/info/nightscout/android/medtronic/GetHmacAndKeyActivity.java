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

    // UI references.
    private TextView mRegisteredStickView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Registered Devices");

        mRegisteredStickView = (TextView) findViewById(R.id.registered_usb_devices);

        showRegisteredSticks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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

    private void showRegisteredSticks() {
        Realm realm = Realm.getDefaultInstance();

        RealmResults<ContourNextLinkInfo> results = realm.where(ContourNextLinkInfo.class).findAll();

        String deviceTableHtml = "";

        for (ContourNextLinkInfo info : results) {
            String longSerial = info.getSerialNumber();
            String key = info.getKey();

            deviceTableHtml += String.format("<b>Serial Number:</b> %s %s<br/>", longSerial, key == null ? "&#x2718;" : "&#x2714;");
        }

        mRegisteredStickView.setText(Html.fromHtml(deviceTableHtml));
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
}
package info.nightscout.android.medtronic;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.util.ArrayList;

import info.nightscout.android.R;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import io.realm.Realm;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * A login screen that offers login via username/password.
 */
public class ManageCNLActivity extends AppCompatActivity {
    private Realm mRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_cnl);

        Toolbar toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(
                    new IconicsDrawable(this)
                            .icon(GoogleMaterial.Icon.gmd_arrow_back)
                            .color(Color.WHITE)
                            .sizeDp(24)
            );
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle(R.string.manage_cnl__title);
        }

        mRealm = Realm.getDefaultInstance();

        //generate list
        ArrayList<ContourNextLinkInfo> list = new ArrayList<>();

        list.addAll(mRealm.where(ContourNextLinkInfo.class).findAll());

        //instantiate custom adapter
        CNLAdapter adapter = new CNLAdapter(list, this);

        //handle listview and assign adapter
        ListView lView = findViewById(R.id.cnl_list);
        lView.addHeaderView(getLayoutInflater().inflate(R.layout.manage_cnl_listview_header, null));
        lView.setEmptyView(findViewById(R.id.manage_cnl_listview_empty)); //getLayoutInflater().inflate(R.layout.manage_cnl_listview_empty, null));
        lView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // avoid memory leaks
                if (mRealm != null && !mRealm.isClosed()) mRealm.close();
                mRealm = null;
                finish();
                break;
        }
        return true;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    private class CNLAdapter extends BaseAdapter implements ListAdapter {
        private ArrayList<ContourNextLinkInfo> list = new ArrayList<>();
        private Context context;

        public CNLAdapter(ArrayList<ContourNextLinkInfo> list, Context context) {
            this.list = list;
            this.context = context;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int pos) {
            return list.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos; //list.get(pos).getSerialNumber();
            //just return 0 if your list items do not have an Id variable.
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.cnl_item, parent, false);
            }

            //Handle TextView and display string from your list
            TextView listItemText = view.findViewById(R.id.cnl_mac);
            listItemText.setText(list.get(position).getSerialNumber());

            //Handle buttons and add onClickListeners
            Button deleteBtn = view.findViewById(R.id.delete_btn);

            deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // deleting CNL form database
                    mRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            ContourNextLinkInfo cnlToDelete = realm.where(ContourNextLinkInfo.class).equalTo("serialNumber", list.get(position).getSerialNumber()).findFirst();
                            cnlToDelete.deleteFromRealm();
                            list.remove(position);
                            notifyDataSetChanged();
                        }
                    });

                }
            });

            return view;
        }
    }
}
package ca.vijayan.flypic;

import android.app.ListActivity;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;


public class SelectEndpoint extends ListActivity {
    private BrowserCastServiceDiscovery.Callback mCallback;
    private BrowserCastServiceDiscovery mDiscovery;
    private ArrayList<String> mListItems;
    private ArrayAdapter<String> mArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_endpoint);
        mListItems = new ArrayList<String>();
        mArrayAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                mListItems);
        setListAdapter(mArrayAdapter);
        initializeDiscovery();
        mDiscovery.startDiscovery();
    }

    @Override
    public void onListItemClick(ListView view, View w, int position, long id) {
        NsdServiceInfo info = mDiscovery.getServiceForName(mListItems.get(position));
        mDiscovery.stopDiscovery();
        Log.d("SelectEndpoint", "Selected pos=" + position + " id=" + id +
                " host=" + info.getHost().getHostAddress() + ":" + info.getPort());
        Intent picViewIntent = new Intent(getApplicationContext(), PicView.class);
        picViewIntent.putExtra("host", info.getHost().getHostAddress());
        picViewIntent.putExtra("port", info.getPort());
        startActivity(picViewIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_select_endpoint, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    private static final int MAX_ENDPOINTS = 50;
    private void initializeDiscovery() {
        mCallback = new BrowserCastServiceDiscovery.Callback() {
            @Override
            public void serviceFound(String name) {
                Log.d("SelectEndpoint", "serviceFound " + name);
                // Just bail if there are too many endpoints.
                if (mListItems.size() > MAX_ENDPOINTS)
                    return;

                int foundItemIndex = mListItems.size();
                for (int i = 0; i < mListItems.size(); i++) {
                    if (mListItems.get(i).compareTo(name) > 0) {
                        foundItemIndex = i;
                        break;
                    }
                }

                mListItems.add(foundItemIndex, name);
                notifyChange();
            }

            @Override
            public void serviceLost(String name) {
                Log.d("SelectEndpoint", "serviceLost " + name);
                for (int i = 0; i < mListItems.size(); i++) {
                    int cmp = mListItems.get(i).compareTo(name);
                    if (cmp < 0)
                        continue;
                    if (cmp == 0) {
                        mListItems.remove(i);
                        break;
                    }
                    if (cmp > 0)
                        break;
                }
                notifyChange();
            }

            @Override
            public void error(String reason) {
                Log.e("SelectEndpoint", reason);
            }

            @Override
            public void failure(String reason) {
                Log.d("SelectEndpoint", reason);
            }

            private void notifyChange() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mArrayAdapter.notifyDataSetChanged();
                    }
                });
            }
        };
        mDiscovery = new BrowserCastServiceDiscovery(this, mCallback);
    }
}

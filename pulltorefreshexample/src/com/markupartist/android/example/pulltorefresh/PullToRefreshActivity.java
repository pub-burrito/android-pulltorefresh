package com.markupartist.android.example.pulltorefresh;

import java.util.Arrays;
import java.util.LinkedList;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.markupartist.android.widget.PullToRefreshListView;
import com.markupartist.android.widget.PullToRefreshListView.OnRefreshListener;

public class PullToRefreshActivity extends ListActivity {    
    /**
	 * 
	 */
	private static final String TAG = "TEST";

	private LinkedList<String> mListItems;

	TextView secondaryHeaderView;
    TextView footerView;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pull_to_refresh);

        // Set a listener to be invoked when the list should be refreshed.
        final PullToRefreshListView ptrListView = (PullToRefreshListView) getListView();
        
		ptrListView.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Do work to refresh the list here.
                new GetDataTask().execute();
            }
        });
        
		secondaryHeaderView = new TextView(this);
		secondaryHeaderView.setText("[Testing another header]");
		
		/*
        
        getListView().setFooterDividersEnabled(true);
        
		getListView().addHeaderView(secondaryHeaderView, null, false);
		
		footerView = new TextView(this);
		footerView.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
		
		AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		footerView.setLayoutParams(layoutParams);
		footerView.setText("[Testing filler footer]");
		getListView().addFooterView(footerView, null, false);
		 */

        mListItems = new LinkedList<String>();
        mListItems.addAll(Arrays.asList(mStrings));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mListItems);

        setListAdapter(adapter);
        
        ((ViewGroup) findViewById(R.id.pull_to_reveal_header)).addView(secondaryHeaderView);
        
        getListView().setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long id) {
				Log.i(TAG, "Adapter: " + getListAdapter());
				Log.i(TAG, "Pos: " + pos + ", Id: " + id + ", ItemId: " + getListAdapter().getItemId(pos) + ", Item: " + getListAdapter().getItem((int) id) );
				
				onRefreshComplete();
			}
		});
    }
    
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	onRefreshComplete();
    }

	private void onRefreshComplete() {
		final PullToRefreshListView ptrListView = (PullToRefreshListView) getListView();
		ptrListView.onRefreshComplete();
		
		/*
		footerView.setHeight(0);
		
		ptrListView.post(new Runnable() {
			
			@Override
			public void run() {
				Log.d(TAG, "Footer top: " + footerView.getBottom() + ", parent height: " + ptrListView.getHeight());
				
				int bottomGap = ptrListView.getHeight() - footerView.getBottom();
				
				//int headersHeight = ptrListView.getHeadersHeight();
				
				boolean lastHeaderVisible = ptrListView.getFirstVisiblePosition() < ptrListView.getHeaderViewsCount();
				
				if (bottomGap >= 0 && lastHeaderVisible) {
					Log.d(TAG, "Bottom gap: " + bottomGap + ", scrollY: " + secondaryHeaderView.getBottom());// + ", headersHeight: " + headersHeight);
					
					bottomGap += Math.max(secondaryHeaderView.getBottom(), 0);
					footerView.setHeight(bottomGap);
					
					ptrListView.setFirstSelected();
				} else {
					bottomGap = 0;
				}
			}
		});
		*/
		
	}
    
    private class GetDataTask extends AsyncTask<Void, Void, String[]> {

        @Override
        protected String[] doInBackground(Void... params) {
            // Simulates a background job.
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                ;
            }
            return mStrings;
        }

        @SuppressWarnings("unchecked")
		@Override
        protected void onPostExecute(String[] result) {
            mListItems.addFirst("Added after refresh...");
            ((ArrayAdapter<String>)getListAdapter()).notifyDataSetChanged();

            // Call onRefreshComplete when the list has been refreshed.
            onRefreshComplete();
			
            super.onPostExecute(result);
        }
    }

    private String[] mStrings = {
            "Abbaye de Belloc", "Abbaye du Mont des Cats", "Abertam",
            "Abondance", "Ackawi", "Acorn"/*, "Adelost", "Affidelice au Chablis",
            "Afuega'l Pitu", "Airag", "Airedale", "Aisy Cendre",
            "Allgauer Emmentaler"*/};
}

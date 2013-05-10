package com.markupartist.android.widget;


import java.lang.reflect.Field;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.markupartist.android.widget.pulltorefresh.R;

public class PullToRefreshListView extends ListView implements OnScrollListener {

	
	private static final int TAP_TO_REFRESH = 1;
    private static final int PULL_TO_REFRESH = 2;
    private static final int RELEASE_TO_REFRESH = 3;
    private static final int REFRESHING = 4;

    private static final String TAG = "PullToRefreshListView";
    
    private static final int RELEASE_DELAY = 500;

    private OnRefreshListener mOnRefreshListener;
    private OnRevealListener mOnRevealListener;

    /**
     * Listener that will receive notifications every time the list scrolls.
     */
    private OnScrollListener mOnScrollListener;
    private LayoutInflater mInflater;

    private RelativeLayout mRefreshView;
    private TextView mRefreshViewText;
    private ImageView mRefreshViewImage;
    private ProgressBar mRefreshViewProgress;
    private TextView mRefreshViewLastUpdated;
    
    private RelativeLayout mRevealView;
    private RelativeLayout mFillerFooterView;

    private int mCurrentScrollState;
    private int mRefreshState;

    private RotateAnimation mFlipAnimation;
    private RotateAnimation mReverseFlipAnimation;

    private int mRefreshViewHeight;
    private int mRefreshOriginalTopPadding;
    private int mLastMotionY;
    private int mInitialMoveFirstVisiblePosition = Integer.MAX_VALUE;
    private int mDir = 0;
    
    private Handler handler;

    private boolean mBounceHack;
    private boolean mIsDown;
    
    public PullToRefreshListView(Context context) {
        super(context);
        init(context);
    }

    public PullToRefreshListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PullToRefreshListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        // Load all of the animations we need in code rather than through XML
        mFlipAnimation = new RotateAnimation(0, -180,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mFlipAnimation.setInterpolator(new LinearInterpolator());
        mFlipAnimation.setDuration(250);
        mFlipAnimation.setFillAfter(true);
        mReverseFlipAnimation = new RotateAnimation(-180, 0,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        mReverseFlipAnimation.setInterpolator(new LinearInterpolator());
        mReverseFlipAnimation.setDuration(250);
        mReverseFlipAnimation.setFillAfter(true);

        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

		mRefreshView = (RelativeLayout) mInflater.inflate(
				R.layout.pull_to_refresh_header, this, false);
        mRefreshViewText =
            (TextView) mRefreshView.findViewById(R.id.pull_to_refresh_text);
        mRefreshViewImage =
            (ImageView) mRefreshView.findViewById(R.id.pull_to_refresh_image);
        mRefreshViewProgress =
            (ProgressBar) mRefreshView.findViewById(R.id.pull_to_refresh_progress);
        mRefreshViewLastUpdated =
            (TextView) mRefreshView.findViewById(R.id.pull_to_refresh_updated_at);
        
        mRevealView = (RelativeLayout) mInflater.inflate(
				R.layout.pull_to_reveal_header, this, false);

        mFillerFooterView = (RelativeLayout) mInflater.inflate(
        		R.layout.filler_footer, this, false);
        
        mRefreshViewImage.setMinimumHeight(50);
        mRefreshView.setOnClickListener(new OnClickRefreshListener());
        mRefreshOriginalTopPadding = mRefreshView.getPaddingTop();
        
        handler = new Handler();
        
        changeState(TAP_TO_REFRESH);

        addHeaderView(mRefreshView);
        addHeaderView(mRevealView);
        addFooterView(mFillerFooterView);

        super.setOnScrollListener(this);

        measureView(mRefreshView);
        mRefreshViewHeight = mRefreshView.getMeasuredHeight();
    }

	protected void changeState(int state) {
		mRefreshState = state;
		
		handler.removeCallbacks(changeStateToReleaseToRefresh);
	}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFirstSelected();
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);

        setFirstSelected();
    }
    
    public void setPullEnabled(boolean enabled) {
    	if (!enabled) {
    		try {
	    		if (mRefreshView != null) 		removeHeaderView(mRefreshView);
	    		if (mRevealView != null) 		removeHeaderView(mRevealView);
	    		if (mFillerFooterView != null) 	removeFooterView(mFillerFooterView);
    		} catch (Exception e) {
    			Log.e(TAG, "Could not disable pull to refresh views", e);
    		}
    		
    		ListAdapter currentAdapter = getAdapter();
    		if (currentAdapter instanceof HeaderViewListAdapter) {
    			setAdapter(((HeaderViewListAdapter) currentAdapter).getWrappedAdapter());
    		}
    		
    		setOnRefreshListener(null);
    	}
    }
    
    /**
     * Set the listener that will receive notifications every time the list
     * scrolls.
     * 
     * @param l The scroll listener. 
     */
    @Override
    public void setOnScrollListener(AbsListView.OnScrollListener l) {
        mOnScrollListener = l;
    }

    /**
     * Register a callback to be invoked when this list should be refreshed.
     * 
     * @param onRefreshListener The callback to run.
     */
    public void setOnRefreshListener(OnRefreshListener onRefreshListener) {
        mOnRefreshListener = onRefreshListener;
    }
    
    /**
     * Register a callback to be invoked when this list's pull to reveal header has been shown.
     * 
     * @param onRevealListener The callback to run.
     */
    public void setOnRevealListener(OnRevealListener onRevealListener) {
    	mOnRevealListener = onRevealListener;
    }

    /**
     * Set a text to represent when the list was last updated. 
     * @param lastUpdated Last updated at.
     */
    public void setLastUpdated(CharSequence lastUpdated) {
        if (lastUpdated != null) {
            mRefreshViewLastUpdated.setVisibility(View.VISIBLE);
            mRefreshViewLastUpdated.setText(lastUpdated);
        } else {
            mRefreshViewLastUpdated.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	if (mOnRefreshListener != null) { //only treat touch events if there's a refresh listener, otherwise let's consider it disabled
	        final int y = (int) event.getY();
	        mBounceHack = false;
	
	        switch (event.getAction()) {
	            case MotionEvent.ACTION_UP:
	            	Log.d(TAG, "Action Up. First visible: " + getFirstVisiblePosition() + ", state: " + mRefreshState);
	            	mIsDown = false;
	            	
	                if (!isVerticalScrollBarEnabled()) {
	                    setVerticalScrollBarEnabled(true);
	                }
	                if (getFirstVisiblePosition() < getHeaderViewsCount() && mRefreshState != REFRESHING) {
	                    if ((mRefreshView.getBottom() >= mRefreshViewHeight
	                            || mRefreshView.getTop() >= 0)
	                            && mRefreshState == RELEASE_TO_REFRESH) {
	                        // Initiate the refresh
	                    	changeState(REFRESHING);
	                        
	                        Log.d(TAG, "Changing state to REFRESHING=" + REFRESHING);
	                        
	                        prepareForRefresh();
	                        onRefresh();
	                    } else if (mRefreshView.getBottom() < mRefreshViewHeight
	                            || mRefreshView.getTop() <= 0
	                            || mRevealView.getTop() <= 0) {
	                        setFirstSelected();
	                        resetHeader();
	                    }
	                }
	                break;
	            case MotionEvent.ACTION_DOWN:
	            	Log.d(TAG, "Action Down. First visible: " + getFirstVisiblePosition() + ", state: " + mRefreshState);
	            	
	                mLastMotionY = y;
	                mInitialMoveFirstVisiblePosition = getFirstVisiblePosition();
	                mIsDown = true;
	                mDir = 0;
	                
	                break;
	            case MotionEvent.ACTION_MOVE:
	            	Log.d(TAG, "Action Move. state: " + mRefreshState);
	            	
	            	mDir = mLastMotionY < y ? 1 : -1;
	            	
	                applyHeaderPadding(event);
	                break;
	        }
    	}
        return super.onTouchEvent(event);
    }

    private void applyHeaderPadding(MotionEvent ev) {
        // getHistorySize has been available since API 1
        int pointerCount = ev.getHistorySize();

        for (int p = 0; p < pointerCount; p++) {
            if (mRefreshState == RELEASE_TO_REFRESH || mRefreshState == PULL_TO_REFRESH) {
                if (isVerticalFadingEdgeEnabled()) {
                    setVerticalScrollBarEnabled(false);
                }

                int historicalY = (int) ev.getHistoricalY(p);

                // Calculate the padding to apply, we divide by 1.7 to
                // simulate a more resistant effect during pull.
                int topPadding = (int) (((historicalY - mLastMotionY)
                        - mRefreshViewHeight) / 1.7);

                mRefreshView.setPadding(
                        mRefreshView.getPaddingLeft(),
                        topPadding,
                        mRefreshView.getPaddingRight(),
                        mRefreshView.getPaddingBottom());
            }
        }
    }

    /**
     * Sets the header padding back to original size.
     */
    private void resetHeaderPadding() {
        mRefreshView.setPadding(
                mRefreshView.getPaddingLeft(),
                mRefreshOriginalTopPadding,
                mRefreshView.getPaddingRight(),
                mRefreshView.getPaddingBottom());
    }
    
    @SuppressWarnings("unchecked")
	public int getHeadersHeight() {
    	int headersHeight = 0;
    	
    	try {
    		Field headersField = ListView.class.getDeclaredField("mHeaderViewInfos");
        	headersField.setAccessible(true);

			List<FixedViewInfo> headers = (List<FixedViewInfo>) headersField.get(this);
			
			for (FixedViewInfo fixedViewInfo : headers) {
				headersHeight += fixedViewInfo.view.getHeight();
			}
			
			Log.d(TAG, "Headers: " + headers + ", Height: " + headersHeight);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    	return headersHeight;
    }

    /**
     * Resets the header to the original state.
     */
    private void resetHeader() {
        if (mRefreshState != TAP_TO_REFRESH) {
            changeState(TAP_TO_REFRESH);
            Log.d(TAG, "Changing state to TAP_TO_REFRESH=" + TAP_TO_REFRESH);

            resetHeaderPadding();

            // Set refresh view text to the pull label
            mRefreshViewText.setText(R.string.pull_to_refresh_tap_label);
            // Replace refresh drawable with arrow drawable
            mRefreshViewImage.setImageResource(R.drawable.ic_pulltorefresh_arrow);
            // Clear the full rotation animation
            mRefreshViewImage.clearAnimation();
            // Hide progress bar and arrow.
            mRefreshViewImage.setVisibility(View.GONE);
            mRefreshViewProgress.setVisibility(View.GONE);
            //mRefreshView.setVisibility(View.GONE);
        }
    }

    private void measureView(View child) {
        ViewGroup.LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int childWidthSpec = ViewGroup.getChildMeasureSpec(0,
                0 + 0, p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    Runnable changeStateToReleaseToRefresh = new Runnable() {

		@Override
		public void run() {
			mRefreshViewText.setText(R.string.pull_to_refresh_release_label);
            mRefreshViewImage.clearAnimation();
            mRefreshViewImage.startAnimation(mFlipAnimation);
            changeState(RELEASE_TO_REFRESH);
            
            Log.d(TAG, "Changing state to RELEASE_TO_REFRESH=" + RELEASE_TO_REFRESH);
		}
		
    };
    
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
    	Log.d(TAG, "onScroll: state: " + mCurrentScrollState + ", firstVisibleItem: " + firstVisibleItem + ", dir: " + mDir);
    	
    	if (mOnRefreshListener != null) { //only treat touch events if there's a refresh listener, otherwise let's consider it disabled
    		
    		boolean canSeeHeaders = firstVisibleItem < getHeaderViewsCount();
    		boolean isScrollingUp = mDir == 1;
    		
	        // When the refresh view is completely visible, change the text to say
	        // "Release to refresh..." and flip the arrow drawable.
	        if (mCurrentScrollState == SCROLL_STATE_TOUCH_SCROLL
	                && mRefreshState != REFRESHING) {
	        	
	            if (canSeeHeaders) {
	            	
	                mRefreshViewImage.setVisibility(View.VISIBLE);
	                //mRefreshView.setVisibility(View.VISIBLE);
	                if ((mRefreshView.getBottom() >= mRefreshViewHeight + 20
	                        || mRefreshView.getTop() >= 0)
	                        && mRefreshState != RELEASE_TO_REFRESH) {
	                    
	                	handler.postDelayed(changeStateToReleaseToRefresh, RELEASE_DELAY);
	                    
	                } else if (mRefreshView.getBottom() < mRefreshViewHeight/2// + 20
	                        && mRefreshState != PULL_TO_REFRESH) {
	                	
	                    mRefreshViewText.setText(R.string.pull_to_refresh_pull_label);
	                    if (mRefreshState != TAP_TO_REFRESH) {
	                        mRefreshViewImage.clearAnimation();
	                        mRefreshViewImage.startAnimation(mReverseFlipAnimation);
	                    }
	                    changeState(PULL_TO_REFRESH);
	                    
	                    Log.d(TAG, "Changing state to PULL_TO_REFRESH=" + PULL_TO_REFRESH);
	                    
	                }
	            } else {
	                mRefreshViewImage.setVisibility(View.GONE);
	                //mRefreshView.setVisibility(View.GONE);
	                resetHeader();
	            }
	        } else if (mCurrentScrollState == SCROLL_STATE_FLING
	                && canSeeHeaders
	                && mRefreshState != REFRESHING
	                && isScrollingUp
	                && !mIsDown) {
	        	
	            setFirstSelected();
	            mBounceHack = true;
	            
	        } else if (mCurrentScrollState == SCROLL_STATE_FLING 
	        		&& canSeeHeaders
	        		&& mBounceHack
	        		&& isScrollingUp) {
	            setFirstSelected();
	            mBounceHack = false;
	        }
    	}

        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(view, firstVisibleItem,
                    visibleItemCount, totalItemCount);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mCurrentScrollState = scrollState;
        
        Log.d(TAG, "onScrollStateChanged: mCurrentScrollState= " + scrollState);

        if (mCurrentScrollState == SCROLL_STATE_IDLE) {
            mBounceHack = false;
            mDir = 0;
        }

        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChanged(view, scrollState);
        }
    }

    public void prepareForRefresh() {
        resetHeaderPadding();

        mRefreshViewImage.setVisibility(View.GONE);
        //mRefreshView.setVisibility(View.GONE);
        // We need this hack, otherwise it will keep the previous drawable.
        mRefreshViewImage.setImageDrawable(null);
        mRefreshViewProgress.setVisibility(View.VISIBLE);

        // Set refresh view text to the refreshing label
        mRefreshViewText.setText(R.string.pull_to_refresh_refreshing_label);

        changeState(REFRESHING);
        
        Log.d(TAG, "Changing state to PULL_TO_REFRESH=" + PULL_TO_REFRESH);
    }

    public void onRefresh() {
        Log.d(TAG, "onRefresh");

        if (mOnRefreshListener != null) {
            mOnRefreshListener.onRefresh();
        }
    }

    /**
     * Resets the list to a normal state after a refresh.
     * @param lastUpdated Last updated at.
     */
    public void onRefreshComplete(CharSequence lastUpdated) {
        setLastUpdated(lastUpdated);
        onRefreshComplete();
    }

    /**
     * Resets the list to a normal state after a refresh.
     */
    public void onRefreshComplete() {
    	if (mOnRefreshListener == null) return;
    	
        Log.d(TAG, "onRefreshComplete. mRefreshView.getBottom: " + mRefreshView.getBottom());

        resetHeader();
        resizeFillerHeader();

        // If refresh view is visible when loading completes, scroll down to
        // the next item.
        if (mRefreshView.getBottom() > 0) {
            invalidateViews();
            setFirstSelected();
        }
    }

	private void resizeFillerHeader() {
		final PullToRefreshListView ptrListView = this;
		
		final android.view.ViewGroup.LayoutParams layoutParams = mFillerFooterView.getLayoutParams();
		layoutParams.height = 0;
		
		mFillerFooterView.post(new Runnable() {
			
			@Override
			public void run() {
				Log.d(TAG, "Footer top: " + mFillerFooterView.getBottom() + ", parent height: " + ptrListView.getHeight());
				
				int bottomGap = ptrListView.getHeight() - mFillerFooterView.getBottom();
				
				boolean lastHeaderVisible = ptrListView.getFirstVisiblePosition() < ptrListView.getHeaderViewsCount();
				
				if (bottomGap >= 0 && lastHeaderVisible) {
					bottomGap += Math.max(mRevealView.getBottom(), 0);
					layoutParams.height = bottomGap;
					
					ptrListView.setFirstSelected();
				}
			}
		});
	}

	public void setFirstSelected() {
		//handler.removeCallbacks(setFirstSelected);
		//handler.post(setFirstSelected);
		setFirstSelected.run();
		
	}

	Runnable setFirstSelected = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "Selecting first item from list");
			
			setSelection(getFirstSelectableItem());
			
			changeState(TAP_TO_REFRESH);
		}
	};

	public int getFirstSelectableItem() {
		int revealIndex = 1;
		int firstVisiblePosition = getFirstVisiblePosition();
		
		Log.d(TAG, "firstVisiblePosition: " + firstVisiblePosition + ", headerViews: " + getHeaderViewsCount() + ", state: " + mRefreshState);
		
		//if (firstVisiblePosition == revealIndex) return revealIndex;
		
		int selectedPos = 
				mInitialMoveFirstVisiblePosition <= getHeaderViewsCount() && mDir == 1 ||
				firstVisiblePosition < getHeaderViewsCount() && 
				getHeaderViewsCount() > 1 && 
				(mRefreshState != TAP_TO_REFRESH) ? 
			getHeaderViewsCount() - 1 : 
			getHeaderViewsCount();
			
			
		if (selectedPos == revealIndex && mOnRevealListener != null) {
			mOnRevealListener.onReveal();
		}
		
		return selectedPos;
	}

    /**
     * Invoked when the refresh view is clicked on. This is mainly used when
     * there's only a few items in the list and it's not possible to drag the
     * list.
     */
    private class OnClickRefreshListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (mRefreshState != REFRESHING) {
                prepareForRefresh();
                onRefresh();
            }
        }

    }

    /**
     * Interface definition for a callback to be invoked when list should be
     * refreshed.
     */
    public interface OnRefreshListener {
        /**
         * Called when the list should be refreshed.
         * <p>
         * A call to {@link PullToRefreshListView #onRefreshComplete()} is
         * expected to indicate that the refresh has completed.
         */
        public void onRefresh();
    }
    
    /**
     * Interface definition for a callback to be invoked when reveal view has been displayed fully.
     */
    public interface OnRevealListener {
        /**
         * Called when the reveal view was shown.
         */
        public void onReveal();
    }
}

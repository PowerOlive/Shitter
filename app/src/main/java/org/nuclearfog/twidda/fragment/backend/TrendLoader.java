package org.nuclearfog.twidda.fragment.backend;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import org.nuclearfog.twidda.adapter.TrendAdapter;
import org.nuclearfog.twidda.backend.ErrorHandler;
import org.nuclearfog.twidda.backend.TwitterEngine;
import org.nuclearfog.twidda.backend.items.Trend;
import org.nuclearfog.twidda.database.DatabaseAdapter;
import org.nuclearfog.twidda.database.GlobalSettings;

import java.lang.ref.WeakReference;
import java.util.List;

import twitter4j.TwitterException;


public class TrendLoader  extends AsyncTask<Void, Void, Boolean> {

    public enum Mode {
        DB_TRND,
        LD_TRND
    }
    private Mode mode;
    private WeakReference<ViewGroup> ui;
    private TwitterException err;
    private TwitterEngine mTwitter;
    private DatabaseAdapter db;
    private TrendAdapter adapter;
    private List<Trend> trends;
    private int woeId;


    public TrendLoader(@NonNull ViewGroup root, Mode mode) {
        ui = new WeakReference<>(root);
        mTwitter = TwitterEngine.getInstance(root.getContext());
        db = new DatabaseAdapter(root.getContext());
        GlobalSettings settings = GlobalSettings.getInstance(root.getContext());
        woeId = settings.getWoeId();
        RecyclerView list = (RecyclerView)root.getChildAt(0);
        adapter = (TrendAdapter) list.getAdapter();
        this.mode = mode;
    }


    @Override
    protected void onPreExecute() {
        if(ui.get() == null)
            return;
        SwipeRefreshLayout reload = (SwipeRefreshLayout)ui.get();
        reload.setRefreshing(true);
    }


    @Override
    protected Boolean doInBackground(Void[] v) {
        try {
            switch(mode) {
                case DB_TRND:
                    trends = db.getTrends(woeId);
                    if(!trends.isEmpty())
                        break;

                case LD_TRND:
                    trends = mTwitter.getTrends(woeId);
                    db.storeTrends(trends, woeId);
                    break;
            }
        } catch(TwitterException err) {
            this.err = err;
            return false;
        } catch(Exception err) {
            return false;
        }
        return true;
    }


    @Override
    protected void onPostExecute(Boolean success) {
        if(ui.get() == null)
            return;

        if(success) {
            adapter.setData(trends);
            adapter.notifyDataSetChanged();
        } else {
            if(err != null)
                ErrorHandler.printError(ui.get().getContext(), err);
        }
        SwipeRefreshLayout reload = (SwipeRefreshLayout) ui.get();
        reload.setRefreshing(false);
    }


    @Override
    protected void onCancelled() {
        if(ui.get() == null)
            return;

        SwipeRefreshLayout reload = (SwipeRefreshLayout) ui.get();
        reload.setRefreshing(false);
    }
}
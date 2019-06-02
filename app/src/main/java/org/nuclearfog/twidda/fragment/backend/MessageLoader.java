package org.nuclearfog.twidda.fragment.backend;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.adapter.MessageAdapter;
import org.nuclearfog.twidda.backend.TwitterEngine;
import org.nuclearfog.twidda.backend.helper.ErrorHandler;
import org.nuclearfog.twidda.backend.items.Message;
import org.nuclearfog.twidda.database.DatabaseAdapter;

import java.lang.ref.WeakReference;
import java.util.List;

import twitter4j.TwitterException;


public class MessageLoader extends AsyncTask<Long, Void, Boolean> {

    public enum Mode {
        DB,
        LOAD,
        DEL
    }

    private Mode mode;
    private WeakReference<View> ui;
    private TwitterEngine mTwitter;
    private TwitterException err;
    private DatabaseAdapter db;
    private MessageAdapter adapter;
    private List<Message> messages;


    public MessageLoader(@NonNull View root, Mode mode) {
        ui = new WeakReference<>(root);
        RecyclerView rv = root.findViewById(R.id.fragment_list);
        adapter = (MessageAdapter) rv.getAdapter();
        mTwitter = TwitterEngine.getInstance(root.getContext());
        db = new DatabaseAdapter(root.getContext());
        this.mode = mode;
    }


    @Override
    protected void onPreExecute() {
        if (ui.get() == null)
            return;
        final SwipeRefreshLayout reload = ui.get().findViewById(R.id.fragment_reload);
        reload.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getStatus() != Status.FINISHED)
                    reload.setRefreshing(true);
            }
        }, 500);
    }


    @Override
    protected Boolean doInBackground(Long[] param) {
        long messageId = 0;
        try {
            switch (mode) {
                case DB:
                    messages = db.getMessages();
                    if (!messages.isEmpty())
                        break;

                case LOAD:
                    messages = mTwitter.getMessages();
                    db.storeMessage(messages);
                    break;

                case DEL:
                    messageId = param[0];
                    mTwitter.deleteMessage(messageId);
                    db.deleteDm(messageId);
                    messages = db.getMessages();
                    break;
            }
        } catch (TwitterException err) {
            if (err.getErrorCode() == 34) {
                db.deleteDm(messageId);
                messages = db.getMessages();
                this.err = err;
            }
            return false;
        } catch (Exception err) {
            if (err.getMessage() != null)
                Log.e("Status Loader", err.getMessage());
            return false;
        }
        return true;
    }


    @Override
    protected void onPostExecute(Boolean success) {
        if (ui.get() == null)
            return;
        if (messages != null) {
            adapter.setData(messages);
            adapter.notifyDataSetChanged();
        }
        SwipeRefreshLayout reload = ui.get().findViewById(R.id.fragment_reload);
        reload.setRefreshing(false);
        if (!success) {
            if (err != null)
                ErrorHandler.printError(ui.get().getContext(), err);
        }
    }


    @Override
    protected void onCancelled() {
        if (ui.get() == null)
            return;
        SwipeRefreshLayout reload = ui.get().findViewById(R.id.fragment_reload);
        reload.setRefreshing(false);
    }
}
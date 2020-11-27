package org.nuclearfog.twidda.backend;

import android.os.AsyncTask;

import androidx.annotation.Nullable;

import org.nuclearfog.twidda.backend.engine.EngineException;
import org.nuclearfog.twidda.backend.engine.TwitterEngine;
import org.nuclearfog.twidda.backend.holder.UserListList;
import org.nuclearfog.twidda.fragment.UserListFragment;

import java.lang.ref.WeakReference;


/**
 * Background task for downloading twitter lists created by a user
 *
 * @see UserListFragment
 */
public class TwitterListLoader extends AsyncTask<Long, Void, UserListList> {

    public static final long NO_CURSOR = -1;

    /**
     * Type of list to be loaded
     */
    public enum Type {
        /**
         * load userlists of an user
         */
        LOAD_USERLISTS,
        /**
         * load userlists the specified user is on
         */
        LOAD_MEMBERSHIPS
    }

    @Nullable
    private EngineException twException;
    private final WeakReference<UserListFragment> callback;
    private final TwitterEngine mTwitter;
    private final Type listType;

    private final long userId;
    private final String ownerName;

    /**
     * @param callback  callback to update information
     * @param listType  type of list to load
     * @param userId    ID of the userlist
     * @param ownerName alternative if user id is not defined
     */
    public TwitterListLoader(UserListFragment callback, Type listType, long userId, String ownerName) {
        super();
        mTwitter = TwitterEngine.getInstance(callback.getContext());
        this.callback = new WeakReference<>(callback);
        this.listType = listType;
        this.userId = userId;
        this.ownerName = ownerName;
    }


    @Override
    protected UserListList doInBackground(Long[] param) {
        try {
            switch (listType) {
                case LOAD_USERLISTS:
                    return mTwitter.getUserList(userId, ownerName, param[0]);

                case LOAD_MEMBERSHIPS:
                    return mTwitter.getUserListMemberships(userId, ownerName, param[0]);
            }
        } catch (EngineException twException) {
            this.twException = twException;
        }
        return null;
    }


    @Override
    protected void onPostExecute(UserListList result) {
        if (callback.get() != null) {
            if (result != null) {
                callback.get().setData(result);
            } else {
                callback.get().onError(twException);
            }
        }
    }
}
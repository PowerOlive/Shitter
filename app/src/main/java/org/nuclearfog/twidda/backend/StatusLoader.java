package org.nuclearfog.twidda.backend;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.backend.listitems.Tweet;
import org.nuclearfog.twidda.database.DatabaseAdapter;
import org.nuclearfog.twidda.database.ErrorLog;
import org.nuclearfog.twidda.viewadapter.TimelineRecycler;
import org.nuclearfog.twidda.window.SearchPage;
import org.nuclearfog.twidda.window.TweetDetail;
import org.nuclearfog.twidda.window.TweetPopup;
import org.nuclearfog.twidda.window.UserProfile;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import twitter4j.TwitterException;

public class StatusLoader extends AsyncTask<Long, Void, Long> implements View.OnClickListener {

    private static final long ERROR     =-1;
    public static final long RETWEET    = 0;
    public static final long FAVORITE   = 1;
    public static final long DELETE     = 2;
    public static final long LOAD_TWEET = 3;
    public static final long LOAD_REPLY = 4;
    public static final long LOAD_DB    = 5;
    private static final long IGNORE    = 6;

    private TwitterEngine mTwitter;
    private TimelineRecycler answerAdapter;
    private String usernameStr, scrNameStr, tweetStr, dateString;
    private String repliedUsername, apiName, retweeter;
    private String medialinks[], profile_pb;
    private String errorMessage = "";
    private boolean retweeted, favorited, toggleImg, verified;
    private boolean rtFlag = false;
    private long tweetReplyID,tweetID, userID, retweeterID, homeId;
    private int rtCount, favCount;
    private int highlight, font;

    private WeakReference<TweetDetail> ui;

    public StatusLoader(Context c) {
        mTwitter = TwitterEngine.getInstance(c);
        GlobalSettings settings = GlobalSettings.getInstance(c);
        font = settings.getFontColor();
        highlight = settings.getHighlightColor();
        toggleImg = settings.loadImages();
        homeId = settings.getUserId();
        ui = new WeakReference<>((TweetDetail)c);
        RecyclerView replyList = ui.get().findViewById(R.id.answer_list);
        answerAdapter = (TimelineRecycler) replyList.getAdapter();
        if(answerAdapter == null) {
            answerAdapter = new TimelineRecycler(ui.get());
            replyList.setAdapter(answerAdapter);
        }
    }


    /**
     * @param data [0] TWEET ID , [1] Mode
     */
    @Override
    protected Long doInBackground(Long... data) {
        tweetID = data[0];
        final long MODE = data[1];
        try {
            Tweet tweet;
            DatabaseAdapter database = new DatabaseAdapter(ui.get());

            if( MODE == LOAD_DB ) {
                tweet = database.getStatus(tweetID);
                List<Tweet> answers = database.getAnswers(tweetID);
                answerAdapter.setData(answers);
                answerAdapter.setColor(highlight, font);
                if(tweet == null)
                    return IGNORE; // NOT FOUND
            } else {
                tweet = mTwitter.getStatus(tweetID);
            }

            if (tweet.embedded != null) {
                retweeter = tweet.user.screenname;
                retweeterID = tweet.user.userID;
                tweet = tweet.embedded;
                tweetID = tweet.tweetID;
                rtFlag = true;
            }

            tweetReplyID = tweet.replyID;
            verified = tweet.user.isVerified;
            tweetStr = tweet.tweet;
            usernameStr = tweet.user.username;
            userID = tweet.user.userID;
            scrNameStr = tweet.user.screenname;
            apiName = formatString(tweet.source);
            rtCount = tweet.retweet;
            favCount = tweet.favorit;
            retweeted = tweet.retweeted;
            favorited = tweet.favorized;
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale.GERMANY);
            dateString = sdf.format(tweet.time);
            repliedUsername = tweet.replyName;
            profile_pb = tweet.user.profileImg + "_bigger";
            medialinks = tweet.media;

            if(MODE == RETWEET) {
                mTwitter.retweet(tweet);
                if(!retweeted) {
                    rtCount++;
                    retweeted = true;
                } else {
                    if(rtCount > 0)
                        rtCount--;
                    retweeted = false;
                    database.removeStatus(tweet.retweetId);
                }
            }
            else if(MODE == FAVORITE) {
                mTwitter.favorite(tweet);
                if(!favorited) {
                    favCount++;
                    favorited = true;
                } else {
                    if(favCount > 0)
                        favCount--;
                    favorited = false;
                    database.removeFavorite(tweetID,homeId);
                }
            }
            else if(MODE == LOAD_REPLY) {
                List<Tweet> answers;
                if(answerAdapter.getItemCount() > 0) {
                    long sinceId = answerAdapter.getItemId(0);
                    answers = mTwitter.getAnswers(scrNameStr, tweetID, sinceId);
                    answers.addAll(answerAdapter.getData());
                } else {
                    answers = mTwitter.getAnswers(scrNameStr, tweetID, tweetID);
                }
                answerAdapter.setData(answers);
                answerAdapter.setColor(highlight, font);
                if(answers.size() > 0 && database.containStatus(tweetID))
                    database.storeReplies(answers);
            }
            else if(MODE == DELETE) {
                mTwitter.deleteTweet(tweetID);
                database.removeStatus(tweetID);
            }
            if(MODE == LOAD_TWEET || MODE == RETWEET || MODE == FAVORITE) {
                if(database.containStatus(tweetID))
                    database.updateStatus(tweetID,rtCount,favCount,retweeted,favorited);
            }
        }
        catch(TwitterException e) {
            int err = e.getErrorCode();
            if(err == 144) {
                new DatabaseAdapter(ui.get()).removeStatus(tweetID);
                errorMessage = "Tweet nicht gefunden!\nID:"+tweetID;
            } else if(err == 420) {
                int retry = e.getRetryAfter();
                errorMessage = "Rate limit erreicht!\n Weiter in "+retry+" Sekunden";
            } else {
                errorMessage = e.getMessage();
                ErrorLog errorLog = new ErrorLog(ui.get());
                errorLog.add(errorMessage);
            }
            return ERROR;
        }
        catch(Exception err) {
            errorMessage = err.getMessage();
            ErrorLog errorLog = new ErrorLog(ui.get());
            errorLog.add(errorMessage);
            return ERROR;
        }
        return MODE;
    }

    @Override
    protected void onPostExecute(Long mode) {
        TweetDetail connect = ui.get();
        if(connect == null)
            return;

        if(mode == LOAD_TWEET ||mode == LOAD_DB) {
            TextView tweet = connect.findViewById(R.id.tweet_detailed);
            TextView username = connect.findViewById(R.id.usernamedetail);
            TextView scrName = connect.findViewById(R.id.scrnamedetail);
            TextView date = connect.findViewById(R.id.timedetail);
            TextView replyName = connect.findViewById(R.id.answer_reference_detail);
            TextView used_api = connect.findViewById(R.id.used_api);
            TextView txtRet = connect.findViewById(R.id.no_rt_detail);
            TextView txtFav = connect.findViewById(R.id.no_fav_detail);
            TextView txtAns = connect.findViewById(R.id.no_ans_detail);
            ImageView profile_img = connect.findViewById(R.id.profileimage_detail);

            tweet.setMovementMethod(LinkMovementMethod.getInstance());
            tweet.setText(highlight(tweetStr));
            username.setText(usernameStr);
            scrName.setText(scrNameStr);
            date.setText(dateString);
            used_api.setText(apiName);

            String ansStr = Integer.toString(answerAdapter.getItemCount());
            String favStr = Integer.toString(favCount);
            String rtStr = Integer.toString(rtCount);
            txtFav.setText(favStr);
            txtRet.setText(rtStr);
            txtAns.setText(ansStr);

            if(tweetReplyID > 0) {
                String reply = "antwort ";
                if(repliedUsername != null)
                    reply += '@'+repliedUsername;

                replyName.setText(reply);
                replyName.setVisibility(View.VISIBLE);
                replyName.setOnClickListener(this);
                replyName.setVisibility(View.VISIBLE);
            }
            if(rtFlag) {
                String retPrompt = "Retweet "+retweeter;
                TextView userRetweet = connect.findViewById(R.id.rt_info);
                userRetweet.setText(retPrompt);
                userRetweet.setOnClickListener(this);
                userRetweet.setVisibility(View.VISIBLE);
            }
            if(verified) {
                View tweet_verify = connect.findViewById(R.id.tweet_verify);
                tweet_verify.setVisibility(View.VISIBLE);
            }
            if(toggleImg) {
                Picasso.with(ui.get()).load(profile_pb).into(profile_img);
                if(medialinks != null && medialinks.length != 0) {
                    View mediabutton = connect.findViewById(R.id.image_attach);
                    mediabutton.setVisibility(View.VISIBLE);
                    mediabutton.setOnClickListener(this);
                }
            }
            Button retweetButton = connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = connect.findViewById(R.id.fav_button_detail);
            Button answer = connect.findViewById(R.id.answer_button);
            setIcons(favoriteButton, retweetButton);
            profile_img.setOnClickListener(this);
            answer.setOnClickListener(this);
        }
        else if(mode == RETWEET) {
            String toastMsg;
            Button retweetButton = connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = connect.findViewById(R.id.fav_button_detail);
            TextView txtRet = connect.findViewById(R.id.no_rt_detail);
            setIcons(favoriteButton, retweetButton);
            String rtStr = Integer.toString(rtCount);
            txtRet.setText(rtStr);
            if(retweeted) {
                toastMsg = "retweeted";
            } else {
                toastMsg = "retweet entfernt!";
            }
            Toast.makeText(ui.get(), toastMsg, Toast.LENGTH_SHORT).show();
        }
        else if(mode == FAVORITE) {
            String toastMsg;
            Button retweetButton = connect.findViewById(R.id.rt_button_detail);
            Button favoriteButton = connect.findViewById(R.id.fav_button_detail);
            TextView txtFav = connect.findViewById(R.id.no_fav_detail);
            setIcons(favoriteButton, retweetButton);
            String favStr = Integer.toString(favCount);
            txtFav.setText(favStr);
            if(favorited) {
                toastMsg = "zu favoriten hinzugefügt!";
            } else {
                toastMsg = "aus favoriten entfernt!";
            }
            Toast.makeText(ui.get(), toastMsg, Toast.LENGTH_SHORT).show();
        }
        else if(mode == LOAD_REPLY) {
            SwipeRefreshLayout ansReload = connect.findViewById(R.id.answer_reload);
            ansReload.setRefreshing(false);
            String ansStr = Integer.toString(answerAdapter.getItemCount());
            TextView txtAns = connect.findViewById(R.id.no_ans_detail);
            answerAdapter.notifyDataSetChanged();
            txtAns.setText(ansStr);
        }
        else if(mode == DELETE) {
            Toast.makeText(ui.get(), "Tweet gelöscht", Toast.LENGTH_LONG).show();
            ui.get().finish();
        }
        else if(mode == ERROR) {
            Toast.makeText(ui.get(),"Fehler beim Laden: "+errorMessage,Toast.LENGTH_LONG).show();
            SwipeRefreshLayout ansReload = connect.findViewById(R.id.answer_reload);
            if(ansReload.isRefreshing()) {
                ansReload.setRefreshing(false);
            }
        }
    }


    private String formatString(String input) {
        StringBuilder output = new StringBuilder("gesendet von: ");
        boolean openTag = false;
        for(int i = 0 ; i < input.length() ; i++){
            char current = input.charAt(i);
            if(current == '>' && !openTag){
                openTag = true;
            } else if(current == '<'){
                openTag = false;
            } else if(openTag) {
                output.append(current);
            }
        }
        return output.toString();
    }


    private Spannable highlight(String tweet) {
        Spannable sTweet = new SpannableStringBuilder(tweet);
        int start = 0;
        boolean marked = false;
        for(int i = 0 ; i < tweet.length() ; i++) {
            char current = tweet.charAt(i);
            switch(current){
                case '@':
                case '#':
                    start = i;
                    marked = true;
                    break;
                case '\'':
                case '\"':
                case '\n':
                case ')':
                case '(':
                case ':':
                case ' ':
                case '.':
                case ',':
                case '!':
                case '?':
                case '-':
                    if(marked && start != i-1) {
                        sTweet = spanning(sTweet, start, i);
                    }
                    marked = false;
                    break;
            }
        }
        if(marked && start != tweet.length()-1) {
            sTweet = spanning(sTweet, start, tweet.length());
        }
        return sTweet;
    }


    private Spannable spanning(Spannable sTweet, final int start, final int end) {
        sTweet.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                TextView tv = (TextView) widget;
                Spanned s = (Spanned) tv.getText();
                String search = s.subSequence(start, end).toString();
                Intent intent = new Intent(ui.get(), SearchPage.class);
                Bundle bundle = new Bundle();
                if(search.startsWith("#"))
                    bundle.putString("Addition", search);
                bundle.putString("search", search);
                intent.putExtras(bundle);
                ui.get().startActivity(intent);
            }
            @Override
            public void updateDrawState(TextPaint ds){
                ds.setColor(highlight);
                ds.setUnderlineText(false);
            }
        },start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sTweet;
    }


    private void setIcons(Button favoriteButton, Button retweetButton) {
        if(favorited) {
            favoriteButton.setBackgroundResource(R.drawable.favorite_enabled);
        }else {
            favoriteButton.setBackgroundResource(R.drawable.favorite);
        }
        if(retweeted) {
            retweetButton.setBackgroundResource(R.drawable.retweet_enabled);
        } else {
            retweetButton.setBackgroundResource(R.drawable.retweet);
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.profileimage_detail:
                Intent profile = new Intent(ui.get(), UserProfile.class);
                Bundle b = new Bundle();
                b.putLong("userID",userID);
                b.putString("username", scrNameStr);
                profile.putExtras(b);
                ui.get().startActivity(profile);
                break;

            case R.id.answer_reference_detail:
                Intent tweet = new Intent(ui.get(), TweetDetail.class);
                tweet.putExtra("tweetID",tweetReplyID);
                tweet.putExtra("username", '@'+repliedUsername);
                ui.get().startActivity(tweet);
                break;

            case R.id.answer_button:
                Intent tweetpop = new Intent(ui.get(), TweetPopup.class);
                Bundle ext = new Bundle();
                ext.putLong("TweetID", tweetID);
                ext.putString("Addition", scrNameStr);
                tweetpop.putExtras(ext);
                ui.get().startActivity(tweetpop);
                break;

            case R.id.image_attach:
                new ImagePopup(ui.get()).execute(medialinks);
                break;

            case R.id.rt_info:
                Intent retweetProfile = new Intent(ui.get(), UserProfile.class);
                Bundle extras = new Bundle();
                extras.putLong("userID",retweeterID);
                extras.putString("username", retweeter);
                retweetProfile.putExtras(extras);
                ui.get().startActivity(retweetProfile);
                break;
        }
    }
}
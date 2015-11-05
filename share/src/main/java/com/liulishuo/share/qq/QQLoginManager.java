package com.liulishuo.share.qq;

import com.liulishuo.share.ShareBlock;
import com.liulishuo.share.base.AuthUserInfo;
import com.liulishuo.share.base.login.ILoginManager;
import com.liulishuo.share.base.login.LoginListener;
import com.liulishuo.share.base.login.UserInfoListener;
import com.liulishuo.share.util.HttpUtil;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

/**
 * Created by echo on 5/19/15.
 */
public class QQLoginManager implements ILoginManager {

    private static Tencent mTencent;

    private static IUiListener mLoginUiListener;

    private static long mExpires = -1;

    @Override
    public void login(@NonNull Activity activity, final @NonNull LoginListener loginListener) {
        String appId = ShareBlock.getInstance().QQAppId;
        if (TextUtils.isEmpty(appId)) {
            throw new NullPointerException("请通过shareBlock初始化appId");
        }
        mLoginUiListener = new IUiListener() {

            @Override
            public void onComplete(Object object) {
                initOpenidAndToken((JSONObject) object); // 初始化id和access token
                loginListener.onSuccess(mTencent.getAccessToken(), mTencent.getOpenId(), mExpires, object.toString());
            }

            @Override
            public void onError(UiError uiError) {
                loginListener.onError(uiError.errorCode + " - " + uiError.errorMessage + " - " + uiError.errorDetail);
            }

            @Override
            public void onCancel() {
                loginListener.onCancel();
            }
        };
        mTencent = Tencent.createInstance(appId, activity);
        // 启动activity后，应该立刻调用{sendLoginMsg}方法
        activity.startActivity(new Intent(activity, SL_QQLoginActivity.class));
    }

    protected static void sendLoginMsg(Activity activity) {
        if (!mTencent.isSessionValid()) {
            mTencent.login(activity, ShareBlock.getInstance().QQScope, mLoginUiListener);
        } else {
            mTencent.logout(activity);
        }
    }

    protected static void handlerOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (mLoginUiListener != null) {
            Tencent.onActivityResultData(requestCode, resultCode, data, mLoginUiListener);
        }
    }

    private void initOpenidAndToken(@NonNull JSONObject jsonObject) {
        try {
            String openId = jsonObject.getString(com.tencent.connect.common.Constants.PARAM_OPEN_ID);
            String token = jsonObject.getString(com.tencent.connect.common.Constants.PARAM_ACCESS_TOKEN);
            String expires = jsonObject.getString(com.tencent.connect.common.Constants.PARAM_EXPIRES_IN);
            if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(expires) && !TextUtils.isEmpty(openId)) {
                mExpires = Long.valueOf(expires);
                mTencent.setOpenId(openId);
                mTencent.setAccessToken(token, "1"); // 这里应该填入expires，但会出现bug 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Tencent getTencent() {
        return mTencent;
    }

    // ---------------------------------- 得到用户信息 -------------------------------------

    @Override
    public void getUserInformation(@NonNull String accessToken, @NonNull String userId, @Nullable UserInfoListener listener) {
        getUserInfo(accessToken, userId, listener);
    }

    /**
     * 得到用户的信息，是一个静态的基础方法
     * @see "http://wiki.open.qq.com/wiki/website/get_simple_userinfo"
     */
    public static void getUserInfo(@NonNull final String accessToken, @NonNull final String userId, 
            @Nullable final UserInfoListener listener) {
        
        new AsyncTask<Void, Void, AuthUserInfo>() {

            @Override
            protected AuthUserInfo doInBackground(Void... params) {
                String respStr = HttpUtil.doGet(
                        "https://graph.qq.com/user/get_simple_userinfo"
                                + "?access_token=" + accessToken
                                + "&oauth_consumer_key=" + ShareBlock.getInstance().QQAppId
                                + "&openid=" + userId
                                + "&format=json");
                if (respStr == null) {
                    return null;
                }
                
                AuthUserInfo userInfo;
                try {
                    userInfo = new AuthUserInfo();
                    JSONObject jsonObject = new JSONObject(respStr);
                    userInfo.nickName = jsonObject.getString("nickname");
                    userInfo.sex = jsonObject.getString("gender");
                    userInfo.headImgUrl = jsonObject.getString("figureurl_qq_1");
                    userInfo.userId = userId;
                } catch (JSONException e) {
                    userInfo = null;
                    e.printStackTrace();
                }
                return userInfo;
            }

            @Override
            protected void onPostExecute(AuthUserInfo userInfo) {
                super.onPostExecute(userInfo);
                if (listener != null) {
                    if (userInfo != null) {
                        listener.onSuccess(userInfo);
                    } else {
                        listener.onError("用户信息解析异常");
                    }
                }
            }
        }.execute();
    }

}



package com.liulishuo.share.qq;

import com.liulishuo.share.ShareBlock;
import com.liulishuo.share.ShareManager;
import com.liulishuo.share.content.ShareContent;
import com.liulishuo.share.type.ContentType;
import com.tencent.connect.share.QQShare;
import com.tencent.connect.share.QzoneShare;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static com.liulishuo.share.ShareBlock.getInstance;

/**
 * @author Jack Tony
 * @date 2015/10/26
 */
public class SL_QQShareActivity extends Activity {

    public static final String KEY_TO_FRIEND = "key_to_friend";

    private boolean isToFriend;

    private IUiListener uiListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 无论是否是恢复的activity，都要先初始化监听器，否则开启不保留活动后监听器对象就是null
        uiListener = initListener(ShareManager.listener);
        isToFriend = getIntent().getBooleanExtra(KEY_TO_FRIEND, true);

        if (savedInstanceState == null) { // 防止不保留活动情况下activity被重置后直接进行操作的情况
            ShareContent shareContent = getIntent().getParcelableExtra(ShareManager.KEY_CONTENT);
            doShare(shareContent);
        }
    }

    /**
     * 解析分享的结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (uiListener != null && data != null) {
            Tencent.handleResultData(data, uiListener);
        }
        finish();
    }

    private void doShare(ShareContent shareContent) {
        String appId = ShareBlock.getInstance().qqAppId;
        if (TextUtils.isEmpty(appId)) {
            throw new NullPointerException("请通过shareBlock初始化QQAppId");
        }
        Tencent tencent = Tencent.createInstance(appId, getApplicationContext());
        if (isToFriend) {
            Bundle params = getShareToQQBundle(shareContent);
            tencent.shareToQQ(this, params, uiListener);
        } else {
            Bundle params = getShareToQZoneBundle(shareContent);
            tencent.shareToQzone(this, params, uiListener);
        }
    }

    private IUiListener initListener(final ShareManager.ShareStateListener listener) {
        return new IUiListener() {

            @Override
            public void onComplete(Object response) {
                if (listener != null) {
                    listener.onSuccess();
                }
            }

            @Override
            public void onCancel() {
                if (listener != null) {
                    listener.onCancel();
                }
            }

            @Override
            public void onError(UiError e) {
                if (listener != null) {
                    listener.onError(e.errorCode + " - " + e.errorMessage + " - " + e.errorDetail);
                }
            }
        };
    }

    private
    @NonNull
    Bundle getShareToQQBundle(ShareContent shareContent) {
        Bundle bundle;
        switch (shareContent.getType()) {
            case ContentType.TEXT:
                // 纯文字
                // 文档中说： "本接口支持3种模式，每种模式的参数设置不同"，这三种模式中不包含纯文本
                Log.e("Share by QQ", "QQ目前不支持分享纯文本信息");
                finish();
                bundle = getTextObj();
                break;
            case ContentType.PIC:
                // 纯图片
                bundle = getImageObj(shareContent);
                break;
            case ContentType.WEBPAGE:
                // 网页
                bundle = getWebPageObj();
                break;
            case ContentType.MUSIC:
                // 音乐
                bundle = getMusicObj(shareContent);
                break;
            default:
                throw new UnsupportedOperationException("不支持的分享内容");
        }
        return getQQFriendParams(bundle, shareContent);
    }

    /**
     * @see "http://wiki.open.qq.com/wiki/mobile/API%E8%B0%83%E7%94%A8%E8%AF%B4%E6%98%8E#1.13_.E5.88.86.E4.BA.AB.E6.B6.88.E6.81.AF.E5.88.B0QQ.EF.BC.88.E6.97.A0.E9.9C.80QQ.E7.99.BB.E5.BD.95.EF.BC.89"
     * QQShare.PARAM_TITLE 	        必填 	String 	分享的标题, 最长30个字符。
     * QQShare.SHARE_TO_QQ_KEY_TYPE 	必填 	Int 	分享的类型。图文分享(普通分享)填Tencent.SHARE_TO_QQ_TYPE_DEFAULT
     * QQShare.PARAM_TARGET_URL 	必填 	String 	这条分享消息被好友点击后的跳转URL。
     * QQShare.PARAM_SUMMARY 	        可选 	String 	分享的消息摘要，最长40个字。
     * QQShare.SHARE_TO_QQ_IMAGE_URL 	可选 	String 	分享图片的URL或者本地路径
     * QQShare.SHARE_TO_QQ_APP_NAME 	可选 	String 	手Q客户端顶部，替换“返回”按钮文字，如果为空，用返回代替
     * QQShare.SHARE_TO_QQ_EXT_INT 	可选 	Int 	分享额外选项，两种类型可选（默认是不隐藏分享到QZone按钮且不自动打开分享到QZone的对话框）：
     * QQShare.SHARE_TO_QQ_FLAG_QZONE_AUTO_OPEN，分享时自动打开分享到QZone的对话框。
     * QQShare.SHARE_TO_QQ_FLAG_QZONE_ITEM_HIDE，分享时隐藏分享到QZone按钮
     *
     * target必须是真实的可跳转链接才能跳到QQ = =！
     *
     * 发送给QQ好友
     */
    private Bundle getQQFriendParams(Bundle params, ShareContent shareContent) {
        params.putString(QQShare.SHARE_TO_QQ_TITLE, shareContent.getTitle()); // 标题
        params.putString(QQShare.SHARE_TO_QQ_SUMMARY, shareContent.getSummary()); // 描述
        params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, shareContent.getURL()); // 这条分享消息被好友点击后的跳转URL
        if (shareContent.getImageBmpBytes() != null) {
            params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, saveByteArr(shareContent.getImageBmpBytes())); // 分享图片的URL或者本地路径 (可选)
        }
        params.putString(QQShare.SHARE_TO_QQ_APP_NAME, ShareBlock.getInstance().appName); // 手Q客户端顶部，替换“返回”按钮文字，如果为空，用返回代替 (可选)
        return params;
    }

    private Bundle getTextObj() {
        final Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
        return params;
    }

    private Bundle getImageObj(ShareContent shareContent) {
        final Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE); // 标识分享的是纯图片 (必填)
        params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, saveByteArr(shareContent.getImageBmpBytes())); // 信息中的图片 (必填)
        params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, QQShare.SHARE_TO_QQ_FLAG_QZONE_AUTO_OPEN); // (可选)
        return params;
    }

    private Bundle getWebPageObj() {
        final Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
        return params;
    }

    private Bundle getMusicObj(ShareContent shareContent) {
        final Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_AUDIO); // 标识分享的是音乐 (必填)
        params.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, shareContent.getMusicUrl()); //  音乐链接 (必填)
        return params;
    }

    /**
     * 分享到QQ空间（目前支持图文分享）
     *
     * @see "http://wiki.open.qq.com/wiki/Android_API%E8%B0%83%E7%94%A8%E8%AF%B4%E6%98%8E#1.14_.E5.88.86.E4.BA.AB.E5.88.B0QQ.E7.A9.BA.E9.97.B4.EF.BC.88.E6.97.A0.E9.9C.80QQ.E7.99.BB.E5.BD.95.EF.BC.89"
     * QzoneShare.SHARE_TO_QQ_KEY_TYPE 	    选填      Int 	SHARE_TO_QZONE_TYPE_IMAGE_TEXT（图文）
     * QzoneShare.SHARE_TO_QQ_TITLE 	    必填      Int 	分享的标题，最多200个字符。
     * QzoneShare.SHARE_TO_QQ_SUMMARY 	    选填      String 	分享的摘要，最多600字符。
     * QzoneShare.SHARE_TO_QQ_TARGET_URL    必填      String 	跳转URL，URL字符串。
     * QzoneShare.SHARE_TO_QQ_IMAGE_URL     选填      String     图片链接ArrayList
     *
     * 注意:QZone接口暂不支持发送多张图片的能力，若传入多张图片，则会自动选入第一张图片作为预览图。多图的能力将会在以后支持。
     *
     * 如果分享的图片url是本地的图片地址那么在分享时会显示图片，如果分享的是图片的网址，那么就不会在分享时显示图片
     */
    private Bundle getShareToQZoneBundle(ShareContent shareContent) {
        Bundle params = new Bundle();
        params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT);
        String title = shareContent.getTitle();
        if (title == null) {
            // 如果没title，说明就是分享的纯文字、纯图片
            Log.e("Share by qq zone", "QQ空间目前只支持分享图文信息");
            finish();
        }
        params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title); // 标题
        params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY, shareContent.getSummary()); // 描述
        params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL, shareContent.getURL()); // 点击后跳转的url

        // 分享的图片, 以ArrayList<String>的类型传入，以便支持多张图片 （注：图片最多支持9张图片，多余的图片会被丢弃）。
        if (shareContent.getImageBmpBytes() != null) {
            ArrayList<String> imageUrls = new ArrayList<>(); // 图片的ArrayList
            imageUrls.add(saveByteArr(shareContent.getImageBmpBytes()));
            params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL, imageUrls);
        }
        return params;
    }

    private String saveByteArr(@NonNull byte[] bytes) {
        if (getInstance().pathTemp == null) {
            throw new NullPointerException("请先调用shareBlock的initSharePicFile(Application application)方法");
        }

        String imagePath = getInstance().pathTemp  + "sharePic_temp";
        try {
            FileOutputStream fos = new FileOutputStream(imagePath);
            fos.write(bytes);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imagePath;
    }
}

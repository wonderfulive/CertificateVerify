package com.phoenix.certificateverify;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Created by LiChengFeng on 2019/4/30 12:13.
 */
public class OkHttpUtils {
    private static volatile OkHttpUtils sInstance;
    private volatile OkHttpClient mOkHttpClient;

    private OkHttpUtils() {

    }

    public static OkHttpUtils getInstance() {
        if (sInstance == null) {
            synchronized (OkHttpUtils.class) {
                if (sInstance == null) {
                    sInstance = new OkHttpUtils();
                }
            }
        }
        return sInstance;
    }

    public OkHttpClient getOkHttpClient(Context context) {
        if (mOkHttpClient == null) {
            synchronized (OkHttpClient.class) {
                if (mOkHttpClient == null) {
                    mOkHttpClient = createOkHttpClient(context.getApplicationContext());
                }
            }
        }
        return mOkHttpClient;
    }

    private OkHttpClient createOkHttpClient(Context context) {
        HttpsUtils httpsUtils = new HttpsUtils();
        HttpsUtils.SSLParam sslParam = null;
        try {
//        sslParam = httpsUtils.createSSLSocketFactory(null, null, null);
//            sslParam = httpsUtils.createSSLSocketFactory(null,null,
//                    new InputStream[]{context.getAssets().open("lcf_server.cer"),
//                            context.getAssets().open("baidu.x509.cer")});
            sslParam = httpsUtils.createSSLSocketFactory(context.getAssets().open("lcf_client.bks"),
                    "123456", new InputStream[]{context.getAssets().open("lcf_server.cer")});
        } catch (IOException e) {
            e.printStackTrace();
        }
        SSLSocketFactory sslSocketFactory = sslParam != null ? sslParam.sslSocketFactory : httpsUtils.getDefaultSSlSocketFacotry();
        X509TrustManager x509TrustManager = sslParam != null ? sslParam.trustManager : httpsUtils.getDefaultTrustManager();
        return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, x509TrustManager)
                .hostnameVerifier(httpsUtils.getDefaultHostnameVerifier())
                .build();
    }
}

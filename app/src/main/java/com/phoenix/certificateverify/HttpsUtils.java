package com.phoenix.certificateverify;

import android.icu.util.ChineseCalendar;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Created by LiChengFeng on 2019/4/30 15:00.
 */
public class HttpsUtils {
    /**
     * 该方法可根据传入参数不同，从而生成不同的验证效果
     * 1.参数全为null，则信任所有证书
     * 2.serverCertificates不为null，则只传入的证书对应的服务端可以通信
     * 2.所有参数均不为null，则可以进行双向验证，前提是服务端也支持，若服务端不支持双向验证仍可通信
     * 备注：
     * 1.如果服务器使用了自签名证书则，客户端必须加入信任，因为系统默认只信任CA办法的证书
     * 2.如果客户端只信任了自签名证书，会导致CA颁发的证书不被信任，
     * 这时候为了信任CA证书，需要自定义TrustManager,例如该类中的MyTrustManager，
     * 并且需要在构造该类的时候将已获取的自签名证书的trustManager作为参数传入,
     * 详情可参考MyTrustManager类
     * 3.若服务端开启双向认证，客户端必须配置私钥，否则无法通信
     * 4.客户端做证书验证还可以防止抓包工具抓包
     *
     * @param clientSecretKey    客户端自己保存的密钥
     * @param secretKeyPassword  客户端密钥密码
     * @param serverCertificates 服务端提供的证书
     * @return
     */
    public SSLParam createSSLSocketFactory(InputStream clientSecretKey, String secretKeyPassword, InputStream[] serverCertificates) {
        SSLParam sslParam = new SSLParam();
        TrustManager[] trustManagers = trustManagerForCertificates(serverCertificates);
        KeyManager[] keyManagers = keyManagerForSecretKey(clientSecretKey, secretKeyPassword);
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (trustManagers == null) {
                trustManagers = new TrustManager[]{new UnsafeTrustManager()};
            } else {
                trustManagers = new TrustManager[]{new MyTrustManager(selectTrustManager(trustManagers))};
            }
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            sslParam.sslSocketFactory = sslContext.getSocketFactory();
            sslParam.trustManager = selectTrustManager(trustManagers);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return sslParam;
    }

    //读取客户端私钥，生成密钥管理器
    private KeyManager[] keyManagerForSecretKey(InputStream clientSecretKey, String secretKeyPassword) {
        if (clientSecretKey == null) {
            return null;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] password = TextUtils.isEmpty(secretKeyPassword) ? null : secretKeyPassword.toCharArray();
            keyStore.load(clientSecretKey, password);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            return keyManagerFactory.getKeyManagers();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSecretKey.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    //读取服务端证书，生成信任管理器
    private TrustManager[] trustManagerForCertificates(InputStream[] certificates) {
        if (certificates == null || certificates.length == 0) {
            return null;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            int index = 0;
            for (InputStream in : certificates) {
                Certificate certificate = certificateFactory.generateCertificate(in);
                String certificateAlias = Integer.toString(index++);
                keyStore.setCertificateEntry(certificateAlias, certificate);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            return trustManagerFactory.getTrustManagers();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (InputStream in : certificates) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        return null;
    }

    private static X509TrustManager selectTrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        return null;
    }
    //默认信任所有证书
    public SSLSocketFactory getDefaultSSlSocketFacotry() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new UnsafeTrustManager()}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }
    //默认信任所有证书
    public X509TrustManager getDefaultTrustManager() {
        return new UnsafeTrustManager();
    }
    //默认信任所有host
    public HostnameVerifier getDefaultHostnameVerifier() {
        return new UnsafeHostnameVerify();
    }


    //信任所有证书
    private static class UnsafeTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static class MyTrustManager implements X509TrustManager {
        private X509TrustManager mDefaultTrustManager;
        private X509TrustManager mLocalTrustManager;

        public MyTrustManager(X509TrustManager trustManager) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                mDefaultTrustManager = selectTrustManager(trustManagerFactory.getTrustManagers());
                this.mLocalTrustManager = trustManager;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                if (mDefaultTrustManager != null) {
                    mDefaultTrustManager.checkServerTrusted(chain, authType);
                }
            } catch (CertificateException e) {
                if (mLocalTrustManager != null) {
                    mLocalTrustManager.checkServerTrusted(chain, authType);
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    //信任所有host
    public static class UnsafeHostnameVerify implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    //输出类，用于设置okhttp信息
    public static class SSLParam {
        X509TrustManager trustManager;
        SSLSocketFactory sslSocketFactory;
    }
}

package com.github.coversocks.test;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.http.SslError;
import android.os.Bundle;

import com.github.coversocks.CoverService;
import com.github.coversocks.NetVpnService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;

import android.os.IBinder;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Date;


public class MainActivity extends AppCompatActivity {

    CoverService mServer;
    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServer = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CoverService.LocalBinder mLocalBinder = (CoverService.LocalBinder) service;
            mServer = mLocalBinder.getServerInstance();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(runner2).start();
            }
        });
        FloatingActionButton fab2 = findViewById(R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(resolver).start();
            }
        });
        FloatingActionButton fab3 = findViewById(R.id.fab3);
//        fab3.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                WebView wview = findViewById(R.id.wview);
//                wview.setWebViewClient(new WebViewClient() {
//                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
//                        System.out.println(description);
//                    }
//
//                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
//                        handler.proceed();
//                        System.out.println(error);
//                    }
//
//                });
//                wview.loadUrl("https://m.jd.com");
//
//            }
//        });
//        toolbar.setTitle("start");
        CoverService.hello();
    }

    public void vpnStart(View v) {
        AppCompatButton button = (AppCompatButton) v;
        if (mServer.isRunning()) {
            button.setText("start");
            mServer.stop();
        } else {
            button.setText("stop");
            Intent intent = CoverService.prepare(getApplicationContext());
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        }
    }

    public void changeMode(View v) {
        AppCompatButton button = (AppCompatButton) v;
        if (button.getText() == "Global") {
            mServer.changeMode("auto");
            button.setText("Auto");
        } else {
            mServer.changeMode("global");
            button.setText("Global");
        }
    }

    protected void onStart() {
        super.onStart();
        Intent mIntent = new Intent(this, CoverService.class);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mServer != null) {
            unbindService(mConnection);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, CoverService.class);
            startService(intent);
        }
    }

    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    private Runnable runner2 = new Runnable() {
        @Override
        public void run() {
            try {
                for (int i = 0; i < 100; i++) {
                    long begin = new Date().getTime();
//                    CoverService.testWeb("https://m.360buyimg.com/mobilecms/s376x240_jfs/t1/49601/16/12206/115887/5d91b4d5E34709952/aba2bcb4855e6e52.png!q70.jpg.dpg", "bc76f6f4e741d930c7ae206fc6f57f398a4fb841");
//                    CoverService.testWeb("https://wq.360buyimg.com/wxsq_project/portal/m_jd_index/js/index.ab7a0b5b.js");
//                    CoverService.testWeb("https://wl.jd.com/unify.min.js", "4017ca2173c07a82f758052e495b21d1f8952722");
                    CoverService.testWeb("https://www.google.com.hk/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png",
                            "26f471f6ebe3b11557506f6ae96156e0a3852e5b");
//                    CoverService.testWeb("https://wq.360buyimg.com/js/common/dest/wq.imk.downloadAppPlugin.min.js?v=1.0.7");
                    long used = new Date().getTime();
                    System.out.println("done by " + used + " used");
                }
//                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable runner = new Runnable() {
        @Override
        public void run() {
            try {
                for (int i = 0; i < 1; i++) {
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
//                URL url = new URL("http://192.168.1.4/F%3A/BaiduNetdiskDownload/%E4%BA%A7%E5%93%81%E7%BB%8F%E7%90%86%E6%95%99%E7%A8%8B%E9%83%A8%E5%88%86/01%E3%80%81%E3%80%90%E6%8E%A8%E8%8D%90%E3%80%91%E4%BA%A7%E5%93%81%E7%BB%8F%E7%90%86%E6%8E%A8%E8%8D%90%E5%AD%A6%E4%B9%A0%E3%80%902019%E3%80%91/%E3%80%8A90%E5%A4%A9%E4%BA%A7%E5%93%81%E7%BB%8F%E7%90%86%E5%AE%9E%E6%88%9824%E6%9C%9F%E3%80%8B/30%E5%B8%82%E5%9C%BA%E4%B8%8E%E7%AB%9E%E5%93%81%E5%88%86%E6%9E%90/%EF%BC%881%EF%BC%89%E8%A7%86%E9%A2%91%E6%95%99%E5%AD%A6/(1)%20%E5%B8%82%E5%9C%BA%E5%88%86%E6%9E%90.mp4");
//                URL url = new URL("http://192.168.1.4/D%3A/temp/Snipaste_2020-03-15_22-22-53.png");
                    URL url = new URL("https://www.kuxiao.cn/config-dy/config-dy.js");
//                    URL url = new URL("https://www.baidu.com");
//                Socket socket = new Socket("219.135.99.111", 80);
//                OutputStream output = socket.getOutputStream();
//                output.write("abcxxx".getBytes());
//                output.flush();
                    URLConnection con = url.openConnection();
                    InputStream input = con.getInputStream();
                    byte[] buf = new byte[1024];
                    long begin = new Date().getTime();
                    int n = 0;
                    int totlal = 0;
                    File f = new File(getFilesDir(), "min.js");
                    if (f.exists()) {
                        f.delete();
                    }
                    OutputStream out = new FileOutputStream(f);
                    while ((n = input.read(buf, 0, 1024)) > 0) {
                        totlal += n;
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                    out.close();
                    long used = new Date().getTime() - begin;
//                    String response = new String(buf, 0, totlal, "UTF-8");
                    byte[] sha1hash = md.digest();
                    System.out.println("back--->done--->" + totlal + "," + +used + ", sha1:" + convertToHex(sha1hash));
                    System.out.println("out-->" + f.getAbsolutePath());
//                    System.out.println(response);
                }
//                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable multiTester = new Runnable() {
        @Override
        public void run() {
            try {
//                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
//                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//                        return null;
//                    }
//
//                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
//                    }
//
//                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
//                    }
//                }
//                };
//
//                // Install the all-trusting trust manager
//                SSLContext sc = SSLContext.getInstance("SSL");
//                sc.init(null, trustAllCerts, new java.security.SecureRandom());
//                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//
//                // Create all-trusting host name verifier
//                HostnameVerifier allHostsValid = new HostnameVerifier() {
//                    public boolean verify(String hostname, SSLSession session) {
//                        return true;
//                    }
//                };
//
//                // Install the all-trusting host verifier
//                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

                for (int i = 0; i < 100; i++) {
                    String data = "abc" + i * i * i;
                    URL url = new URL("https://192.168.1.50:8070/test?data=" + data);
                    URLConnection con = url.openConnection();
                    InputStream input = con.getInputStream();
                    byte[] buf = new byte[1024];
                    long begin = new Date().getTime();
                    int n = 0;
                    int totlal = 0;
                    while ((n = input.read(buf, n, 1024 - n)) > 0) {
                        totlal += n;
                    }
                    long used = new Date().getTime() - begin;
                    String response = new String(buf, 0, totlal, "UTF-8");
                    if (!data.equals(response)) {
                        throw new Exception("expect " + data + ", but " + response);
                    }
                    input.close();
                    System.out.println("done--->" + used);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void resolver(String hostname) throws UnknownHostException {
        System.out.println("start resolve " + hostname);
        long begin, used;
        InetAddress address;
        begin = new Date().getTime();
        address = InetAddress.getByName(hostname);
        used = new Date().getTime() - begin;
        System.out.println(address.getHostAddress() + "   " + used);
    }

    private Runnable resolver = new Runnable() {
        @Override
        public void run() {
            try {
                resolver("www.baidu.com");
                resolver("www.kuxiao.cn");
                resolver("www.google.com");
                resolver("www.youtube.com");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    };

}

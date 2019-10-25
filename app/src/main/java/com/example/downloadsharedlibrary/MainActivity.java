package com.example.downloadsharedlibrary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String[] request = null;
    private File downloadedfile = null;
    private TextView textView = null;

    private GetFileAsync getFileAsync = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text);
        request = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        boolean allGranted = true;
        for (String s : request) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
        }
        if (!allGranted) {
            requestPermissions(request, 0x02);
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        findViewById(R.id.fab).setOnClickListener(this);
        getFile();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0x02) {
            boolean denied = false;
            for (int i : grantResults) {
                if (i == PackageManager.PERMISSION_DENIED) {
                    denied = true;
                }
            }
            if (denied) {
                requestPermissions(request, 0x01);
                return;
            }
            getFile();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab) {
            testLibrary();
        }
    }

    private void getFile() {
        if (getFileAsync != null) {
            return;
        }
        getFileAsync = new GetFileAsync(this);
        getFileAsync.execute("libcrypto.so");
    }

    private void testLibrary() {
        if (downloadedfile == null) {
            updateText("Error reading file");
            return;
        }

        try {
            cryptoJNI cjni = new cryptoJNI(downloadedfile.getPath());
            updateText("Version: " + cjni.OpenSSL_version_num());
            updateText("Version type: " + cjni.OpenSSL_version(3));
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TAG", " Error: " + e.getMessage());
        }
    }

    private void updateText(final String s) {
        String currentText = String.valueOf(textView.getText());
        runOnUiThread(() -> textView.setText(String.format("%s\n%s\n", currentText, s)));
    }


    private static class GetFileAsync extends AsyncTask<String, String, byte[]> {

        private AtomicReference<MainActivity> activity = new AtomicReference<>();

        private GetFileAsync(MainActivity activity) {
            this.activity.set(activity);
        }

        @Override
        protected void onPreExecute() {
            this.activity.get().updateText("Downloading file from server");
        }

        @Override
        protected void onProgressUpdate(String... s) {
            this.activity.get().updateText(s[0]);
        }

        @Override
        protected byte[] doInBackground(String... args) {
            try {
                if (args[0] == null) {
                    return null;
                }

                HostnameVerifier hostnameVerifier = (s, sslSession) -> {
                    if ("10.10.3.104".equals(s)) {
                        return true;
                    }
                    return HttpsURLConnection.getDefaultHostnameVerifier().verify(s, sslSession);
                };

                URL url = new URL("https://10.10.3.104:6001");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                publishProgress("Establishing connection...");
                conn.setSSLSocketFactory(this.activity.get().getSocketFactory());
                conn.setHostnameVerifier(hostnameVerifier);

                conn.setDoOutput(true);
                //conn.setDoInput(true);
                conn.connect();
                publishProgress("Connected!");

                String data = URLEncoder.encode("filename", "UTF-8")
                        + "=" + URLEncoder.encode(args[0], "UTF-8");

                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                writer.write(data);
                writer.flush();
                writer.close();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                InputStream responseStream = conn.getInputStream();

                publishProgress("Downloading...");
                byte[] byteBuffer = new byte[256];
                int bufferLength;

                while ((bufferLength = responseStream.read(byteBuffer, 0, 256)) > 0) {
                    bos.write(byteBuffer, 0, bufferLength);
                }

                responseStream.close();
                publishProgress("Downloaded.");
                return bos.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                publishProgress("Error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(byte[] b) {
            this.activity.get().getFileAsync = null;
            if (b != null) {
                writeFile(b);
            } else {
                Log.e("TAG", "onResponse is null");
            }
        }

        private void writeFile(byte[] b) {
            try {
                this.activity.get().downloadedfile = new File(this.activity.get().getFilesDir(), "libcrypto_1.so");
                FileOutputStream fos = new FileOutputStream(this.activity.get().downloadedfile);
                fos.write(b);
                fos.close();
                this.activity.get().updateText("Saved file to: " + this.activity.get().downloadedfile.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private SSLSocketFactory getSocketFactory() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        InputStream is = getResources().openRawResource(R.raw.cert);
        Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(is);
        is.close();

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }
}

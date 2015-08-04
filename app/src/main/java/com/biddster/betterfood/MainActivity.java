package com.biddster.betterfood;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private final Set<String> allowedHosts = newHashSet("www.bbcgoodfood.com", "ajax.googleapis.com", "code.jquery.com");
    private WebView webView;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_PROGRESS);
//        requestWindowFeature(Window.FEATURE_PROGRESS);
        setProgressBarVisibility(true);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {

            public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
                try {
                    final URL aUrl = new URL(url);
                    if (allowedHosts.contains(aUrl.getHost())) {
                        return super.shouldInterceptRequest(view, url);
                    }
                    Log.d("NETWORK", "Ignoring: " + url);
                    return new WebResourceResponse("text/html", "utf-8", null);
                } catch (final MalformedURLException e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(final WebView view, final int progress) {
                // Activities and WebViews measure progress with different scales.
                // The progress meter will automatically disappear when we reach 100%
                Log.d("NETWORK", "Progress: " + (progress * 100));
//                MainActivity.this.setProgress(progress * 100);
                getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress * 100);
            }
        });
        goHome();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        //noinspection SimplifiableIfStatement
        if (item.getItemId() == R.id.action_home) {
            goHome();
            return true;
        } else if (item.getItemId() == R.id.menu_item_share) {
            final Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, webView.getTitle());
            i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
            startActivity(Intent.createChooser(i, "Share - " + webView.getTitle()));
        }
        return super.onOptionsItemSelected(item);
    }

    private void goHome() {
        webView.loadUrl("http://www.bbcgoodfood.com");
    }

    private Set<String> newHashSet(final String... entries) {
        final HashSet<String> set = new HashSet<>();
        Collections.addAll(set, entries);
        return set;
    }
}

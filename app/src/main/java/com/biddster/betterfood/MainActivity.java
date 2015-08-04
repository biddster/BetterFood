package com.biddster.betterfood;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.JavascriptInterface;
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
    private final Set<String> ignoredHosts = newHashSet(
            "d3c3cq33003psk.cloudfront.net",
            "widget.bbclogin.com",
            "pq-direct.revsci.net",
            "cdn.krxd.net",
            "www.googletagservices.com",
            "widgets.outbrain.com",
            "b.scorecardresearch.com",
            "www.google-analytics.com",
            "d12au6kkv2cs1v.cloudfront.net",
            "pagead2.googlesyndication.com",
            "d2gfdmu30u15x7.cloudfront.net",
            "js.foodity.com",
            "api-us1.lift.acquia.com");
    private WebView webView;
    private String printLink;

    @SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_PROGRESS);
//        requestWindowFeature(Window.FEATURE_PROGRESS);
        setProgressBarVisibility(true);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setPrintLink(final String printLink) {
                Log.d("PRINT", "PDF: " + printLink);
                MainActivity.this.printLink = printLink;
            }
        }, "PRINTLINK");
        webView.setWebViewClient(new WebViewClient() {

            public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
                try {
                    final URL aUrl = new URL(url);
                    if (!ignoredHosts.contains(aUrl.getHost())) {
                        if (!allowedHosts.contains(aUrl.getHost())) {
                            Log.d("NETWORK", "Loading: " + aUrl.getHost());
                        }
                        return super.shouldInterceptRequest(view, url);
                    }
                    return new WebResourceResponse("text/html", "utf-8", null);
                } catch (final MalformedURLException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                Log.d("NETWORK", "Loaded: " + url);
                webView.loadUrl("javascript:window.PRINTLINK.setPrintLink(document.getElementsByClassName('btn-print')[0].href);");
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
        } else if (item.getItemId() == R.id.menu_item_print) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                final PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter();
                final String jobName = getString(R.string.app_name) + " Document";
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            }
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

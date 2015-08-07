package com.biddster.betterfood;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.biddster.betterfood.Logger.NETWORK;
import static com.biddster.betterfood.Logger.PREFS;
import static com.biddster.betterfood.Logger.PRINT;
import static com.biddster.betterfood.Logger.log;

public class MainActivity extends AppCompatActivity {

    private static final String OPEN_AFTER_DOWNLOAD = MainActivity.class.getName() + ".OpenAfterDownload";
    private static final String goodFoodHome = "http://www.bbcgoodfood.com";
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
    private ProgressBar progressBar;
    private String printLink;
    private DownloadManager downloadManager;
    private long lastDownload;
    private boolean open;

    @SuppressLint({"JavascriptInterface", "AddJavascriptInterface", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setPrintLink(final String printLink) {
                log(PRINT, null, "PDF: %s", printLink);
                MainActivity.this.printLink = printLink;
            }
        }, "PRINTLINK");
        webView.setWebViewClient(new WebViewClient() {

            public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
                try {
                    final URL aUrl = new URL(url);
                    if (!ignoredHosts.contains(aUrl.getHost())) {
                        if (!allowedHosts.contains(aUrl.getHost())) {
                            log(NETWORK, null, "Loading: %s", aUrl.getHost());
                        }
                        return super.shouldInterceptRequest(view, url);
                    }
                    return new WebResourceResponse("text/html", "utf-8", null);
                } catch (final MalformedURLException e) {
                    e.printStackTrace();
                }
                return null;
            }

            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (url.startsWith("mailto:")) {
                    startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                log(NETWORK, null, "Loaded: %s", url);
                webView.loadUrl("javascript:window.PRINTLINK.setPrintLink(jQuery('.btn-print:first').attr('href'));");
                webView.loadUrl("javascript:jQuery('.tips-carousel,#buy-ingredients,.side-bar-content,.adsense-ads,#footer').hide()");
                saveLastPage(url);
            }
        });
        final Animation slideOutTop = AnimationUtils.loadAnimation(this, R.anim.abc_slide_out_top);
        final Animation slideInTop = AnimationUtils.loadAnimation(this, R.anim.abc_slide_in_top);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(final WebView view, final int progress) {
                progressBar.setProgress(progress);
                if (progress == 100) {
                    progressBar.startAnimation(slideOutTop);
                    progressBar.setVisibility(View.GONE);
                } else if (progressBar.getVisibility() == View.GONE) {
                    progressBar.startAnimation(slideInTop);
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });

        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        loadLastPage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(onComplete);
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
            shareCurrentPage();
            return true;
        } else if (item.getItemId() == R.id.menu_item_print) {
            startDownload(true);
            return true;
        } else if (item.getItemId() == R.id.menu_item_download) {
            startDownload(false);
            return true;
        } else if (item.getItemId() == R.id.menu_item_view_downloads) {
            viewDownloads();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goHome() {
        webView.loadUrl(goodFoodHome);
    }

    private void shareCurrentPage() {
        final Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, webView.getTitle());
        i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
        startActivity(Intent.createChooser(i, "Share - " + webView.getTitle()));
    }

    private void saveLastPage(final String url) {
        log(PREFS, null, "Saving page [%s]", url);
        getSharedPreferences().edit().putString("LastPage", url).apply();
    }

    private void loadLastPage() {
        final String lastPage = getSharedPreferences().getString("LastPage", goodFoodHome);
        log(PREFS, null, "Loading last page [%s]", lastPage);
        webView.loadUrl(lastPage);
    }

    private SharedPreferences getSharedPreferences() {
        return getSharedPreferences("BF", Context.MODE_PRIVATE);
    }

    private Set<String> newHashSet(final String... entries) {
        final HashSet<String> set = new HashSet<>();
        Collections.addAll(set, entries);
        return set;
    }

    private void startDownload(final boolean open) {
        this.open = open;
        if (!TextUtils.isEmpty(printLink)) {
            final File betterFoodDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BetterFood");
            betterFoodDirectory.mkdirs();
            final File download = new File(betterFoodDirectory, webView.getTitle() + ".pdf");
            if (!download.exists()) {
                final Uri uri = Uri.parse(goodFoodHome + printLink);
                log(NETWORK, null, "Downloading [%s]", uri);
                lastDownload = downloadManager.enqueue(new DownloadManager.Request(uri)
                        .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                        .setAllowedOverRoaming(false)
                        .setTitle(webView.getTitle())
                        .setDestinationUri(Uri.parse(download.toURI().toString())));
            } else if (open) {
                log(NETWORK, null, "Already downloaded [%s]", download);
                openPdf(Uri.parse(download.toURI().toString()));
            } else {
                Toast.makeText(this, "File already downloaded to Downloads directory, click 'View downloads' to view it.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void viewDownloads() {
        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
    }

    private void openPdf(final Uri uri) {
        log(NETWORK, null, "Opening [%s]", uri);
        startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf"));
    }

    private final BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(final Context ctxt, final Intent intent) {
            final Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(lastDownload));
            c.moveToFirst();
            final String localUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
            log(NETWORK, null, "Downloaded to [%s]", localUri);
            final Uri uri = Uri.parse(localUri);
            if (open) {
                openPdf(uri);
            } else {
                Toast.makeText(MainActivity.this, "File downloaded to Downloads directory, click 'View downloads' to view it.",
                        Toast.LENGTH_LONG).show();
            }
        }
    };
}

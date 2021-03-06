package com.biddster.betterfood;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.widget.DrawerLayout;
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
import android.widget.SearchView;
import android.widget.Toast;

import com.facebook.stetho.okhttp.StethoInterceptor;
import com.github.lzyzsd.circleprogress.DonutProgress;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.biddster.betterfood.Logger.NETWORK;
import static com.biddster.betterfood.Logger.PREFS;
import static com.biddster.betterfood.Logger.PRINT;
import static com.biddster.betterfood.Logger.log;
import static com.biddster.betterfood.Logger.logException;

@SuppressLint({"JavascriptInterface", "AddJavascriptInterface", "SetJavaScriptEnabled"})
public class MainActivity extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String goodFoodHome = "http://www.bbcgoodfood.com";
    private static final String goodFoodSearch = "http://www.bbcgoodfood.com/search/recipes?query=";
    private final Set<String> allowedHosts = newHashSet("www.bbcgoodfood.com", "ajax.googleapis.com", "code.jquery.com");
    //    "secure-au.imrworldwide.com",
    private final Set<Pattern> ignored = newHashSet(
            Pattern.compile(".*\\.akamaihd\\.net.*"),
            Pattern.compile(".*\\.acquia\\.com.*"),
            Pattern.compile(".*\\.bbclogin\\.com.*"),
            Pattern.compile(".*\\.brightcove\\.com.*"),
            Pattern.compile(".*\\.cloudfront\\.net.*"),
            Pattern.compile(".*\\.foodity\\.com.*"),
            Pattern.compile(".*\\.googletagservices\\.com.*"),
            Pattern.compile(".*\\.google-analytics\\.com.*"),
            Pattern.compile(".*\\.googlesyndication\\.com.*"),
            Pattern.compile(".*\\.imrworldwide\\.com.*"),
            Pattern.compile(".*\\.inspectlet\\.com.*"),
            Pattern.compile(".*\\.krxd\\.net.*"),
            Pattern.compile(".*\\.outbrain\\.com.*"),
            Pattern.compile(".*\\.revsci\\.net.*"),
            Pattern.compile(".*\\.scorecardresearch\\.com.*")
    );
    private WebView webView;
    private String printLink;
    private DownloadManager downloadManager;
    private long lastDownload;
    private boolean open;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private CharSequence mTitle;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (BuildConfig.DEBUG) {
            final OkHttpClient client = new OkHttpClient();
            client.networkInterceptors().add(new StethoInterceptor());
        }
        mTitle = getTitle();
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));
        mNavigationDrawerFragment.clearDrawer();

        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setBackgroundColor(Color.WHITE);

        final View progressView = findViewById(R.id.progress_view);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            //Required to stop signal 11 (SIGSEGV) Crash when starting a search from the SearchView on the action bar.Disables hardware accelerate for WebView
            //http://stackoverflow.com/questions/18520844/webview-causes-application-restart-fatal-signal-11-sigsegv
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setPrintLink(final String printLink) {
                log(PRINT, null, "PDF: %s", printLink);
                MainActivity.this.printLink = printLink;
            }
        }, "PRINTLINK");

        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        final Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        final Handler handler = new Handler();
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void showWebView() {
                // Time here is eyeballed. It's the time that makes the load clean on a Xoom.
                // In conjunction with the fade in animation, combined they should be about a second.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        progressView.startAnimation(fadeOut);
                        progressView.setVisibility(View.INVISIBLE);
                    }
                }, 750);
            }
        }, "FINISHED");
        webView.setWebViewClient(new WebViewClient() {
            public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
                try {
                    final URL aUrl = new URL(url);
                    if (!isIgnored(url)) {
                        if (!allowedHosts.contains(aUrl.getHost())) {
                            log(NETWORK, null, "Unexpected load: %s", aUrl.getHost());
                        }
                        return super.shouldInterceptRequest(view, url);
                    }
                    return new WebResourceResponse("text/html", "utf-8", null);
                } catch (final MalformedURLException e) {
                    logException(e);
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
            public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                log(NETWORK, null, "onPageStarted: [%s]", url);
                progressView.startAnimation(fadeIn);
                progressView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                log(NETWORK, null, "Loaded: %s", url);
                saveLastPage(url);
                // TODO - should probably insert a style node into the DOM for the CSS changes.
                webView.loadUrl(
                        "javascript:jQuery('#recipe-content,#search-main > .row > .col').addClass('span12');" +
                                "jQuery('#scroll-wrapper').css('padding-top', '0px');" +
                                "jQuery('#scroller').css('margin-top', '-18px');" +
                                "jQuery('.search-header-bar').css('top', '0px');" +
                                "jQuery('.main-container').css('margin-top', '0px');" +
                                "jQuery('#more-info-button').click();" +
                                "jQuery('.page-header-touch,.ad--mobile-banner,.sharing-options,#nav-touch.tips-carousel," +
                                "#buy-ingredients,.side-bar-content,.adsense-ads,#footer,.nav-touch,.page-header-touch," +
                                "#ad-mobile-banner,#ad-leader,#print-logo,#print-ad-leaderboard,#masthead,#nav-toolbar" +
                                "#bbcgf-search-form,.col span4,aside,#recipetools,#ad-mpu-top').remove();" +
                                "window.PRINTLINK.setPrintLink(jQuery('.btn-print:first').hide().attr('href'));" +
                                "window.FINISHED.showWebView();"
                );
            }
        });
        final DonutProgress donutProgress = (DonutProgress) findViewById(R.id.donut_progress);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(final WebView view, final int progress) {
                donutProgress.setProgress(progress);
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
    public boolean onOptionsItemSelected(final MenuItem item) {
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
        getAppSharedPreferences().edit().putString("LastPage", url).apply();
    }

    private void loadLastPage() {
        final String lastPage = getAppSharedPreferences().getString("LastPage", goodFoodHome);
        log(PREFS, null, "Loading last page [%s]", lastPage);
        webView.loadUrl(lastPage);
    }

    private boolean isIgnored(final String url) {
        for (final Pattern p : ignored) {
            if (p.matcher(url).matches()) {
                return true;
            }
        }
        return false;
    }

    private SharedPreferences getAppSharedPreferences() {
        return getSharedPreferences("BF", Context.MODE_PRIVATE);
    }

    private <T> Set<T> newHashSet(final T... entries) {
        final HashSet<T> set = new HashSet<>();
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

    @Override
    public void loadUrl(final String url) {
        log(NETWORK, null, "loadUrl");
        webView.loadUrl(url);
    }

    private void restoreActionBar() {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(mTitle);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.menu_main, menu);
            restoreActionBar();
            final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            final SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
                public boolean onQueryTextChange(final String newText) {
//                    log(UIEVENT, null, "onQueryTextChange: [%s]",newText);
                    return true;
                }

                public boolean onQueryTextSubmit(final String query) {
                    //Here u can get the value "query" which is entered in the search box.
                    //log(UIEVENT, null, "onQueryTextSubmit: [%s]", query);
                    searchView.setQuery("", false);
                    searchView.clearFocus(); // close the keyboard on load
                    searchView.onActionViewCollapsed();
                    webView.loadUrl(goodFoodSearch + query);
                    return true;
                }
            };
            searchView.setOnQueryTextListener(queryTextListener);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }
}
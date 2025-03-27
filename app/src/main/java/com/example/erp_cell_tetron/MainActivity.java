package com.example.erp_cell_tetron;

import androidx.appcompat.app.AppCompatActivity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.browser.customtabs.CustomTabsIntent;
import android.content.Intent;

public class MainActivity extends AppCompatActivity {

    private WebView myWeb;
    private static final String LOGIN_URL = "https://accounts.google.com"; // Google Sign-In URL
    private static final String REDIRECT_URL = "https://bharathuniv.tech/home"; // Change this to your post-login URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myWeb = findViewById(R.id.myWeb);

        if (myWeb != null) {
            myWeb.getSettings().setJavaScriptEnabled(true);
            myWeb.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();

                    if (url.startsWith(LOGIN_URL)) {
                        // Open Google Sign-In in Chrome Custom Tabs
                        openInCustomTab(url);
                        return true; // Prevent WebView from loading
                    }
                    return false; // Continue loading other URLs in WebView
                }
            });

            myWeb.loadUrl("https://bharathuniv.tech/");
        } else {
            System.err.println("WebView is null. Check activity_main.xml for correct ID.");
        }
    }

    private void openInCustomTab(String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload WebView when returning from Chrome Custom Tabs
        if (myWeb != null) {
            myWeb.loadUrl(REDIRECT_URL);
        }
    }
}

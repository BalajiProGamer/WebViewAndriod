package com.example.erp_cell_tetron;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    WebView myWeb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myWeb = findViewById(R.id.myWeb);

        if (myWeb != null) {
            myWeb.getSettings().setJavaScriptEnabled(true);
            myWeb.setWebViewClient(new WebViewClient());
            myWeb.loadUrl("https://erp.tetroninfotech.online/");
        } else {
            // Log error and handle the null case
            System.err.println("WebView is null. Check activity_main.xml for correct ID.");
        }
    }

}
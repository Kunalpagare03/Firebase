package com.Kunal.stock

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply professional splash screen via Jetpack Core library
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize WebView
        webView = WebView(this)
        
        // Professional API & Technical Configuration
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // Essential for login session & GitHub token persistence
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep all navigation within the professional app hub
                return false 
            }
        }

        // Load the High-Performance Institutional Hub
        webView.loadUrl("https://kunal-analysis.web.app")
        setContentView(webView)

        // Handle System Back Button for smooth navigation between Hub sections
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
}

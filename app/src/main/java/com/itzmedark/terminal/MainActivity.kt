package com.itzmedark.terminal

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    // Ultra-fast thread pool for concurrent tasks
    private val executor = Executors.newFixedThreadPool(10) 

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide ActionBar for immersive look
        supportActionBar?.hide() 

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        
        // Bind Kotlin to your JavaScript
        webView.addJavascriptInterface(TerminalBridge(), "Android")
        
        // Load the HTML file from assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class TerminalBridge {
        @JavascriptInterface
        fun execute(command: String) {
            executor.execute {
                try {
                    // Run command inside the app's private directory
                    val process = Runtime.getRuntime().exec(command, null, filesDir)
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    val output = StringBuilder()
                    
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("<br>")
                    }
                    process.waitFor()
                    
                    val finalOutput = output.toString().replace("'", "\\'")
                    runOnUiThread {
                        webView.evaluateJavascript("appendOutput('$finalOutput')", null)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        webView.evaluateJavascript("appendOutput('Error: ${e.message}')", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun installPackage(pkgName: String) {
            executor.execute {
                try {
                    val targetDir = File(filesDir, "bin")
                    targetDir.mkdirs()
                    val targetFile = File(targetDir, pkgName)
                    
                    // Directly pull raw binaries from your GitHub repo
                    val repoUrl = "https://raw.githubusercontent.com/Itz_MeDark/termux-commands/main/$pkgName"
                    URL(repoUrl).openStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Instantly make the downloaded file executable
                    targetFile.setExecutable(true)
                    
                    runOnUiThread {
                        webView.evaluateJavascript("stopDownloadAnimation('<span style=\"color:#00ffcc;\">✔ Successfully integrated $pkgName</span>')", null)
                    }
                } catch (e: Exception) {
                     runOnUiThread {
                        webView.evaluateJavascript("stopDownloadAnimation('<span style=\"color:#ff4500;\">✘ Download failed: ${e.message}</span>')", null)
                    }
                }
            }
        }
    }
}

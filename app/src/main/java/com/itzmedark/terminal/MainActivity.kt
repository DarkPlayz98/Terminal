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
    // Ultra-fast 10-thread pool for concurrent operations
    private val executor = Executors.newFixedThreadPool(10) 

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure minimalist full-screen design
        supportActionBar?.hide() 

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        
        // Link Native Android to Custom UI
        webView.addJavascriptInterface(TerminalBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class TerminalBridge {
        @JavascriptInterface
        fun execute(command: String) {
            executor.execute {
                try {
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
                    val errorMsg = e.message?.replace("'", "\\'") ?: "Unknown error"
                    runOnUiThread {
                        webView.evaluateJavascript("appendOutput('Error: $errorMsg')", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun installPackage(target: String) {
            executor.execute {
                try {
                    val parts = target.split("/")
                    val pkgName = parts.last()

                    val targetDir = File(filesDir, "bin")
                    targetDir.mkdirs()
                    val targetFile = File(targetDir, pkgName)
                    
                    // Universal repository logic
                    val repoUrl = if (parts.size >= 3) {
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/main/${parts.drop(2).joinToString("/")}"
                    } else {
                        "https://raw.githubusercontent.com/Itz_MeDark/termux-commands/main/$target"
                    }

                    URL(repoUrl).openStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    targetFile.setExecutable(true)
                    
                    runOnUiThread {
                        webView.evaluateJavascript("stopDownloadAnimation('<span style=\"color:#00ffcc;\">✔ Successfully integrated $pkgName</span>')", null)
                    }
                } catch (e: Exception) {
                     runOnUiThread {
                        webView.evaluateJavascript("stopDownloadAnimation('<span style=\"color:#ff4500;\">✘ Download failed: Verify repository or connection.</span>')", null)
                    }
                }
            }
        }
    }
}

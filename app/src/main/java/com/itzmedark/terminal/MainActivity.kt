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
    private val executor = Executors.newFixedThreadPool(10)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        
        webView.addJavascriptInterface(TerminalBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class TerminalBridge {
        @JavascriptInterface
        fun execute(command: String) {
            executor.execute {
                try {
                    // FIX FOR ERROR 13: Execute via the native Android shell
                    val process = ProcessBuilder("/system/bin/sh", "-c", command)
                        .directory(filesDir)
                        .redirectErrorStream(true)
                        .start()

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    val output = StringBuilder()
                    
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("<br>")
                    }
                    process.waitFor()
                    
                    val finalOutput = output.toString().replace("'", "\\'").replace("\n", "<br>")
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

        // ---- NANO EDITOR BRIDGES ----
        @JavascriptInterface
        fun readFile(filename: String) {
            try {
                val file = File(filesDir, filename)
                val content = if (file.exists()) file.readText().replace("`", "\\`") else ""
                runOnUiThread {
                    webView.evaluateJavascript("openNanoUI('$filename', `$content`)", null)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    webView.evaluateJavascript("appendOutput('Error reading file: ${e.message}')", null)
                }
            }
        }

        @JavascriptInterface
        fun saveFile(filename: String, content: String) {
            try {
                val file = File(filesDir, filename)
                file.writeText(content)
                // Make shell scripts executable automatically
                if (filename.endsWith(".sh")) file.setExecutable(true)
                
                runOnUiThread {
                    webView.evaluateJavascript("appendOutput('File saved: $filename')", null)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    webView.evaluateJavascript("appendOutput('Error saving file: ${e.message}')", null)
                }
            }
        }
        // -----------------------------

        @JavascriptInterface
        fun installPackage(target: String) {
            executor.execute {
                try {
                    val parts = target.split("/")
                    val pkgName = parts.last()

                    val targetDir = File(filesDir, "bin")
                    targetDir.mkdirs()
                    val targetFile = File(targetDir, pkgName)
                    
                    val repoUrl = if (parts.size >= 3) {
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.drop(2).joinToString("/")}"
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
                        webView.evaluateJavascript("appendOutput('<span style=\"color:#D0BCFF;\">✔ Installed $pkgName</span>')", null)
                    }
                } catch (e: Exception) {
                     runOnUiThread {
                        webView.evaluateJavascript("appendOutput('<span style=\"color:#F2B8B5;\">✘ Download failed</span>')", null)
                    }
                }
            }
        }
    }
}

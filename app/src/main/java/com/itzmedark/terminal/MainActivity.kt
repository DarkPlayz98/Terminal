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
import java.net.HttpURLConnection
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
                    val binDir = File(filesDir, "bin").absolutePath
                    // We inject the bin folder into the PATH so downloaded scripts run normally
                    val fullCommand = "export PATH=\$PATH:$binDir && $command"
                    
                    val process = ProcessBuilder("/system/bin/sh", "-c", fullCommand)
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
                    runOnUiThread { webView.evaluateJavascript("appendOutput('$finalOutput')", null) }
                } catch (e: Exception) {
                    val errorMsg = e.message?.replace("'", "\\'") ?: "Unknown error"
                    runOnUiThread { webView.evaluateJavascript("appendOutput('Error: $errorMsg')", null) }
                }
            }
        }

        @JavascriptInterface
        fun readFile(filename: String) {
            try {
                val file = File(filesDir, filename)
                val content = if (file.exists()) file.readText().replace("`", "\\`") else ""
                runOnUiThread { webView.evaluateJavascript("openNanoUI('$filename', `$content`)", null) }
            } catch (e: Exception) {
                runOnUiThread { webView.evaluateJavascript("appendOutput('Error reading: ${e.message}')", null) }
            }
        }

        @JavascriptInterface
        fun saveFile(filename: String, content: String) {
            try {
                val file = File(filesDir, filename)
                file.writeText(content)
                if (filename.endsWith(".sh") || filename.endsWith(".py")) file.setExecutable(true)
                runOnUiThread { webView.evaluateJavascript("appendOutput('Saved and granted execution to $filename')", null) }
            } catch (e: Exception) {
                runOnUiThread { webView.evaluateJavascript("appendOutput('Error saving: ${e.message}')", null) }
            }
        }

        @JavascriptInterface
        fun installPackage(target: String) {
            executor.execute {
                try {
                    val targetDir = File(filesDir, "bin")
                    targetDir.mkdirs()

                    val repoUrl = if (target.startsWith("http")) {
                        target // Allow direct URL downloads
                    } else {
                        // Default to your repo
                        "https://raw.githubusercontent.com/Itz_MeDark/termux-commands/main/$target"
                    }

                    val url = URL(repoUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        runOnUiThread { webView.evaluateJavascript("appendOutput('<span style=\"color:#F2B8B5;\">✘ Failed to fetch package. Check URL or GitHub Repo. (HTTP ${connection.responseCode})</span>')", null) }
                        return@execute
                    }

                    val pkgName = target.split("/").last()
                    val targetFile = File(targetDir, pkgName)

                    connection.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    targetFile.setExecutable(true)
                    
                    runOnUiThread { webView.evaluateJavascript("appendOutput('<span style=\"color:#D0BCFF;\">✔ Installed $pkgName to bin/</span>')", null) }
                } catch (e: Exception) {
                     runOnUiThread { webView.evaluateJavascript("appendOutput('<span style=\"color:#F2B8B5;\">✘ Download error: ${e.message}</span>')", null) }
                }
            }
        }
    }
}

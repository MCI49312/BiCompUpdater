package pixela16.space.bicompupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val activeDownloadIds = mutableSetOf<Long>()
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val updateButton: Button = findViewById(R.id.updateButton)

        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )

        updateButton.setOnClickListener {
            updateButton.isEnabled = false
            statusText.text = "Checking for app updates..."

            // Queue updates for your specific bicycle computer apps
            queueAppUpdate("MCI49312", "speedometer", "Speedometer.apk")
            queueAppUpdate("MCI49312", "FoxBike", "FoxBike.apk")
        }
    }

    private fun queueAppUpdate(owner: String, repo: String, fileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = getLatestReleaseUrl(owner, repo)

            withContext(Dispatchers.Main) {
                if (url != null) {
                    statusText.text = "Downloading latest $repo..."
                    startDownload(url, fileName)
                } else {
                    // If no release is found, just keep moving
                    if (activeDownloadIds.isEmpty()) {
                        findViewById<Button>(R.id.updateButton).isEnabled = true
                    }
                }
            }
        }
    }

    private fun getLatestReleaseUrl(owner: String, repo: String): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "BiCompUpdater")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val assetsArray = jsonResponse.getJSONArray("assets")
                if (assetsArray.length() > 0) {
                    return assetsArray.getJSONObject(0).getString("browser_download_url")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun startDownload(url: String, fileName: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        activeDownloadIds.add(downloadId)
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (activeDownloadIds.contains(id)) {
                activeDownloadIds.remove(id)
                statusText.text = "Download finished. Launching installer..."
                launchInstaller(id)

                if (activeDownloadIds.isEmpty()) {
                    findViewById<Button>(R.id.updateButton).isEnabled = true
                    statusText.text = "All updates processed."
                }
            }
        }
    }

    private fun launchInstaller(downloadId: Long) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId)

        if (uri != null) {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(installIntent)
            } catch (e: Exception) {
                statusText.text = "Error launching package installer."
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
}
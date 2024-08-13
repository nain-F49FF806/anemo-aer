package alt.nainapps.aer.config

import alt.nainapps.aer.config.ui.theme.AnemoaerTheme
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File

class StorageConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnemoaerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val storageInfos by rememberSaveable { mutableStateOf(fetchExternalStorageDirectories(this.applicationContext)) }
                    StorageInfoList(storageInfos )
                    Greeting(name = "Wohoo!")
                }
            }
        }
    }
}

@Composable
fun StorageInfoList(storageInfos: List<StorageInfo>) {
    Log.i("live", "about to begin")
    Column(modifier = Modifier.padding(16.dp)) {
        for (info in storageInfos) {
            Log.i("live", "$info")
            Card(modifier = Modifier.padding(8.dp)) {
                Text(text = info.name ?: "Unknown")
                Text(text = "Total Space: ${ bytesToHumanReadableSize(info.totalSpace) }")
                Text(text = "Free Space: ${ bytesToHumanReadableSize(info.freeSpace) }")
                Text(text = "Is Emulated: ${if (info.isEmulated) "Yes" else "No"}")
            }
        }
    }
}

data class StorageInfo(
    val name: String?,
    val totalSpace: Long,
    val freeSpace: Long,
    val isEmulated: Boolean
)

fun fetchExternalStorageDirectories(context: Context): List<StorageInfo> {
    val directories = mutableListOf<StorageInfo>()
    val externalDirs = context.getExternalFilesDirs(null)

    for (dir in externalDirs) {
        if (dir != null) {
            val totalSpace = getTotalSpace(dir)
            val freeSpace = getFreeSpace(dir)

            val isEmulated = Environment.isExternalStorageEmulated(dir)

            directories.add(StorageInfo(dir.path, totalSpace, freeSpace, isEmulated))
        }
    }

    return directories
}
private fun getTotalSpace(path: File): Long {
    val statFs = StatFs(path.absolutePath)
    return statFs.blockCountLong * statFs.blockSizeLong
}

private fun getFreeSpace(path: File): Long {
    val statFs = StatFs(path.absolutePath)
    return statFs.availableBlocksLong * statFs.blockSizeLong
}

fun bytesToHumanReadableSize(bytes: Long) = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.0f kB".format(bytes.toDouble() / (1 shl 10))
    else -> "$bytes bytes"
}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AnemoaerTheme {
        Greeting("Android")
    }
}

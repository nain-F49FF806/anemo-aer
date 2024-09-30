package alt.nainapps.aer.config

import alt.nainapps.aer.config.ui.theme.AnemoaerTheme
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

class StorageConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnemoaerTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                        topBar = {
                    TopAppBar(
                        title = {
                            Text("Aer Storage Backend priority")
                        }
                    )
                }) { innerPadding ->
                    val storageInfos by rememberSaveable { mutableStateOf(fetchExternalStorageDirectories(this.applicationContext)) }
                    Column (Modifier.padding(paddingValues = innerPadding)) {
                        Text(
                            text = "Drag to reorder:",
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(8.dp)
                        )
                        StorageInfoListReorderable(storageInfos)
                    }
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
                Text(text = info.dir)
                Text(text = "Total Space: ${ bytesToHumanReadableSize(info.totalSpace) }")
                Text(text = "Free Space: ${ bytesToHumanReadableSize(info.freeSpace) }")
                Text(text = "Is Emulated: ${if (info.isEmulated) "Yes" else "No"}")
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageInfoListReorderable(storageInfos: List<StorageInfo>) {
    val mutableStorageInfosList = remember { mutableStateListOf(*storageInfos.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            mutableStorageInfosList.apply { add(to.index, removeAt(from.index)) }
            if (to.index == 0 ) {
                // TODO: Save it to settings
            }
        }
    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(mutableStorageInfosList, key = { it.dir }) { info ->
            ReorderableItem(reorderableLazyListState, key = info.dir) {
                val interactionSource = remember { MutableInteractionSource() }
                val longPressDraggable = Modifier.longPressDraggableHandle(interactionSource = interactionSource)
                val draggable = Modifier.draggableHandle(interactionSource = interactionSource)
                StorageInfoCard(info = info, longPressDraggable, draggable)
            }
        }
    }
}

// Pass either longPressDraggableModifier or draggableModifier to make it draggable
@Composable
fun StorageInfoCard(
    info: StorageInfo,
    @SuppressLint("ModifierParameter")
    longPressDraggableModifier: Modifier? = null,
    draggableModifier: Modifier? = null,
) {
    Card(onClick = {}, modifier = (longPressDraggableModifier ?: Modifier).padding(horizontal = 8.dp)) {
        Row {
            (draggableModifier ?: longPressDraggableModifier)?.let {
                IconButton( modifier = it, onClick = {}) {
                    Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Reorder")
                }
            }

            Column(modifier = Modifier.padding(4.dp)) {
                Text(text = "Location: ${info.dir}")
                Text(text = "Total Space: ${ bytesToHumanReadableSize(info.totalSpace) }")
                Text(text = "Free Space: ${ bytesToHumanReadableSize(info.freeSpace) }")
                Text(text = "Is Emulated: ${if (info.isEmulated) "Yes" else "No"}")
            }
        }
    }

}

//@Composable
//fun DraggableHandleIcon(draggableModifier: Modifier) {
//    IconButton(
//        modifier = draggableModifier,
//        onClick = {},
//    ) {
//        Icon(Icons.Rounded.Menu, contentDescription = "Reorder")
//    }
//}

data class StorageInfo(
    val dir: String,
    val totalSpace: Long,
    val freeSpace: Long,
    val isEmulated: Boolean,
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

fun bytesToHumanReadableSize(bytes: Long) =
    when {
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

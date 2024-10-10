package alt.nainapps.aer.config

import alt.nainapps.aer.R
import alt.nainapps.aer.config.ui.theme.AnemoaerTheme
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

class StorageConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // At the top level of your kotlin file:
        val sharedPrefs = getDefaultSharedPreferences(this)
        setContent {
            AnemoaerTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                        topBar = {
                    TopAppBar(
                        title = {
                            // Text("Aer Storage Backend priority")
                            Text(stringResource(R.string.storage_config_screen_title))
                        }
                    )
                }) { innerPadding ->
                    var selectedStorageDir by rememberSaveable { mutableStateOf(getPreferredStorageDir(sharedPrefs)) }
                    var storageInfos = fetchExternalStorageDirectories(this.applicationContext)
                    val internalStorageInfo =  StorageInfo(
                        filesDir.toString(),
                        getTotalSpace(filesDir),
                        getFreeSpace(filesDir),
                        isEmulated = false,
                        isRemoveAble = false,
                        isInternal = true
                    )
                    storageInfos = listOf(*storageInfos.toTypedArray(), internalStorageInfo)

                    Column (Modifier.padding(paddingValues = innerPadding)) {
                        selectedStorageDir?.let {
                            Card(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Selected: $it",
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.storage_config_select_help),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(8.dp)
                        )

                        StorageInfoListReorderable(storageInfos, sharedPrefs) {
                            selected -> selectedStorageDir = selected
                        }
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
fun StorageInfoListReorderable(storageInfos: List<StorageInfo>, sharedPrefs: SharedPreferences,
                               onFreshStorageSelect: (String?) -> Unit) {
    val mutableStorageInfosList = remember { mutableStateListOf(*storageInfos.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            mutableStorageInfosList.apply { add(to.index, removeAt(from.index)) }
            if (to.index == 0  || from.index == 0 ) {
                // Save it to settings
                savePreferredStorageDir(sharedPrefs, mutableStorageInfosList.first().dir)
                // callback to let parent compose update
                onFreshStorageSelect(getPreferredStorageDir(sharedPrefs))
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
                StorageInfoCard(info = info, longPressDraggable, draggable) {
                    savePreferredStorageDir(sharedPrefs, info.dir)
                    onFreshStorageSelect(getPreferredStorageDir(sharedPrefs))
                }
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
    onClick: () -> Unit = {}
) {
    Card(onClick = onClick, modifier = (longPressDraggableModifier ?: Modifier).padding(horizontal = 8.dp)) {
        Row (Modifier.padding(4.dp)) {
            (draggableModifier ?: longPressDraggableModifier)?.let {
                IconButton( modifier = it, onClick = {}) {
                    Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Reorder")
                }
            }

            Column {
                Row (
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (info.isInternal) "Internal" else "External", fontWeight = FontWeight.Bold )
                    Row {
                        if (info.isRemoveAble) {
                            SuggestionChip(
                                onClick = { },
                                modifier = Modifier.sizeIn(maxHeight = 20.dp).padding(horizontal = 2.dp),
                                label = { Text(text = "Removable", fontSize = 10.sp)}

                            )
                        }
                        if (info.isEmulated) {
                            SuggestionChip(
                                onClick = { },
                                modifier = Modifier.sizeIn(maxHeight = 20.dp).padding(horizontal = 2.dp),
                                label = { Text(text = "Emulated", fontSize = 10.sp )}
                            )
                        }
                    }

                }
                val totalSpace = bytesToHumanReadableSize(info.totalSpace)
                val freeSpace = bytesToHumanReadableSize(info.freeSpace)
                Column (Modifier.padding(vertical = 4.dp)) {
                    Text(text = info.dir, fontWeight = FontWeight.Medium)
                    Text(text = "Total Space: $totalSpace")
                    Text(text = "Free Space: $freeSpace")
                    Spacer(Modifier.size(2.dp))
                }


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
    val isRemoveAble: Boolean,
    val isInternal: Boolean
)

fun fetchExternalStorageDirectories(context: Context): List<StorageInfo> {
    val directories = mutableListOf<StorageInfo>()
    val externalDirs = context.getExternalFilesDirs(null)

    for (dir in externalDirs.reversed()) {
        if (dir != null) {
            val totalSpace = getTotalSpace(dir)
            val freeSpace = getFreeSpace(dir)

            val isEmulated = Environment.isExternalStorageEmulated(dir)
            val isRemovable = Environment.isExternalStorageRemovable(dir)
            val isInternal = false

            directories.add(StorageInfo(dir.path, totalSpace, freeSpace, isEmulated, isRemovable, isInternal))
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

fun savePreferredStorageDir(sharedPrefs: SharedPreferences, dir: String) {
    with (sharedPrefs.edit()) {
        putString("selected_storage_dir", dir)
        apply()
    }
}

fun getPreferredStorageDir(sharedPrefs: SharedPreferences): String? {
    return sharedPrefs.getString("selected_storage_dir", null)
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

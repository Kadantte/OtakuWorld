package com.programmersbox.animeworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.programmersbox.helpfulutils.stringForTime
import com.programmersbox.uiviews.BaseMainActivity
import com.programmersbox.uiviews.utils.*
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

class ViewVideosFragment : BaseBottomSheetDialogFragment() {

    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalMaterialApi::class,
        ExperimentalAnimationApi::class,
        ExperimentalPermissionsApi::class
    )
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
        setContent {
            M3MaterialTheme(currentColorScheme) {
                PermissionRequest(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    val context = LocalContext.current
                    val viewModel: DownloadViewModel = viewModel(factory = factoryCreate { DownloadViewModel(context) })
                    VideoLoad(viewModel)
                }
            }
        }
    }

    class DownloadViewModel(context: Context) : ViewModel() {

        var videos by mutableStateOf<List<VideoContent>>(emptyList())

        private val v = VideoGet.getInstance(context).also { v ->
            v?.loadVideos(viewModelScope, VideoGet.externalContentUri)
            viewModelScope.launch { v?.videos2?.collect { videos = it } }
        }

        override fun onCleared() {
            super.onCleared()
            v?.unregister()
        }

    }

    @ExperimentalMaterial3Api
    @ExperimentalAnimationApi
    @ExperimentalMaterialApi
    @Composable
    private fun VideoLoad(viewModel: DownloadViewModel) {

        val items = viewModel.videos

        val state = rememberBottomSheetScaffoldState()
        val scope = rememberCoroutineScope()

        BackHandler(state.bottomSheetState.isExpanded && currentScreen.value == R.id.setting_nav) {
            scope.launch { state.bottomSheetState.collapse() }
        }

        var itemToDelete by remember { mutableStateOf<VideoContent?>(null) }
        val showDialog = remember { mutableStateOf(false) }

        itemToDelete?.let { SlideToDeleteDialog(showDialog = showDialog, video = it) }

        val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }

        BottomSheetDeleteScaffold(
            bottomScrollBehavior = scrollBehavior,
            topBar = {
                SmallTopAppBar(
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { IconButton(onClick = { findNavController().popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                    title = { Text(stringResource(R.string.downloaded_videos)) },
                    actions = {
                        AndroidView(
                            factory = { context ->
                                MediaRouteButton(context).apply {
                                    MainActivity.cast.showIntroductoryOverlay(this)
                                    MainActivity.cast.setMediaRouteMenu(context, this)
                                }
                            }
                        )
                        IconButton(onClick = { scope.launch { state.bottomSheetState.expand() } }) { Icon(Icons.Default.Delete, null) }
                    }
                )
            },
            state = state,
            listOfItems = items,
            multipleTitle = stringResource(id = R.string.delete),
            deleteTitle = { it.videoName.orEmpty() },
            onRemove = {
                itemToDelete = it
                showDialog.value = true
            },
            customSingleRemoveDialog = {
                itemToDelete = it
                showDialog.value = true
                false
            },
            onMultipleRemove = { downloadedItems ->
                downloadedItems.forEach {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            it.assetFileStringUri?.toUri()?.let { it1 ->
                                context?.contentResolver?.delete(
                                    it1,
                                    "${MediaStore.Video.Media._ID} = ?",
                                    arrayOf(it.videoId.toString())
                                )
                            }
                        } else {
                            File(it.path!!).delete()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Something went wrong with ${it.videoName}", Toast.LENGTH_SHORT).show()
                    }
                }
                downloadedItems.clear()
            },
            itemUi = { item ->
                ListItem(
                    modifier = Modifier.padding(5.dp),
                    icon = {
                        Box {
                            /*convert millis to appropriate time*/
                            val runTimeString = remember {
                                val duration = item.videoDuration
                                if (duration > TimeUnit.HOURS.toMillis(1)) {
                                    String.format(
                                        "%02d:%02d:%02d",
                                        TimeUnit.MILLISECONDS.toHours(duration),
                                        TimeUnit.MILLISECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(
                                            TimeUnit.MILLISECONDS.toHours(duration)
                                        ),
                                        TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(
                                            TimeUnit.MILLISECONDS.toMinutes(duration)
                                        )
                                    )
                                } else {
                                    String.format(
                                        "%02d:%02d",
                                        TimeUnit.MILLISECONDS.toMinutes(duration),
                                        TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(
                                            TimeUnit.MILLISECONDS.toMinutes(duration)
                                        )
                                    )
                                }
                            }

                            GlideImage(
                                imageModel = item.assetFileStringUri.orEmpty(),
                                contentDescription = item.videoName,
                                contentScale = ContentScale.Crop,
                                requestBuilder = {
                                    Glide.with(LocalView.current)
                                        .asDrawable()
                                        .override(360, 480)
                                        .thumbnail(0.5f)
                                        .transform(RoundedCorners(15))
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                            )

                            Text(
                                runTimeString,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0x99000000))
                                    .border(BorderStroke(1.dp, Color(0x00000000)), shape = RoundedCornerShape(5.dp))
                            )

                        }
                    },
                    overlineText = if (requireContext().getSharedPreferences("videos", Context.MODE_PRIVATE).contains(item.path)) {
                        { Text(requireContext().getSharedPreferences("videos", Context.MODE_PRIVATE).getLong(item.path, 0).stringForTime()) }
                    } else null,
                    text = { Text(item.videoName.orEmpty()) },
                    secondaryText = { Text(item.path.orEmpty()) }
                )
            }
        ) { p, itemList ->
            Scaffold(
                modifier = Modifier.padding(p)
            ) { p1 ->
                if (items.isEmpty()) {
                    EmptyState()
                } else {
                    AnimatedLazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = p1,
                        items = itemList.fastMap { AnimatedLazyListItem(it.videoId.toString(), it) { VideoContentView(it) } }
                    )
                }
                //val videos by updateAnimatedItemsState(newList = itemList)

                /*LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = p1,
                    state = rememberLazyListState(),
                    modifier = Modifier.padding(5.dp)
                ) {
                    animatedItems(
                        videos,
                        enterTransition = slideInHorizontally(initialOffsetX = { x -> x / 2 }),
                        exitTransition = slideOutHorizontally()
                    ) { i -> VideoContentView(i) }
                }*/
            }
        }
    }

    @Composable
    private fun EmptyState() {

        Box(modifier = Modifier.fillMaxSize()) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                tonalElevation = 5.dp,
                shape = RoundedCornerShape(5.dp)
            ) {

                Column(modifier = Modifier) {

                    Text(
                        text = stringResource(id = R.string.get_started),
                        style = M3MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = stringResource(id = R.string.download_a_video),
                        style = M3MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Button(
                        onClick = {
                            findNavController().popBackStack()
                            (activity as? BaseMainActivity)?.goToScreen(BaseMainActivity.Screen.RECENT)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 5.dp)
                    ) { Text(text = stringResource(id = R.string.go_download)) }

                }

            }
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @ExperimentalAnimationApi
    @ExperimentalMaterialApi
    @Composable
    private fun VideoContentView(item: VideoContent) {
        val showDialog = remember { mutableStateOf(false) }

        SlideToDeleteDialog(showDialog = showDialog, video = item)

        val dismissState = rememberDismissState(
            confirmStateChange = {
                if (it == DismissValue.DismissedToEnd) {
                    if (MainActivity.cast.isCastActive()) {
                        MainActivity.cast.loadMedia(
                            File(item.path!!),
                            context?.getSharedPreferences("videos", Context.MODE_PRIVATE)?.getLong(item.assetFileStringUri, 0) ?: 0L,
                            null, null
                        )
                    } else {
                        context?.startActivity(
                            Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra("showPath", item.assetFileStringUri)
                                putExtra("showName", item.videoName)
                                putExtra("downloadOrStream", true)
                                data = item.assetFileStringUri?.toUri()
                            }
                        )
                    }
                } else if (it == DismissValue.DismissedToStart) {
                    showDialog.value = true
                }
                false
            }
        )

        SwipeToDismiss(
            state = dismissState,
            background = {
                val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                val color by animateColorAsState(
                    when (dismissState.targetValue) {
                        DismissValue.Default -> Color.Transparent
                        DismissValue.DismissedToEnd -> Color.Green
                        DismissValue.DismissedToStart -> Color.Red
                    }
                )
                val alignment = when (direction) {
                    DismissDirection.StartToEnd -> Alignment.CenterStart
                    DismissDirection.EndToStart -> Alignment.CenterEnd
                }
                val icon = when (direction) {
                    DismissDirection.StartToEnd -> Icons.Default.PlayArrow
                    DismissDirection.EndToStart -> Icons.Default.Delete
                }
                val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f)

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.scale(scale)
                    )
                }
            }
        ) {
            androidx.compose.material3.ElevatedCard(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = rememberRipple(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (MainActivity.cast.isCastActive()) {
                            MainActivity.cast.loadMedia(
                                File(item.path!!),
                                context
                                    ?.getSharedPreferences("videos", Context.MODE_PRIVATE)
                                    ?.getLong(item.assetFileStringUri, 0) ?: 0L,
                                null, null
                            )
                        } else {
                            context?.startActivity(
                                Intent(context, VideoPlayerActivity::class.java).apply {
                                    putExtra("showPath", item.assetFileStringUri)
                                    putExtra("showName", item.videoName)
                                    putExtra("downloadOrStream", true)
                                    data = item.assetFileStringUri?.toUri()
                                }
                            )
                        }
                    }
            ) {
                Row {
                    Box {
                        /*convert millis to appropriate time*/
                        val runTimeString = remember {
                            val duration = item.videoDuration
                            if (duration > TimeUnit.HOURS.toMillis(1)) {
                                String.format(
                                    "%02d:%02d:%02d",
                                    TimeUnit.MILLISECONDS.toHours(duration),
                                    TimeUnit.MILLISECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(duration)),
                                    TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
                                )
                            } else {
                                String.format(
                                    "%02d:%02d",
                                    TimeUnit.MILLISECONDS.toMinutes(duration),
                                    TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
                                )
                            }
                        }

                        GlideImage(
                            imageModel = item.assetFileStringUri.orEmpty(),
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            requestBuilder = {
                                Glide.with(LocalView.current)
                                    .asDrawable()
                                    .thumbnail(0.5f)
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(ComposableUtils.IMAGE_HEIGHT, ComposableUtils.IMAGE_WIDTH),
                            failure = { Text(text = "image request failed.") }
                        )

                        Text(
                            runTimeString,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color(0x99000000))
                                .border(BorderStroke(1.dp, Color(0x00000000)), shape = RoundedCornerShape(bottomEnd = 5.dp))
                        )

                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, top = 4.dp)
                    ) {
                        val shared = requireContext().getSharedPreferences("videos", Context.MODE_PRIVATE)
                        if (shared.contains(item.assetFileStringUri))
                            Text(shared.getLong(item.assetFileStringUri, 0).stringForTime(), style = M3MaterialTheme.typography.labelMedium)
                        Text(item.videoName.orEmpty(), style = M3MaterialTheme.typography.titleSmall)
                        Text(item.path.orEmpty(), style = M3MaterialTheme.typography.bodyMedium, fontSize = 10.sp)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(horizontal = 2.dp)
                    ) {

                        var showDropDown by remember { mutableStateOf(false) }

                        val dropDownDismiss = { showDropDown = false }

                        androidx.compose.material3.DropdownMenu(
                            expanded = showDropDown,
                            onDismissRequest = dropDownDismiss
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    showDialog.value = true
                                },
                                text = { Text(stringResource(R.string.remove)) }
                            )
                        }

                        IconButton(onClick = { showDropDown = true }) { Icon(Icons.Default.MoreVert, null) }
                    }
                }
            }
        }
    }
}
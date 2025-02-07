package com.programmersbox.novelworld

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.text.HtmlCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.textview.MaterialTextView
import com.programmersbox.favoritesdatabase.ChapterWatched
import com.programmersbox.favoritesdatabase.ItemDatabase
import com.programmersbox.gsonutils.fromJson
import com.programmersbox.helpfulutils.battery
import com.programmersbox.helpfulutils.enableImmersiveMode
import com.programmersbox.helpfulutils.timeTick
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.Storage
import com.programmersbox.rxutils.invoke
import com.programmersbox.sharedutils.FirebaseDb
import com.programmersbox.uiviews.GenericInfo
import com.programmersbox.uiviews.utils.*
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

class ReadingActivity : ComponentActivity() {

    private val genericInfo by inject<GenericInfo>()
    private val disposable = CompositeDisposable()

    private val model by lazy {
        intent.getStringExtra("currentChapter")
            ?.fromJson<ChapterModel>(ChapterModel::class.java to ChapterModelDeserializer(genericInfo))
            ?.getChapterInfo()
            ?.map { it.mapNotNull(Storage::link) }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.doOnError { Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show() }
    }

    private val ad by lazy { AdRequest.Builder().build() }

    class ReadViewModel(activity: ComponentActivity, genericInfo: GenericInfo) : ViewModel() {

        private val dao by lazy { ItemDatabase.getInstance(activity).itemDao() }

        private val disposable = CompositeDisposable()

        val list by lazy { ChapterList(activity, genericInfo).get().orEmpty() }

        private val novelUrl by lazy { activity.intent.getStringExtra("novelInfoUrl") ?: "" }
        val title by lazy { activity.intent.getStringExtra("novelTitle") ?: "" }

        var currentChapter: Int by mutableStateOf(0)

        var batteryColor by mutableStateOf(androidx.compose.ui.graphics.Color.White)
        var batteryIcon by mutableStateOf(BatteryInformation.BatteryViewType.UNKNOWN)
        var batteryPercent by mutableStateOf(0f)

        val batteryInformation by lazy { BatteryInformation(activity) }

        init {
            batteryInformation.composeSetup(
                disposable,
                androidx.compose.ui.graphics.Color.White
            ) {
                batteryColor = it.first
                batteryIcon = it.second
            }

            val url = activity.intent.getStringExtra("novelUrl") ?: ""
            currentChapter = list.indexOfFirst { l -> l.url == url }
        }

        val pageList = mutableStateOf("")
        var isLoadingPages = mutableStateOf(false)
            private set

        fun addChapterToWatched(newChapter: Int, chapter: () -> Unit) {
            currentChapter = newChapter
            list.getOrNull(newChapter)?.let { item ->
                ChapterWatched(item.url, item.name, novelUrl)
                    .let {
                        Completable.mergeArray(
                            FirebaseDb.insertEpisodeWatched(it),
                            dao.insertChapter(it)
                        )
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(chapter)
                    .addTo(disposable)

                item
                    .getChapterInfo()
                    .map { it.mapNotNull(Storage::link) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { pageList.value = "" }
                    .subscribeBy { pages: List<String> -> pageList.value = pages.firstOrNull().orEmpty() }
                    .addTo(disposable)
            }
        }

        override fun onCleared() {
            super.onCleared()
            disposable.dispose()
        }

    }

    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalMaterialApi::class,
        ExperimentalComposeUiApi::class,
        ExperimentalAnimationApi::class,
        ExperimentalFoundationApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        setContent {

            val readVm: ReadViewModel = viewModel(factory = factoryCreate { ReadViewModel(this, genericInfo) })

            LaunchedEffect(Unit) { loadPages(readVm, model) }

            M3MaterialTheme(currentColorScheme) {

                DisposableEffect(LocalContext.current) {
                    val batteryInfo = battery {
                        readVm.batteryPercent = it.percent
                        readVm.batteryInformation.batteryLevelAlert(it.percent)
                        readVm.batteryInformation.batteryInfoItem(it)
                    }
                    onDispose { unregisterReceiver(batteryInfo) }
                }

                val scope = rememberCoroutineScope()
                val swipeState = rememberSwipeRefreshState(isRefreshing = readVm.isLoadingPages.value)

                var showInfo by remember { mutableStateOf(false) }

                var settingsPopup by remember { mutableStateOf(false) }

                if (settingsPopup) {
                    AlertDialog(
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        onDismissRequest = { settingsPopup = false },
                        title = { Text(stringResource(R.string.settings)) },
                        text = {
                            Column(
                                modifier = Modifier.padding(5.dp)
                            ) {
                                SliderSetting(
                                    scope = scope,
                                    settingIcon = Icons.Default.BatteryAlert,
                                    settingTitle = R.string.battery_alert_percentage,
                                    settingSummary = R.string.battery_default,
                                    preference = BATTERY_PERCENT,
                                    initialValue = runBlocking { dataStore.data.first()[BATTERY_PERCENT] ?: 20 },
                                    range = 1f..100f,
                                    steps = 0
                                )
                            }
                        },
                        confirmButton = { TextButton(onClick = { settingsPopup = false }) { Text(stringResource(R.string.ok)) } }
                    )
                }

                fun showToast() {
                    runOnUiThread { Toast.makeText(this, R.string.addedChapterItem, Toast.LENGTH_SHORT).show() }
                }

                val showItems = showInfo

                val scaffoldState = rememberBottomSheetScaffoldState()

                BackHandler(scaffoldState.drawerState.isOpen) {
                    scope.launch {
                        when {
                            scaffoldState.drawerState.isOpen -> scaffoldState.drawerState.close()
                        }
                    }
                }

                val contentScrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }

                //56 is the bottom app bar size
                //16 is the scaffold padding

                val topBarHeight = 28.dp
                val topBarHeightPx = with(LocalDensity.current) { topBarHeight.roundToPx().toFloat() }
                val topBarOffsetHeightPx = remember { mutableStateOf(0f) }

                val toolbarHeight = 56.dp
                val toolbarHeightPx = with(LocalDensity.current) { toolbarHeight.roundToPx().toFloat() }
                val toolbarOffsetHeightPx = remember { mutableStateOf(0f) }
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection by contentScrollBehavior.nestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val delta = available.y

                            val newOffset = toolbarOffsetHeightPx.value + delta
                            toolbarOffsetHeightPx.value = newOffset.coerceIn(-toolbarHeightPx, 0f)

                            val newTopOffset = topBarOffsetHeightPx.value + delta
                            topBarOffsetHeightPx.value = newTopOffset.coerceIn(-topBarHeightPx, 0f)
                            return contentScrollBehavior.nestedScrollConnection.onPreScroll(available, source)//Offset.Zero
                        }
                    }
                }

                BottomSheetScaffold(
                    sheetContent = {},
                    sheetPeekHeight = 0.dp,
                    sheetGesturesEnabled = false,
                    modifier = Modifier.nestedScroll(nestedScrollConnection),
                    scaffoldState = scaffoldState,
                    backgroundColor = M3MaterialTheme.colorScheme.surface,
                    contentColor = M3MaterialTheme.colorScheme.onSurface,
                    drawerContent = if (readVm.list.size > 1) {
                        {
                            val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }
                            Scaffold(
                                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                                topBar = {
                                    SmallTopAppBar(
                                        title = { Text(readVm.title) },
                                        actions = { PageIndicator(readVm.list.size - readVm.currentChapter, readVm.list.size) },
                                        scrollBehavior = scrollBehavior
                                    )
                                },
                                bottomBar = {
                                    if (!BuildConfig.DEBUG) {
                                        AndroidView(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            factory = {
                                                AdView(it).apply {
                                                    adSize = AdSize.BANNER
                                                    adUnitId = getString(R.string.ad_unit_id)
                                                    loadAd(ad)
                                                }
                                            }
                                        )
                                    }
                                }
                            ) { p ->
                                if (scaffoldState.drawerState.isOpen) {
                                    LazyColumn(
                                        state = rememberLazyListState(readVm.currentChapter.coerceIn(0, readVm.list.lastIndex)),
                                        contentPadding = p,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(readVm.list) { i, c ->

                                            var showChangeChapter by remember { mutableStateOf(false) }

                                            if (showChangeChapter) {
                                                AlertDialog(
                                                    onDismissRequest = { showChangeChapter = false },
                                                    title = { Text(stringResource(R.string.changeToChapter, c.name)) },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showChangeChapter = false
                                                                readVm.addChapterToWatched(i, ::showToast)
                                                            }
                                                        ) { Text(stringResource(R.string.yes)) }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showChangeChapter = false }) { Text(stringResource(R.string.no)) }
                                                    }
                                                )
                                            }

                                            Surface(
                                                modifier = Modifier.padding(horizontal = 5.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    animateColorAsState(
                                                        if (readVm.currentChapter == i) M3MaterialTheme.colorScheme.onSurface
                                                        else M3MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                    ).value
                                                ),
                                                shape = MaterialTheme.shapes.medium,
                                                tonalElevation = 5.dp
                                            ) {
                                                ListItem(
                                                    text = { Text(c.name) },
                                                    icon = if (readVm.currentChapter == i) {
                                                        { Icon(Icons.Default.ArrowRight, null) }
                                                    } else null,
                                                    modifier = Modifier.clickable { showChangeChapter = true }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else null
                ) { p ->
                    //TODO: If/when swipe refresh gains a swipe up to refresh, make the swipe up go to the next chapter
                    SwipeRefresh(
                        state = swipeState,
                        onRefresh = {
                            loadPages(
                                readVm,
                                readVm.list.getOrNull(readVm.currentChapter)
                                    ?.getChapterInfo()
                                    ?.map { it.mapNotNull(Storage::link) }
                                    ?.subscribeOn(Schedulers.io())
                                    ?.observeOn(AndroidSchedulers.mainThread())
                            )
                        },
                        modifier = Modifier.padding(p)
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(PaddingValues(top = topBarHeight))
                            ) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(5.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = {
                                                showInfo = !showInfo
                                                if (!showInfo) {
                                                    toolbarOffsetHeightPx.value = -toolbarHeightPx
                                                    topBarOffsetHeightPx.value = -topBarHeightPx
                                                }
                                            }
                                        ),
                                    factory = { MaterialTextView(it) },
                                    update = { it.text = HtmlCompat.fromHtml(readVm.pageList.value, HtmlCompat.FROM_HTML_MODE_COMPACT) }
                                )

                                if (readVm.currentChapter <= 0) {
                                    Text(
                                        stringResource(id = R.string.reachedLastChapter),
                                        style = M3MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center,
                                        color = M3MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.CenterHorizontally)
                                    )
                                }

                                if (!BuildConfig.DEBUG) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth(),
                                        factory = {
                                            AdView(it).apply {
                                                adSize = AdSize.BANNER
                                                adUnitId = getString(R.string.ad_unit_id)
                                                loadAd(ad)
                                            }
                                        }
                                    )
                                }
                            }

                            val animateTopBar by animateIntAsState(if (showItems) 0 else (topBarOffsetHeightPx.value.roundToInt()))

                            CenterAlignedTopAppBar(
                                scrollBehavior = contentScrollBehavior,
                                modifier = Modifier
                                    .height(topBarHeight)
                                    .align(Alignment.TopCenter)
                                    .alpha(1f - (-animateTopBar / topBarHeightPx))
                                    .offset { IntOffset(x = 0, y = animateTopBar) },
                                navigationIcon = {
                                    Row(
                                        modifier = Modifier.padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            readVm.batteryIcon.composeIcon,
                                            contentDescription = null,
                                            tint = animateColorAsState(
                                                if (readVm.batteryColor == androidx.compose.ui.graphics.Color.White) M3MaterialTheme.colorScheme.onSurface
                                                else readVm.batteryColor
                                            ).value
                                        )
                                        AnimatedContent(
                                            targetState = readVm.batteryPercent.toInt(),
                                            transitionSpec = {
                                                if (targetState > initialState) {
                                                    slideInVertically { height -> height } + fadeIn() with
                                                            slideOutVertically { height -> -height } + fadeOut()
                                                } else {
                                                    slideInVertically { height -> -height } + fadeIn() with
                                                            slideOutVertically { height -> height } + fadeOut()
                                                }
                                                    .using(SizeTransform(clip = false))
                                            }
                                        ) { targetBattery ->
                                            Text(
                                                "$targetBattery%",
                                                style = M3MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                },
                                title = {
                                    var time by remember { mutableStateOf(System.currentTimeMillis()) }

                                    DisposableEffect(LocalContext.current) {
                                        val timeReceiver = timeTick { _, _ -> time = System.currentTimeMillis() }
                                        onDispose { unregisterReceiver(timeReceiver) }
                                    }

                                    AnimatedContent(
                                        targetState = time,
                                        transitionSpec = {
                                            (slideInVertically { height -> height } + fadeIn() with
                                                    slideOutVertically { height -> -height } + fadeOut())
                                                .using(SizeTransform(clip = false))
                                        }
                                    ) { targetTime ->
                                        Text(
                                            DateFormat.getTimeFormat(LocalContext.current).format(targetTime).toString(),
                                            style = M3MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                },
                                actions = { PageIndicator(readVm.list.size - readVm.currentChapter, readVm.list.size) }
                            )

                            val animateBar by animateIntAsState(if (showItems) 0 else (-toolbarOffsetHeightPx.value.roundToInt()))

                            BottomAppBar(
                                modifier = Modifier
                                    .height(toolbarHeight)
                                    .align(Alignment.BottomCenter)
                                    .alpha(1f - (animateBar / toolbarHeightPx))
                                    .offset { IntOffset(x = 0, y = animateBar) },
                                containerColor = TopAppBarDefaults.centerAlignedTopAppBarColors()
                                    .containerColor(scrollFraction = contentScrollBehavior.scrollFraction).value,
                                contentColor = TopAppBarDefaults.centerAlignedTopAppBarColors()
                                    .titleContentColor(scrollFraction = contentScrollBehavior.scrollFraction).value
                            ) {

                                val prevShown = readVm.currentChapter < readVm.list.lastIndex
                                val nextShown = readVm.currentChapter > 0

                                AnimatedVisibility(
                                    visible = prevShown && readVm.list.size > 1,
                                    enter = expandHorizontally(expandFrom = Alignment.Start),
                                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
                                ) {
                                    PreviousButton(
                                        viewModel = readVm,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .weight(
                                                when {
                                                    prevShown && nextShown -> 8f / 3f
                                                    prevShown -> 4f
                                                    else -> 4f
                                                }
                                            ),
                                        previousChapter = ::showToast
                                    )
                                }

                                GoBackButton(
                                    modifier = Modifier
                                        .weight(
                                            animateFloatAsState(
                                                when {
                                                    prevShown && nextShown -> 8f / 3f
                                                    prevShown || nextShown -> 4f
                                                    else -> 8f
                                                }
                                            ).value
                                        )
                                )

                                AnimatedVisibility(
                                    visible = nextShown && readVm.list.size > 1,
                                    enter = expandHorizontally(),
                                    exit = shrinkHorizontally()
                                ) {
                                    NextButton(
                                        viewModel = readVm,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .weight(
                                                when {
                                                    prevShown && nextShown -> 8f / 3f
                                                    nextShown -> 4f
                                                    else -> 4f
                                                }
                                            ),
                                        nextChapter = ::showToast
                                    )
                                }

                                IconButton(
                                    onClick = { settingsPopup = true },
                                    modifier = Modifier.weight(2f)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        null,
                                        tint = M3MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ExperimentalAnimationApi
    @Composable
    private fun PageIndicator(currentPage: Int, pageCount: Int) {
        Row {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { height -> height } + fadeIn() with
                                slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() with
                                slideOutVertically { height -> height } + fadeOut()
                    }
                        .using(SizeTransform(clip = false))
                }
            ) { targetPage ->
                Text(
                    "$targetPage",
                    style = M3MaterialTheme.typography.bodyLarge,
                )
            }

            Text(
                "/$pageCount",
                style = M3MaterialTheme.typography.bodyLarge
            )
        }
    }

    private fun loadPages(viewModel: ReadViewModel, modelPath: Single<List<String>>?) {
        modelPath
            ?.doOnSubscribe {
                viewModel.isLoadingPages.value = true
                viewModel.pageList.value = ""
            }
            ?.subscribeBy {
                viewModel.pageList.value = it.firstOrNull().orEmpty()
                viewModel.isLoadingPages.value = false
            }
            ?.addTo(disposable)
    }

    @Composable
    private fun GoBackButton(modifier: Modifier = Modifier) {
        OutlinedButton(
            onClick = { finish() },
            modifier = modifier,
            border = BorderStroke(ButtonDefaults.outlinedButtonBorder.width, M3MaterialTheme.colorScheme.primary)
        ) { Text(stringResource(id = R.string.goBack), color = M3MaterialTheme.colorScheme.primary) }
    }

    @Composable
    private fun NextButton(viewModel: ReadViewModel, modifier: Modifier = Modifier, nextChapter: () -> Unit) {
        Button(
            onClick = { viewModel.addChapterToWatched(viewModel.currentChapter - 1, nextChapter) },
            modifier = modifier,
            border = BorderStroke(ButtonDefaults.outlinedButtonBorder.width, M3MaterialTheme.colorScheme.primary)
        ) { Text(stringResource(id = R.string.loadNextChapter)) }
    }

    @Composable
    private fun PreviousButton(viewModel: ReadViewModel, modifier: Modifier = Modifier, previousChapter: () -> Unit) {
        TextButton(
            onClick = { viewModel.addChapterToWatched(viewModel.currentChapter + 1, previousChapter) },
            modifier = modifier
        ) { Text(stringResource(id = R.string.loadPreviousChapter)) }
    }

    @Composable
    private fun SliderSetting(
        scope: CoroutineScope,
        settingIcon: ImageVector,
        @StringRes settingTitle: Int,
        @StringRes settingSummary: Int,
        preference: Preferences.Key<Int>,
        initialValue: Int,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0
    ) {
        ConstraintLayout(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            val (
                icon,
                title,
                summary,
                slider,
                value
            ) = createRefs()

            Icon(
                settingIcon,
                null,
                modifier = Modifier
                    .constrainAs(icon) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    }
                    .padding(8.dp)
            )

            Text(
                stringResource(settingTitle),
                style = M3MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.constrainAs(title) {
                    top.linkTo(parent.top)
                    end.linkTo(parent.end)
                    start.linkTo(icon.end, margin = 10.dp)
                    width = Dimension.fillToConstraints
                }
            )

            Text(
                stringResource(settingSummary),
                style = M3MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.constrainAs(summary) {
                    top.linkTo(title.bottom)
                    end.linkTo(parent.end)
                    start.linkTo(icon.end, margin = 10.dp)
                    width = Dimension.fillToConstraints
                }
            )

            var sliderValue by remember { mutableStateOf(initialValue.toFloat()) }

            androidx.compose.material3.Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    scope.launch { updatePref(preference, sliderValue.toInt()) }
                },
                valueRange = range,
                steps = steps,
                modifier = Modifier.constrainAs(slider) {
                    top.linkTo(summary.bottom)
                    end.linkTo(value.start)
                    start.linkTo(icon.end)
                    width = Dimension.fillToConstraints
                }
            )

            Text(
                sliderValue.toInt().toString(),
                style = M3MaterialTheme.typography.titleMedium,
                modifier = Modifier.constrainAs(value) {
                    end.linkTo(parent.end)
                    start.linkTo(slider.end)
                    centerVerticallyTo(slider)
                }
            )

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }
}
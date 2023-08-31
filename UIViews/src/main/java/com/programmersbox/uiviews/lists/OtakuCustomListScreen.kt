package com.programmersbox.uiviews.lists

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDismissState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.programmersbox.favoritesdatabase.CustomListInfo
import com.programmersbox.favoritesdatabase.ListDao
import com.programmersbox.favoritesdatabase.toDbModel
import com.programmersbox.favoritesdatabase.toItemModel
import com.programmersbox.sharedutils.AppLogo
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.BackButton
import com.programmersbox.uiviews.utils.Cached
import com.programmersbox.uiviews.utils.ComponentState
import com.programmersbox.uiviews.utils.ComposableUtils
import com.programmersbox.uiviews.utils.CustomBannerBox
import com.programmersbox.uiviews.utils.InsetSmallTopAppBar
import com.programmersbox.uiviews.utils.LightAndDarkPreviews
import com.programmersbox.uiviews.utils.LoadingDialog
import com.programmersbox.uiviews.utils.LocalCustomListDao
import com.programmersbox.uiviews.utils.LocalNavController
import com.programmersbox.uiviews.utils.LocalSourcesRepository
import com.programmersbox.uiviews.utils.M3CoverCard
import com.programmersbox.uiviews.utils.PreviewTheme
import com.programmersbox.uiviews.utils.Screen
import com.programmersbox.uiviews.utils.adaptiveGridCell
import com.programmersbox.uiviews.utils.components.BottomSheetDeleteScaffold
import com.programmersbox.uiviews.utils.components.CoilGradientImage
import com.programmersbox.uiviews.utils.components.DynamicSearchBar
import com.programmersbox.uiviews.utils.components.GradientImage
import com.programmersbox.uiviews.utils.components.ImageFlushListItem
import com.programmersbox.uiviews.utils.dataStore
import com.programmersbox.uiviews.utils.dispatchIo
import com.programmersbox.uiviews.utils.launchCatching
import com.programmersbox.uiviews.utils.navigateToDetails
import com.programmersbox.uiviews.utils.updatePref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.util.UUID

private val CUSTOM_LIST_LIST_OR_GRID = booleanPreferencesKey("custom_list_list_or_grid")

private val Context.customListOrGrid get() = dataStore.data.map { it[CUSTOM_LIST_LIST_OR_GRID] ?: true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OtakuCustomListScreen(
    isHorizontal: Boolean = false,
    listDao: ListDao = LocalCustomListDao.current,
    vm: OtakuCustomListViewModel = viewModel { OtakuCustomListViewModel(listDao, createSavedStateHandle()) },
) {
    val context = LocalContext.current
    val customListOrGrid by context.customListOrGrid.collectAsStateWithLifecycle(true)
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val customItem = vm.customItem
    val state = rememberBottomSheetScaffoldState()

    val logoDrawable = koinInject<AppLogo>().logo

    val pickDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { document -> document?.let { vm.writeToFile(it, context) } }

    val shareItem = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    var deleteList by remember { mutableStateOf(false) }

    if (deleteList) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteList = false },
            title = { Text(stringResource(R.string.delete_list_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.are_you_sure_delete_list))
                    Text(customItem?.item?.name.orEmpty())
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { vm.deleteAll() }
                            deleteList = false
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = listName == customItem?.item?.name
                ) { Text(stringResource(id = R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleteList = false }) { Text(stringResource(id = R.string.cancel)) } }
        )
    }

    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        var name by remember { mutableStateOf(customItem?.item?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.update_list_name_title)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.list_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            vm.rename(name)
                            showAdd = false
                        }
                    },
                    enabled = name.isNotEmpty()
                ) { Text(stringResource(id = R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(id = R.string.cancel)) }
            }
        )
    }

    var showLoadingDialog by remember { mutableStateOf(false) }

    LoadingDialog(
        showLoadingDialog = showLoadingDialog,
        onDismissRequest = { showLoadingDialog = false }
    )

    var showBanner by remember { mutableStateOf(false) }

    CustomBannerBox(
        showBanner = showBanner,
        bannerContent = {
            ListItem(
                leadingContent = {
                    val logo = koinInject<AppLogo>().logoId
                    CoilGradientImage(
                        model = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(it?.imageUrl)
                                .lifecycle(LocalLifecycleOwner.current)
                                .crossfade(true)
                                .placeholder(logo)
                                .error(logo)
                                .build()
                        ),
                        modifier = Modifier
                            .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                            .clip(MaterialTheme.shapes.small)
                    )
                },
                overlineContent = { Text(it?.source.orEmpty()) },
                headlineContent = { Text(it?.title.orEmpty()) },
                supportingContent = {
                    Text(
                        it?.description.orEmpty(),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 5
                    )
                },
                modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues())
            )
        },
    ) {
        BottomSheetDeleteScaffold(
            listOfItems = vm.items,
            multipleTitle = stringResource(R.string.remove_items),
            onRemove = { vm.removeItem(it) },
            onMultipleRemove = { it.forEach { i -> vm.removeItem(i) } },
            bottomScrollBehavior = scrollBehavior,
            state = state,
            topBar = {
                Surface {
                    Column {
                        InsetSmallTopAppBar(
                            title = { Text(customItem?.item?.name.orEmpty()) },
                            navigationIcon = { BackButton() },
                            actions = {
                                var showMenu by remember { mutableStateOf(false) }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_list)) },
                                        onClick = {
                                            showMenu = false
                                            pickDocumentLauncher.launch("${customItem?.item?.name}.json")
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_import_list)) },
                                        onClick = {
                                            showMenu = false
                                            showAdd = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(if (customListOrGrid) "List View" else "Grid View") },
                                        onClick = {
                                            scope.launch { context.updatePref(CUSTOM_LIST_LIST_OR_GRID, !customListOrGrid) }
                                        },
                                        leadingIcon = {
                                            if (customListOrGrid) {
                                                Icon(Icons.Default.List, null)
                                            } else {
                                                Icon(Icons.Default.GridView, null)
                                            }
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_list_title)) },
                                        onClick = {
                                            showMenu = false
                                            deleteList = true
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ),
                                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        shareItem.launchCatching(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        customItem?.list.orEmpty().joinToString("\n") { "${it.title} - ${it.url}" }
                                                    )
                                                    putExtra(Intent.EXTRA_TITLE, customItem?.item?.name.orEmpty())
                                                },
                                                context.getString(R.string.share_item, customItem?.item?.name.orEmpty())
                                            )
                                        )
                                    }
                                ) { Icon(Icons.Default.Share, null) }

                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                                Text("(${customItem?.list.orEmpty().size})")
                            },
                            scrollBehavior = scrollBehavior
                        )

                        DynamicSearchBar(
                            query = vm.searchQuery,
                            onQueryChange = vm::setQuery,
                            isDocked = isHorizontal,
                            onSearch = { vm.searchBarActive = false },
                            active = vm.searchBarActive,
                            onActiveChange = { vm.searchBarActive = it },
                            placeholder = { Text(stringResource(id = R.string.search)) },
                            trailingIcon = {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Default.Cancel, null)
                                }
                            },
                            windowInsets = WindowInsets(0.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(vm.items) { index, item ->
                                    ListItem(
                                        headlineContent = { Text(item.title) },
                                        leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                                        modifier = Modifier.clickable {
                                            vm.setQuery(item.title)
                                            vm.searchBarActive = false
                                        }
                                    )
                                    if (index != vm.items.lastIndex) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            },
            itemUi = { item ->
                ImageFlushListItem(
                    leadingContent = {
                        GradientImage(
                            model = item.imageUrl,
                            placeholder = rememberDrawablePainter(logoDrawable),
                            error = rememberDrawablePainter(logoDrawable),
                            contentScale = ContentScale.FillBounds,
                            contentDescription = item.title,
                            modifier = Modifier.size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                        )
                    },
                    overlineContent = { Text(item.source) },
                    headlineContent = { Text(item.title) },
                    supportingContent = {
                        Text(
                            item.description,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3
                        )
                    },
                )
            }
        ) { padding, ts ->
            Crossfade(customListOrGrid, label = "") { target ->
                when (target) {
                    true -> {
                        LazyColumn(
                            contentPadding = padding,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            items(ts) { item ->
                                CustomItem(
                                    item = item,
                                    logo = logoDrawable,
                                    showLoadingDialog = { showLoadingDialog = it },
                                    onDelete = { vm.removeItem(it) },
                                    onError = {
                                        scope.launch {
                                            state.snackbarHostState.currentSnackbarData?.dismiss()
                                            state.snackbarHostState.showSnackbar(
                                                "Something went wrong. Source might not be installed",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }

                    false -> {
                        LazyVerticalGrid(
                            columns = adaptiveGridCell(),
                            contentPadding = padding,
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                ts,
                                key = { it },
                                contentType = { it }
                            ) { item ->
                                CustomItemVertical(
                                    item = item,
                                    logo = logoDrawable,
                                    showLoadingDialog = { showLoadingDialog = it },
                                    onError = {
                                        scope.launch {
                                            state.snackbarHostState.currentSnackbarData?.dismiss()
                                            state.snackbarHostState.showSnackbar(
                                                "Something went wrong. Source might not be installed",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    },
                                    onShowBanner = {
                                        newItem(if (it) item else null)
                                        showBanner = it
                                    },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomItemVertical(
    item: CustomListInfo,
    logo: Drawable?,
    showLoadingDialog: (Boolean) -> Unit,
    onError: () -> Unit,
    onShowBanner: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sourceRepository = LocalSourcesRepository.current
    val navController = LocalNavController.current

    M3CoverCard(
        onLongPress = { c -> onShowBanner(c == ComponentState.Pressed) },
        imageUrl = item.imageUrl,
        name = item.title,
        placeHolder = logo,
        onClick = {
            sourceRepository
                .toSourceByApiServiceName(item.source)
                ?.apiService
                ?.let { source ->
                    Cached.cache[item.url]?.let {
                        flow {
                            emit(
                                it
                                    .toDbModel()
                                    .toItemModel(source)
                            )
                        }
                    } ?: source.getSourceByUrlFlow(item.url)
                }
                ?.dispatchIo()
                ?.onStart { showLoadingDialog(true) }
                ?.onEach {
                    showLoadingDialog(false)
                    navController.navigateToDetails(it)
                }
                ?.onCompletion { showLoadingDialog(false) }
                ?.launchIn(scope) ?: onError()
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomItem(
    item: CustomListInfo,
    logo: Drawable?,
    onDelete: (CustomListInfo) -> Unit,
    showLoadingDialog: (Boolean) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sourceRepository = LocalSourcesRepository.current
    val navController = LocalNavController.current
    var showPopup by remember { mutableStateOf(false) }

    if (showPopup) {
        val onDismiss = { showPopup = false }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.removeNoti, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        onDelete(item)
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
        )
    }

    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                showPopup = true
            }
            false
        }
    )

    SwipeToDismiss(
        modifier = modifier,
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToStart -> Color.Red
                    DismissValue.DismissedToEnd -> Color.Red
                }, label = ""
            )
            val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f, label = "")

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.scale(scale),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        dismissContent = {
            ElevatedCard(
                onClick = {
                    sourceRepository
                        .toSourceByApiServiceName(item.source)
                        ?.apiService
                        ?.let { source ->
                            Cached.cache[item.url]?.let {
                                flow {
                                    emit(
                                        it
                                            .toDbModel()
                                            .toItemModel(source)
                                    )
                                }
                            } ?: source.getSourceByUrlFlow(item.url)
                        }
                        ?.dispatchIo()
                        ?.onStart { showLoadingDialog(true) }
                        ?.onEach {
                            showLoadingDialog(false)
                            navController.navigateToDetails(it)
                        }
                        ?.onCompletion { showLoadingDialog(false) }
                        ?.launchIn(scope) ?: onError()
                },
                modifier = Modifier
                    .height(ComposableUtils.IMAGE_HEIGHT)
                    .padding(horizontal = 4.dp)
            ) {
                ImageFlushListItem(
                    leadingContent = {
                        GradientImage(
                            model = item.imageUrl,
                            placeholder = rememberDrawablePainter(logo),
                            error = rememberDrawablePainter(logo),
                            contentScale = ContentScale.FillBounds,
                            contentDescription = item.title,
                            modifier = Modifier.size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                        )
                    },
                    overlineContent = { Text(item.source) },
                    headlineContent = { Text(item.title) },
                    supportingContent = {
                        Text(
                            item.description,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3
                        )
                    },
                    trailingContent = {
                        var showDropDown by remember { mutableStateOf(false) }

                        val dropDownDismiss = { showDropDown = false }

                        DropdownMenu(
                            expanded = showDropDown,
                            onDismissRequest = dropDownDismiss
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.global_search_by_name)) },
                                onClick = {
                                    dropDownDismiss()
                                    Screen.GlobalSearchScreen.navigate(navController, item.title)
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove)) },
                                onClick = {
                                    dropDownDismiss()
                                    showPopup = true
                                }
                            )
                        }

                        IconButton(onClick = { showDropDown = true }) { Icon(Icons.Default.MoreVert, null) }
                    }
                )
            }
        }
    )
}

@LightAndDarkPreviews
@Composable
private fun CustomListScreenPreview() {
    PreviewTheme {
        val listDao: ListDao = LocalCustomListDao.current
        val vm: OtakuCustomListViewModel = viewModel {
            OtakuCustomListViewModel(listDao, SavedStateHandle())
        }
        OtakuCustomListScreen(
            listDao = listDao,
            vm = vm
        )
    }
}

@LightAndDarkPreviews
@Composable
private fun CustomItemPreview() {
    PreviewTheme {
        CustomItem(
            item = CustomListInfo(
                uuid = UUID.randomUUID(),
                title = "Title",
                description = "description",
                url = "",
                imageUrl = "",
                source = "MANGA_READ"
            ),
            logo = null,
            onDelete = {},
            showLoadingDialog = {},
            onError = {}
        )
    }
}
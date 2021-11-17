package com.programmersbox.novelworld

import android.content.Context
import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import com.google.accompanist.placeholder.material.placeholder
import com.programmersbox.favoritesdatabase.DbModel
import com.programmersbox.gsonutils.toJson
import com.programmersbox.models.ApiService
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.InfoModel
import com.programmersbox.models.ItemModel
import com.programmersbox.novel_sources.Sources
import com.programmersbox.sharedutils.AppUpdate
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.GenericInfo
import com.programmersbox.uiviews.utils.*
import org.koin.dsl.module

val appModule = module {
    single<GenericInfo> { GenericNovel(get()) }
    single { MainLogo(R.mipmap.ic_launcher) }
    single { NotificationLogo(R.mipmap.ic_launcher_foreground) }
}

class GenericNovel(val context: Context) : GenericInfo {

    override fun chapterOnClick(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {
        context.startActivity(
            Intent(context, ReadingActivity::class.java).apply {
                putExtra("currentChapter", model.toJson(ChapterModel::class.java to ChapterModelSerializer()))
                putExtra("allChapters", allChapters.toJson(ChapterModel::class.java to ChapterModelSerializer()))
                putExtra("novelTitle", model.name)
                putExtra("novelUrl", model.url)
                putExtra("novelInfoUrl", model.sourceUrl)
            }
        )
    }

    override fun sourceList(): List<ApiService> = Sources.values().toList()

    override fun toSource(s: String): ApiService? = try {
        Sources.valueOf(s)
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun downloadChapter(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {}

    override val apkString: AppUpdate.AppUpdates.() -> String? get() = { novel_file }

    @ExperimentalMaterialApi
    @Composable
    override fun ComposeShimmerItem() {
        LazyColumn {
            items(10) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    tonalElevation = 5.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .placeholder(
                                true,
                                color = androidx.compose.material3
                                    .contentColorFor(backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
                                    .copy(0.1f)
                                    .compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            )
                            .padding(5.dp)
                    )
                }
            }
        }
    }

    @ExperimentalAnimationApi
    @ExperimentalMaterialApi
    @ExperimentalFoundationApi
    @Composable
    override fun ItemListView(
        list: List<ItemModel>,
        favorites: List<DbModel>,
        listState: LazyListState,
        onLongPress: (ItemModel, ComponentState) -> Unit,
        onClick: (ItemModel) -> Unit
    ) {
        val animated by updateAnimatedItemsState(newList = list)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            animatedItems(
                animated,
                enterTransition = fadeIn(),
                exitTransition = fadeOut()
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp)
                        .combineClickableWithIndication(
                            onLongPress = { c -> onLongPress(it, c) },
                            onClick = { onClick(it) }
                        ),
                    tonalElevation = 5.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    ListItem(
                        icon = {
                            androidx.compose.material3.Icon(
                                if (favorites.fastAny { f -> f.url == it.url }) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        text = { androidx.compose.material3.Text(it.title) },
                        overlineText = { androidx.compose.material3.Text(it.source.serviceName) },
                        secondaryText = if (it.description.isNotEmpty()) {
                            { androidx.compose.material3.Text(it.description) }
                        } else null
                    )
                }
            }
        }
    }

}
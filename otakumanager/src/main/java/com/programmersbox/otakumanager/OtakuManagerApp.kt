package com.programmersbox.otakumanager

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.programmersbox.favoritesdatabase.DbModel
import com.programmersbox.manga_sources.utilities.NetworkHelper
import com.programmersbox.models.ApiService
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.InfoModel
import com.programmersbox.models.ItemModel
import com.programmersbox.sharedutils.AppUpdate
import com.programmersbox.sharedutils.FirebaseDb
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.GenericInfo
import com.programmersbox.uiviews.OtakuApp
import com.programmersbox.uiviews.utils.NotificationLogo
import com.programmersbox.uiviews.utils.shouldCheck
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import com.programmersbox.anime_sources.Sources as ASources
import com.programmersbox.manga_sources.Sources as MSources
import com.programmersbox.novel_sources.Sources as NSources

class OtakuManagerApp : OtakuApp() {
    override fun onCreated() {

        FirebaseDb.DOCUMENT_ID = "favoriteManga"
        FirebaseDb.CHAPTERS_ID = "chaptersRead"
        FirebaseDb.COLLECTION_ID = "mangaworld"
        FirebaseDb.ITEM_ID = "mangaUrl"
        FirebaseDb.READ_OR_WATCHED_ID = "chapterCount"

        loadKoinModules(appModule)
        shouldCheck = false
    }
}

val appModule = module {
    single { NetworkHelper(get()) }
    single { MainLogo(R.mipmap.ic_launcher) }
    single { NotificationLogo(R.drawable.otakumanager_logo) }
    single<GenericInfo> {
        object : GenericInfo {
            override val apkString: AppUpdate.AppUpdates.() -> String? get() = { otakumanager_file }

            override fun chapterOnClick(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {
                throw Exception("This should not be seen")
            }

            override fun sourceList(): List<ApiService> = listOf(
                ASources.values().toList(),
                MSources.values().toList(),
                NSources.values().toList()
            )
                .flatten()

            override fun toSource(s: String): ApiService? = try {
                sourceList().find { s == it.serviceName }
            } catch (e: IllegalArgumentException) {
                null
            }

            override fun downloadChapter(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {
                throw Exception("This should not be seen")
            }

            @Composable
            override fun ComposeShimmerItem() {

            }

            @Composable
            override fun ItemListView(
                list: List<ItemModel>,
                favorites: List<DbModel>,
                listState: LazyListState,
                onClick: (ItemModel) -> Unit
            ) {
            }

        }
    }
}
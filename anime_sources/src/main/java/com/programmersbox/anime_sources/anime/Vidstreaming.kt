package com.programmersbox.anime_sources.anime

import androidx.annotation.WorkerThread
import androidx.compose.ui.util.fastMap
import com.programmersbox.anime_sources.ShowApi
import com.programmersbox.anime_sources.toJsoup
import com.programmersbox.anime_sources.utilities.extractors
import com.programmersbox.anime_sources.utilities.fixUrl
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.InfoModel
import com.programmersbox.models.ItemModel
import com.programmersbox.models.Storage
import io.reactivex.Single
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import org.jsoup.nodes.Document

object Vidstreaming : VidstreamingTemplate(
    "https://vidstreaming.io",
    "popular",
    ""
) {
    override val serviceName: String get() = "VIDSTREAMING"
    override val searchUrl: String get() = "https://streamani.net"
}

object VidEmbed : VidstreamingTemplate(
    "https://vidembed.io",
    "movies",
    "series"
) {
    override val serviceName: String get() = "VIDEMBED"
    override val searchUrl: String get() = baseUrl
}

abstract class VidstreamingTemplate(
    baseUrl: String,
    allPath: String,
    recentPath: String
) : ShowApi(
    baseUrl = baseUrl,
    allPath = allPath,
    recentPath = recentPath
) {

    //override val serviceName: String get() = "VIDSTREAMING"

    abstract val searchUrl: String

    override fun getRecent(doc: Document): Single<List<ItemModel>> = Single.create {
        doc
            .select("li.video-block")
            .fastMap {
                ItemModel(
                    title = it.select("div.name").text(),
                    description = "",
                    imageUrl = it.select("div.picture").select("img").attr("abs:src"),
                    url = it.select("a").first()?.attr("abs:href").orEmpty(),
                    source = this
                )
            }
            .let(it::onSuccess)
    }

    override fun getList(doc: Document): Single<List<ItemModel>> = Single.create {
        doc
            .select("li.video-block")
            .fastMap {
                ItemModel(
                    title = it.select("div.name").text(),
                    description = "",
                    imageUrl = it.select("div.picture").select("img").attr("abs:src"),
                    url = it.select("a").first()?.attr("abs:href").orEmpty(),
                    source = this
                )
            }
            .let(it::onSuccess)
    }

    override fun getItemInfo(source: ItemModel, doc: Document): Single<InfoModel> = Single.create {
        InfoModel(
            source = this,
            title = source.title,
            url = source.url,
            alternativeNames = emptyList(),
            description = doc.select("div.post-entry").text(),
            imageUrl = source.imageUrl,
            genres = emptyList(),
            chapters = doc.select("div.video-info-left > ul.listing > li.video-block > a").fastMap {
                ChapterModel(
                    it.select("div.name").text(),
                    it.select("a").attr("abs:href"),
                    it.select("span.date").text(),
                    source.url,
                    this
                )
            }
        )
            .let(it::onSuccess)
    }

    override fun getSourceByUrl(url: String): Single<ItemModel> = Single.create {
        val doc = url.toJsoup()
        ItemModel(
            title = doc.select("div.video-details").select("span.date").text(),
            description = doc.select("div.post-entry").text(),
            imageUrl = doc
                .select("div.video-info-left > ul.listing > li.video-block > a")
                .select("div.picture")
                .select("img")
                .randomOrNull()
                ?.attr("abs:src")
                .orEmpty(),
            url = url,
            source = this
        )
            .let(it::onSuccess)
    }

    override fun searchList(searchText: CharSequence, page: Int, list: List<ItemModel>): Single<List<ItemModel>> {
        return if (searchText.isEmpty()) super.searchList(searchText, page, list)
        else Single.create<List<ItemModel>> {
            "$searchUrl/search.html?keyword=${searchText.split(" ").joinToString("%20")}".toJsoup()
                .select("li.video-block")
                .fastMap {
                    ItemModel(
                        title = it.select("div.name").text(),
                        description = "",
                        imageUrl = it.select("div.picture").select("img").attr("abs:src"),
                        url = it.select("a").first()?.attr("abs:href").orEmpty(),
                        source = this
                    )
                }
                .let(it::onSuccess)

        }
            .onErrorResumeNext(super.searchList(searchText, page, list))
    }

    override fun getChapterInfo(chapterModel: ChapterModel): Single<List<Storage>> {
        return Single.create<List<Storage>> { emitter ->

            //val e = "https://vidstreaming.io/videos/tensei-shitara-slime-datta-ken-episode-24-9".toJsoup()
            //println(e)

            val v = chapterModel.url.toJsoup().select("div.play-video").select("iframe").attr("abs:src")

            val s = v.toJsoup()

            val servers = s.select(".list-server-items > .linkserver").mapNotNull { li ->
                if (!li?.attr("data-video").isNullOrEmpty()) {
                    li.text() to fixUrl(li.attr("data-video"), baseUrl)
                } else {
                    null
                }
            }

            val i = servers
                .map { l ->
                    //println(l)
                    extractors.flatMap { e ->
                        //println(e.name)
                        if (l.second.startsWith(e.mainUrl)) {
                            //println(url + "\t" + e.name)
                            e.getUrl(l.second)
                        } else emptyList()
                    }
                }
                .filter { it.isNotEmpty() }
                .flatten()
                .distinctBy { it.link }

            //Loged.i(i)

            emitter.onSuccess(i)
        }
            .onErrorReturnItem(emptyList())
    }

    data class Xstream(val success: Boolean?, val player: Any?, val data: List<XstreamData>?, val captions: Any?, val is_vr: Boolean?)

    data class XstreamData(val file: String?, val label: String?, val type: String?)

    data class Player(
        val poster_file: String?,
        val logo_file: String?,
        val logo_position: String?,
        val logo_link: String?,
        val logo_margin: Number?,
        val aspectratio: String?,
        val powered_text: String?,
        val powered_url: String?,
        val css_background: String?,
        val css_text: String?,
        val css_menu: String?,
        val css_mntext: String?,
        val css_caption: String?,
        val css_cttext: String?,
        val css_ctsize: Number?,
        val css_ctopacity: Number?,
        val css_ctedge: String?,
        val css_icon: String?,
        val css_ichover: String?,
        val css_tsprogress: String?,
        val css_tsrail: String?,
        val css_button: String?,
        val css_bttext: String?,
        val opt_autostart: Boolean?,
        val opt_title: Boolean?,
        val opt_quality: Boolean?,
        val opt_caption: Boolean?,
        val opt_download: Boolean?,
        val opt_sharing: Boolean?,
        val opt_playrate: Boolean?,
        val opt_mute: Boolean?,
        val opt_loop: Boolean?,
        val opt_vr: Boolean?,
        val opt_cast: Boolean?,
        val opt_nodefault: Boolean?,
        val opt_forceposter: Boolean?,
        val opt_parameter: Boolean?,
        val restrict_domain: String?,
        val restrict_action: String?,
        val restrict_target: String?,
        val resume_enable: Boolean?,
        val resume_text: String?,
        val resume_yes: String?,
        val resume_no: String?,
        val adb_enable: Boolean?,
        val adb_offset: Number?,
        val adb_text: String?,
        val ads_adult: Boolean?,
        val ads_pop: Boolean?,
        val ads_vast: Boolean?,
        val ads_free: Boolean?,
        val trackingId: String?,
        val income: Boolean?,
        val incomePop: Boolean?,
        val logger: String?,
        val revenue: String?,
        val revenue_fallback: String?,
        val revenue_track: String?
    )

    @WorkerThread
    private fun getApiPost(url: String, builder: okhttp3.Request.Builder.() -> Unit = {}): String? {
        val request = okhttp3.Request.Builder()
            .url(url)
            .apply(builder)
            .post(object : RequestBody() {
                override fun contentType(): MediaType? = "application/json".toMediaTypeOrNull()

                override fun writeTo(sink: BufferedSink) {
                }

            })
            .build()
        val response = OkHttpClient().newCall(request).execute()
        return if (response.code == 200) response.body!!.string() else null
    }

    @WorkerThread
    private fun getApiPost(url: HttpUrl, builder: okhttp3.Request.Builder.() -> Unit = {}): String? {
        val request = okhttp3.Request.Builder()
            .url(url)
            .apply(builder)
            .post(object : RequestBody() {
                override fun contentType(): MediaType? = "application/json".toMediaTypeOrNull()

                override fun writeTo(sink: BufferedSink) {
                }

            })
            .build()
        val response = OkHttpClient().newCall(request).execute()
        return if (response.code == 200) response.body!!.string() else null
    }

}

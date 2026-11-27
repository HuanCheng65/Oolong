package oolong.core.network.search

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.searchSug.SearchSugRequest
import oolong.core.network.protos.searchSug.SearchSugRequestData
import java.net.URLEncoder

/** Searching Tieba for forums, threads and users, and inside one forum for its threads. */
class SearchRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val protoProfile = TiebaProfile.PROTO
    private val webProfile = TiebaProfile.WEB
    private val sugService = network.service<SearchSugService>(protoProfile)
    private val webService = network.service<SearchWebService>(webProfile)

    /** Keywords that start like [keyword]. */
    suspend fun suggestions(keyword: String): List<String> {
        val request = SearchSugRequest(
            SearchSugRequestData(common = commonParams.protobuf(protoProfile), word = keyword, isforum = "0")
        )
        return sugService.suggestions(ProtobufBody(request, commonParams.stokenField())).data_?.list.orEmpty()
    }

    suspend fun forums(keyword: String): SearchForumsResponse.Data =
        webService.forums(keyword, globalReferer(keyword)).checked().data ?: SearchForumsResponse.Data()

    suspend fun users(keyword: String): SearchUsersResponse.Data =
        webService.users(keyword, globalReferer(keyword)).checked().data ?: SearchUsersResponse.Data()

    /**
     * A page (from 1) of threads and replies across Tieba.
     *
     * @param sort one of the `SORT_` constants of [SearchSort]
     */
    suspend fun threads(keyword: String, page: Int, sort: Int): SearchThreadsResponse.Data =
        webService.threads(
            keyword = keyword,
            page = page,
            sort = sort,
            filter = 1,
            pageSize = null,
            forumName = null,
            ct = 1,
            useZonghe = 1,
            clientVersion = "99.9.101",
            referer = globalReferer(keyword),
        ).checked().data ?: SearchThreadsResponse.Data()

    /**
     * A page (from 1) of threads and replies in one forum.
     *
     * @param sort [SearchSort.IN_FORUM_NEWEST] or [SearchSort.IN_FORUM_RELEVANT]
     * @param onlyThreads whether to leave out replies
     */
    suspend fun threadsInForum(
        keyword: String,
        forumName: String,
        forumId: Long,
        page: Int,
        sort: Int,
        onlyThreads: Boolean,
    ): SearchThreadsResponse.Data {
        val now = clock()
        val referer = "https://tieba.baidu.com/mo/q/hybrid-usergrow-search/searchGlobal?entryPage=frs&loadingSignal=1" +
            "&forumName=${forumName.encoded()}&forumId=$forumId&customfullscreen=1&nonavigationbar=1" +
            "&cuid=${device.cuid}&cuid_galaxy2=${device.cuid}&cuid_gid=&timestamp=$now" +
            "&_client_version=${webProfile.version}&_client_type=2"
        return webService.threads(
            keyword = keyword,
            page = page,
            sort = sort,
            filter = if (onlyThreads) 1 else 2,
            pageSize = IN_FORUM_PAGE_SIZE,
            forumName = forumName,
            ct = 2,
            useZonghe = null,
            clientVersion = webProfile.version,
            referer = referer,
        ).checked().data ?: SearchThreadsResponse.Data()
    }

    private fun globalReferer(keyword: String) =
        "https://tieba.baidu.com/mo/q/hybrid/search?keyword=${keyword.encoded()}&_webview_time=${clock()}"

    private fun <T : SearchResponse> T.checked(): T {
        if (no != 0) throw TiebaServerException(no, error, this)
        return this
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")

    private companion object {
        const val IN_FORUM_PAGE_SIZE = 20
    }
}

/** How search results are ordered. */
object SearchSort {
    const val NEWEST = 5
    const val OLDEST = 0
    const val RELEVANT = 2

    const val IN_FORUM_NEWEST = 1
    const val IN_FORUM_RELEVANT = 2
}

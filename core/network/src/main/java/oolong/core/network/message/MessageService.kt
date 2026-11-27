package oolong.core.network.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import okhttp3.RequestBody
import oolong.core.network.protos.agreeMe.AgreeMeResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface MessageService {
    @POST("c/s/msg")
    suspend fun reminder(@Body body: RequestBody): MessageReminderResponse

    @POST("c/u/feed/replyme")
    suspend fun replyMe(@Body body: RequestBody): FeedResponse

    @POST("c/u/feed/atme")
    suspend fun atMe(@Body body: RequestBody): FeedResponse
}

interface MessageProtoService {
    @POST("c/u/feed/agreeme?cmd=309593&format=protobuf")
    suspend fun agreeMe(@Body body: RequestBody): AgreeMeResponse
}

/** A page of replies to the user, or of posts that mention them. */
@Serializable
data class FeedResponse(
    @Serializable(with = FeedItemsSerializer::class) @SerialName("reply_list") val replyList: List<FeedItem> = emptyList(),
    @Serializable(with = FeedItemsSerializer::class) @SerialName("at_list") val atList: List<FeedItem> = emptyList(),
    val page: Page? = null,
) {
    @Serializable
    data class Page(
        @SerialName("current_page") val currentPage: String? = null,
        @SerialName("has_more") val hasMore: String? = null,
    )
}

@Serializable
data class FeedItem(
    /** "1" for a reply inside a post, as opposed to a post in the thread. */
    @SerialName("is_floor") val isFloor: String? = null,
    val title: String? = null,
    val content: String? = null,
    @SerialName("quote_content") val quoteContent: String? = null,
    val replyer: User? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("post_id") val postId: String? = null,
    /** The post or the reply inside a post that this one answers. */
    @SerialName("quote_pid") val quotePid: String? = null,
    val time: String? = null,
) {
    @Serializable
    data class User(
        val id: String? = null,
        val name: String? = null,
        @SerialName("name_show") val nameShow: String? = null,
        val portrait: String? = null,
    )
}

/** An empty list comes as an empty string rather than an empty array. */
internal object FeedItemsSerializer : JsonTransformingSerializer<List<FeedItem>>(ListSerializer(FeedItem.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = element as? JsonArray ?: JsonArray(emptyList())
}

@Serializable
data class MessageReminderResponse(val message: Counts? = null) {
    /** Unread counts, as strings like everything the server sends. */
    @Serializable
    data class Counts(
        val agree: String? = null,
        @SerialName("replyme") val replyMe: String? = null,
        @SerialName("atme") val atMe: String? = null,
        val fans: String? = null,
        val pletter: String? = null,
        val bookmark: String? = null,
    )
}

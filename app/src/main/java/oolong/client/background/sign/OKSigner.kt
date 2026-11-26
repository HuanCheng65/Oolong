package oolong.client.background.sign

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import oolong.client.data.account.AccountRepository
import oolong.client.data.error.getErrorCode
import oolong.client.data.error.getErrorMessage
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.Account
import oolong.client.model.SignDataBean
import oolong.core.network.forum.ForumGuideResponse
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.sign.MSignResponse
import oolong.core.network.sign.SignRemoteDataSource
import oolong.core.network.sign.SignResponse
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadLocalRandom
import kotlin.properties.Delegates

abstract class IOKSigner(
    context: Context,
    protected val signs: SignRemoteDataSource,
) {
    private val contextWeakReference: WeakReference<Context> = WeakReference(context)

    val context: Context
        get() = contextWeakReference.get()!!

    abstract suspend fun start(): Boolean

    fun signFlow(signDataBean: SignDataBean): Flow<SignResponse> = flow {
        emit(signs.sign(signDataBean.forumId.toLong(), signDataBean.forumName, signDataBean.tbs))
    }

    fun getSignDelay(): Long {
        return if (Settings.of(context)[SettingKeys.OksignSlowMode]) {
            ThreadLocalRandom.current().nextInt(3500, 8000).toLong()
        } else {
            2000
        }
    }
}

class SingleAccountSigner(
    context: Context,
    private val account: Account,
    private val accounts: AccountRepository,
    signs: SignRemoteDataSource,
    private val likes: LikeRemoteDataSource,
) : IOKSigner(context, signs) {
    companion object {
        const val TAG = "SingleAccountSigner"
    }

    private val signData: MutableList<SignDataBean> = mutableListOf()
    private var position = 0
    private var successCount = 0
    private var totalCount = 0
    private var mSignCount = 0

    var lastFailure: Throwable? = null

    private var mProgressListener: ProgressListener? = null

    fun setProgressListener(listener: ProgressListener?): SingleAccountSigner {
        mProgressListener = listener
        return this
    }

    /** Every forum the user follows, page by page. */
    private suspend fun followedForums(): List<ForumGuideResponse.LikeForum> {
        val forums = mutableListOf<ForumGuideResponse.LikeForum>()
        var pageNo = 1
        do {
            val page = likes.forumGuide(pageNo++)
            val pageForums = page.likeForum.orEmpty()
            forums += pageForums
        } while (page.hasMore && pageForums.isNotEmpty())
        return forums
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun start(): Boolean {
        var result = false
        signData.clear()
        var userName: String by Delegates.notNull()
        var tbs: String by Delegates.notNull()
        Log.i(TAG, "start")
        flow { emit(accounts.refresh(account)) }
            .flatMapConcat { account ->
                userName = account.name
                tbs = account.tbs
                flow { emit(signs.forumList()) }
            }
            .zip(
                flow { emit(followedForums()) }
            ) { getForumListBean, likeForums ->
                val useMSign = Settings.of(context)[SettingKeys.OksignUseOfficialOksign]
                val mSignLevel = getForumListBean.level.toIntOrNull() ?: 0
                val mSignMax = getForumListBean.msignStepNum.toIntOrNull() ?: 0
                signData.addAll(
                    likeForums
                        .filter { it.isSign != "1" }
                        .map {
                            SignDataBean(
                                it.forumName,
                                it.forumId,
                                userName,
                                tbs,
                                (it.levelId?.toIntOrNull() ?: 0) >= mSignLevel && signData.size < mSignMax
                            )
                        }
                )
                totalCount = signData.size
                mSignCount = 0
                (if (useMSign) {
                    val mSignData = signData.filter { it.canUseMSign }
                    flow {
                        // At most MAX_MSIGN_FORUMS forums per request
                        val results = mSignData.map { it.forumId.toLong() }
                            .chunked(SignRemoteDataSource.MAX_MSIGN_FORUMS)
                            .map { signs.mSign(it, tbs) }
                        results.firstOrNull { it.info.isEmpty() }?.refusal?.let { refusal ->
                            withContext(Dispatchers.Main) { mProgressListener?.onMSignRefused(refusal) }
                        }
                        emit(results.flatMap { it.info })
                    }
                } else {
                    flow { emit(emptyList()) }
                })
                    .onStart {
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onStart(totalCount)
                        }
                    }
                    .catch { emit(emptyList()) }
            }
            .flattenConcat()
            .flatMapConcat { mSignInfo ->
                val newSignData = if (mSignInfo.isNotEmpty()) {
                    val mSignInfoMap = mutableMapOf<String, MSignResponse.ForumResult>()
                    mSignInfo.forEach { info ->
                        info.forumId?.let { mSignInfoMap[it] = info }
                    }
                    val signedCount = mSignInfo.filter { it.signed == "1" }.size
                    successCount += signedCount
                    signData
                        .filter { !it.canUseMSign || mSignInfoMap[it.forumId]?.signed != "1" }
                } else {
                    signData.toList()
                }
                mSignCount = totalCount - newSignData.size
                if (mSignCount > 0) {
                    val signedIds = mSignInfo.filter { it.signed == "1" }.mapNotNull { it.forumId?.toLongOrNull() }
                    withContext(Dispatchers.Main) { mProgressListener?.onMSigned(signedIds, mSignCount) }
                }
                newSignData
                    .asFlow()
                    .onEach {
                        position = signData.indexOf(it)
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onProgressStart(
                                it,
                                position,
                                signData.size
                            )
                        }
                    }
                    .onEmpty {
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onFinish(
                                successCount == totalCount,
                                successCount,
                                totalCount
                            )
                        }
                        result = true
                    }
                    .flatMapConcat { signData ->
                        // A banned or closed forum must not stop signing the remaining forums
                        signFlow(signData).catch { e ->
                            lastFailure = e
                            mProgressListener?.onFailure(
                                position,
                                totalCount,
                                e.getErrorCode(),
                                e.getErrorMessage(context)
                            )
                            delay(getSignDelay())
                        }
                    }
            }
            .catch { e ->
                result = false
                lastFailure = e
                mProgressListener?.onFailure(
                    position,
                    totalCount,
                    e.getErrorCode(),
                    e.getErrorMessage(context)
                )
                delay(getSignDelay())
            }
            .onCompletion {
                withContext(Dispatchers.Main) {
                    mProgressListener?.onFinish(
                        successCount == totalCount,
                        successCount,
                        totalCount
                    )
                }
            }
            .collect {
                result = true
                successCount += 1
                mProgressListener?.onProgressFinish(
                    signData[position],
                    it,
                    position,
                    totalCount
                )
                delay(getSignDelay())
            }
        return result
    }
}

interface ProgressListener {
    /** The forums [forumIds] were signed all at once, [done] of the run's forums with them. */
    fun onMSigned(forumIds: List<Long>, done: Int) {}

    fun onStart(
        total: Int
    )

    fun onProgressStart(
        signDataBean: SignDataBean,
        current: Int,
        total: Int
    )

    fun onProgressFinish(
        signDataBean: SignDataBean,
        signResultBean: SignResponse,
        current: Int,
        total: Int
    )

    fun onFinish(
        success: Boolean,
        signedCount: Int,
        total: Int
    )

    fun onFailure(
        current: Int,
        total: Int,
        errorCode: Int,
        errorMsg: String
    )

    /** Official one-click sign-in refused the forums for [reason]; they are signed in to one by one. */
    fun onMSignRefused(reason: String) {}
}

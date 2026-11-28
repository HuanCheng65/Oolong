package oolong.client.data.identity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import oolong.client.App
import oolong.client.BuildConfig
import oolong.client.data.account.AccountRepository
import oolong.client.data.identity.ClientUtils
import oolong.client.data.identity.CuidUtils
import oolong.client.data.identity.DeviceUtils
import oolong.client.data.identity.MobileInfoUtil
import oolong.client.data.identity.UIDUtil
import oolong.client.diagnostics.RequestRecorder
import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.account.AccountRemoteDataSource
import oolong.core.network.agree.AgreeRemoteDataSource
import oolong.core.network.explore.ExploreRemoteDataSource
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.identity.InstallIdentity
import oolong.core.network.identity.NetworkType
import oolong.core.network.identity.Session
import oolong.core.network.identity.SessionProvider
import oolong.core.network.message.MessageRemoteDataSource
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.search.SearchRemoteDataSource
import oolong.core.network.sign.SignRemoteDataSource
import oolong.core.network.thread.ThreadRemoteDataSource
import oolong.core.network.upload.UploadRemoteDataSource
import oolong.core.network.user.UserRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the identities from the same storage the app has always used, so existing installs keep
 * their `cuid`, `c3_aid` and client id.
 */
private class AppDeviceIdentity(private val context: Context) : DeviceIdentity {
    override val androidId: String get() = UIDUtil.getAndroidId("")
    override val cuid: String get() = CuidUtils.getNewCuid()
    override val c3Aid: String get() = UIDUtil.getAid()
    override val imei: String get() = MobileInfoUtil.getIMEI(context)
    override val oaid: String get() = UIDUtil.getOAID()
    override val model: String get() = Build.MODEL
    override val brand: String get() = Build.BRAND
    override val osVersion: String get() = Build.VERSION.RELEASE
    override val screenWidth: Int get() = getScreenWidth()
    override val screenHeight: Int get() = getScreenHeight()
    override val screenDensity: Float get() = getScreenDensity()
    override val networkType: Int get() = currentNetworkType(context)
    override val carrier: Int get() = carrierOf((context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simOperator.orEmpty())
    override val deviceScore: Float by lazy { DeviceUtils.getNormalizedDeviceScore() }
    override val webViewUserAgent: String get() = getUserAgent()
}

@Suppress("DEPRECATION") // NetworkInfo.subtype is the only way to get the generation without READ_PHONE_STATE
private fun currentNetworkType(context: Context): Int {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkType.ofMobile(connectivityManager.getNetworkInfo(network)?.subtype ?: -1)

        else -> NetworkType.MOBILE_4G
    }
}

private object AppInstallIdentity : InstallIdentity {
    override val clientId: String? get() = ClientUtils.clientId
    override val sampleId: String? get() = ClientUtils.sampleId
    override val baiduId: String? get() = ClientUtils.baiduId
    override val activeTimestamp: Long get() = ClientUtils.activeTimestamp
    override val firstInstallTime: Long get() = App.Config.appFirstInstallTime
    override val lastUpdateTime: Long get() = App.Config.appLastUpdateTime
}

/** The current account's session. Lazy, as logging in needs the network that needs this. */
class AccountSessionProvider @Inject constructor(
    private val accounts: dagger.Lazy<AccountRepository>,
) : SessionProvider {
    override fun currentSession(): Session? = accounts.get().current.value?.let {
        Session(
            uid = it.uid,
            name = it.name,
            bduss = it.bduss,
            stoken = it.sToken.takeIf { token -> token.isNotEmpty() },
            tbs = it.tbs.takeIf { tbs -> tbs.isNotEmpty() },
            zid = it.zid?.takeIf { zid -> zid.isNotEmpty() },
        )
    }

    override fun updateTbs(tbs: String) {
        val account = accounts.get().current.value?.copy() ?: return
        if (account.tbs == tbs) return
        account.tbs = tbs
        accounts.get().saveAsync(account)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkIdentityModule {
    @Provides
    @Singleton
    fun provideDeviceIdentity(@ApplicationContext context: Context): DeviceIdentity =
        AppDeviceIdentity(context)

    @Provides
    fun provideInstallIdentity(): InstallIdentity = AppInstallIdentity

    @Provides
    fun provideSessionProvider(provider: AccountSessionProvider): SessionProvider = provider

    @Provides
    @Singleton
    fun provideCommonParams(
        device: DeviceIdentity,
        install: InstallIdentity,
        sessionProvider: SessionProvider,
    ): CommonParams = CommonParams(device, install, sessionProvider)

    @Provides
    @Singleton
    fun provideTiebaNetwork(
        commonParams: CommonParams,
        sessionProvider: SessionProvider,
    ): TiebaNetwork = TiebaNetwork(
        commonParams,
        sessionProvider,
        networkInterceptors = if (BuildConfig.DEBUG) listOf(RequestRecorder) else emptyList(),
    )

    @Provides
    @Singleton
    fun provideForumRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ForumRemoteDataSource = ForumRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideSignRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): SignRemoteDataSource = SignRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideAgreeRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): AgreeRemoteDataSource = AgreeRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideLikeRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): LikeRemoteDataSource = LikeRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideThreadRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ThreadRemoteDataSource = ThreadRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideUserRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): UserRemoteDataSource = UserRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideExploreRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ExploreRemoteDataSource = ExploreRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideAccountRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): AccountRemoteDataSource = AccountRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideSearchRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): SearchRemoteDataSource = SearchRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideMessageRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): MessageRemoteDataSource = MessageRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun providePostRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        sessionProvider: SessionProvider,
    ): PostRemoteDataSource = PostRemoteDataSource(network, commonParams, sessionProvider)

    @Provides
    @Singleton
    fun provideUploadRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): UploadRemoteDataSource = UploadRemoteDataSource(network, commonParams)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LegacyNetworkEntryPoint {
    fun sessionProvider(): SessionProvider
    fun accountRemoteDataSource(): AccountRemoteDataSource
    fun threadRemoteDataSource(): ThreadRemoteDataSource
}

private val legacyEntryPoint: LegacyNetworkEntryPoint by lazy {
    EntryPointAccessors.fromApplication(App.INSTANCE, LegacyNetworkEntryPoint::class.java)
}

/** For the legacy singletons, which can't be injected. */
internal val legacySessionProvider: SessionProvider
    get() = legacyEntryPoint.sessionProvider()

internal val legacyAccountRemoteDataSource: AccountRemoteDataSource
    get() = legacyEntryPoint.accountRemoteDataSource()

internal val legacyThreadRemoteDataSource: ThreadRemoteDataSource
    get() = legacyEntryPoint.threadRemoteDataSource()

/**
 * The official client's code for the operator of [simOperator], its MCC and MNC: 1 China Mobile,
 * 2 China Unicom, 3 China Telecom, 0 otherwise.
 */
internal fun carrierOf(simOperator: String): Int {
    if (!simOperator.startsWith("460") || simOperator.length < 5) return 0
    return when (simOperator.substring(3)) {
        "00", "02", "04", "07", "08" -> 1
        "01", "06", "09" -> 2
        "03", "05", "11" -> 3
        else -> 0
    }
}

package oolong.client.data.identity

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.App
import oolong.client.data.identity.legacyAccountRemoteDataSource
import oolong.client.data.settings.dataStore
import oolong.client.data.update.InstallState
import oolong.core.network.account.SyncInfo
import java.lang.ref.WeakReference

object ClientUtils {
    private const val CLIENT_ID = "client_id"
    private const val SAMPLE_ID = "sample_id"
    private const val BAIDU_ID = "baidu_id"
    private const val ACTIVE_TIMESTAMP = "active_timestamp"

    private val clientIdKey by lazy { stringPreferencesKey(CLIENT_ID) }
    private val sampleIdKey by lazy { stringPreferencesKey(SAMPLE_ID) }
    private val baiduIdKey by lazy { stringPreferencesKey(BAIDU_ID) }
    private val activeTimestampKey by lazy { longPreferencesKey(ACTIVE_TIMESTAMP) }

    private lateinit var contextWeakReference: WeakReference<Context>
    private val context: Context
        get() = contextWeakReference.get() ?: App.INSTANCE

    var clientId: String? = null
    var sampleId: String? = null
    var baiduId: String? = null
    var activeTimestamp: Long = System.currentTimeMillis()

    fun init(context: Context) {
        contextWeakReference = WeakReference(context)
        CoroutineScope(Dispatchers.IO).launch {
            clientId = withContext(Dispatchers.IO) {
                context.dataStore.data.map { it[clientIdKey] }.firstOrNull()
            }
            sampleId = withContext(Dispatchers.IO) {
                context.dataStore.data.map { it[sampleIdKey] }.firstOrNull()
            }
            baiduId = withContext(Dispatchers.IO) {
                context.dataStore.data.map { it[baiduIdKey] }.firstOrNull()
            }
            activeTimestamp = withContext(Dispatchers.IO) {
                context.dataStore.data.map { it[activeTimestampKey] }.firstOrNull()
                    ?: System.currentTimeMillis()
            }
            sync(context)
        }
    }

    suspend fun saveBaiduId(context: Context, id: String) {
        baiduId = id
        context.dataStore.edit {
            it[baiduIdKey] = id
        }
    }

    suspend fun setActiveTimestamp() {
        activeTimestamp = System.currentTimeMillis()
        context.dataStore.edit {
            it[activeTimestampKey] = activeTimestamp
        }
    }

    private suspend fun save(context: Context, clientId: String, sampleId: String) {
        context.dataStore.edit {
            it[clientIdKey] = clientId
            it[sampleIdKey] = sampleId
        }
    }

    private suspend fun sync(context: Context) {
        val pushSwitch = InstallState.pushSwitch(context)
        val info = SyncInfo(
            clientId = clientId,
            pushSwitch = pushSwitch.current,
            lastPushSwitch = pushSwitch.last,
            lastPushSwitchTime = pushSwitch.lastChangeTime,
            runningAbi = InstallState.runningAbi,
            supportAbi = InstallState.supportAbi,
            installTime = App.Config.appFirstInstallTime,
            board = Build.BOARD,
            incremental = Build.VERSION.INCREMENTAL,
        )
        runCatching { legacyAccountRemoteDataSource.sync(info) }
            .onSuccess {
                clientId = it.client.clientId
                sampleId = it.wlConfig.sampleId
                save(context, it.client.clientId, it.wlConfig.sampleId)
            }
            .onFailure { it.printStackTrace() }
    }
}

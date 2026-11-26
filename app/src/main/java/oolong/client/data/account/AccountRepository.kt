package oolong.client.data.account

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import oolong.client.arch.ApplicationScope
import oolong.client.data.identity.SofireUtils
import oolong.client.data.update.InstallState
import oolong.client.data.web.WebSessions
import oolong.client.data.web.bdussCookie
import oolong.client.database.AccountDao
import oolong.client.database.AccountEntity
import oolong.client.model.Account
import oolong.core.network.account.AccountRemoteDataSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What pages may do with the accounts. */
interface AccountActions {
    /** Makes the account with [id] the current one; false when there is none. */
    fun switchTo(id: Int): Boolean

    /**
     * Removes the current account and its web login, and switches to the next one, which is
     * returned; null when none is left.
     */
    fun logOut(): Account?

    /**
     * Logs in with [bduss] and [sToken], which need not be the current account's, and saves the
     * account with what the server says about it.
     */
    suspend fun logIn(bduss: String, sToken: String, cookie: String? = null): Account
}

/** The accounts logged in on this device, and which one the app uses. */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AccountDao,
    private val remote: AccountRemoteDataSource,
    @ApplicationScope private val scope: CoroutineScope,
    // Lazy: making it loads the web view, which only logging out needs
    private val webSessions: dagger.Lazy<WebSessions>,
) : AccountActions {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _current = MutableStateFlow<Account?>(null)

    /** The account requests are made as, or null when logged out. */
    val current: StateFlow<Account?> = _current.asStateFlow()

    init {
        // Blocking: everything after startup expects to know who is logged in
        val accounts = runBlocking { dao.getAll() }.map { it.toAccount() }
        val currentId = prefs.getInt(KEY_CURRENT, -1)
        _accounts.value = accounts
        _current.value = accounts.firstOrNull { it.id == currentId }
    }

    fun accountByUid(uid: String): Account? = accounts.value.firstOrNull { it.uid == uid }

    /** Saves [account] over the account with the same uid, or as a new one, and sets its id. */
    suspend fun save(account: Account) {
        account.id = dao.saveByUid(account.toEntity())
        val accounts = dao.getAll().map { it.toAccount() }
        _accounts.value = accounts
        if (current.value?.uid == account.uid) {
            _current.value = accounts.firstOrNull { it.id == account.id }
        }
    }

    fun saveAsync(account: Account) {
        scope.launch { save(account) }
    }

    override fun switchTo(id: Int): Boolean {
        val account = accounts.value.firstOrNull { it.id == id } ?: return false
        _current.value = account
        prefs.edit(commit = true) { putInt(KEY_CURRENT, id) }
        return true
    }

    override suspend fun logIn(bduss: String, sToken: String, cookie: String?): Account = logIn(bduss, sToken, cookie, zid = null)

    /** Logs in, keeping [zid] if given rather than fetching a new one. */
    private suspend fun logIn(bduss: String, sToken: String, cookie: String?, zid: String?): Account = withContext(Dispatchers.IO) {
        val login = coroutineScope {
            val nickname = async { remote.initNickname(bduss, sToken) }
            val firstLogin = InstallState.firstLogin(context, hasAccounts = accounts.value.isNotEmpty()) == "1"
            remote.login(bduss, sToken, firstLogin).also { nickname.await() }
        }
        InstallState.onLoginSucceeded(context)
        val account = accountByUid(login.user.id)?.copy() ?: Account(uid = login.user.id)
        account.apply {
            this.bduss = bduss
            this.sToken = sToken
            this.cookie = cookie ?: bdussCookie(bduss)
            uid = login.user.id
            name = login.user.name
            portrait = login.user.portrait
            tbs = login.anti.tbs
            if (uuid.isNullOrBlank()) uuid = UUID.randomUUID().toString()
            // Ad blockers often stop the request for a new one; requests go without it then
            this.zid = zid ?: SofireUtils.fetchZid().firstOrNull()
        }
        runCatching { remote.userInfo(account.uid.toLong(), bduss, sToken) }.onSuccess { user ->
            account.nameShow = user.nameShow
            account.portrait = user.portrait
        }
        save(account)
        account
    }

    /**
     * Logs [account] in again, refreshing what is saved about it. Its z_id is kept: fetching one
     * fails where ad blockers stop the request, which would stop sign-in with it.
     */
    suspend fun refresh(account: Account): Account =
        logIn(account.bduss, account.sToken, account.cookie, zid = account.zid?.takeIf { it.isNotBlank() })

    override fun logOut(): Account? {
        val account = current.value ?: return null
        val remaining = accounts.value.filterNot { it.id == account.id }
        _accounts.value = remaining
        scope.launch { dao.delete(account.id) }
        webSessions.get().remove(account)
        val next = remaining.firstOrNull()
        if (next != null) {
            switchTo(next.id)
        } else {
            _current.value = null
            prefs.edit(commit = true) { clear() }
        }
        return next
    }

    private fun Account.toEntity() = AccountEntity(
        id = id,
        uid = uid,
        name = name,
        bduss = bduss,
        tbs = tbs,
        portrait = portrait,
        stoken = sToken,
        cookie = cookie,
        nameShow = nameShow,
        intro = intro,
        sex = sex,
        fansNum = fansNum,
        postNum = postNum,
        threadNum = threadNum,
        concernNum = concernNum,
        tbAge = tbAge,
        age = age,
        birthdayShowStatus = birthdayShowStatus,
        birthdayTime = birthdayTime,
        constellation = constellation,
        tiebaUid = tiebaUid,
        loadSuccess = loadSuccess,
        uuid = uuid,
        zid = zid,
    )

    private fun AccountEntity.toAccount() = Account(
        uid = uid,
        name = name,
        bduss = bduss,
        tbs = tbs,
        portrait = portrait,
        sToken = stoken,
        cookie = cookie,
        nameShow = nameShow,
        intro = intro,
        sex = sex,
        fansNum = fansNum,
        postNum = postNum,
        threadNum = threadNum,
        concernNum = concernNum,
        tbAge = tbAge,
        age = age,
        birthdayShowStatus = birthdayShowStatus,
        birthdayTime = birthdayTime,
        constellation = constellation,
        tiebaUid = tiebaUid,
        loadSuccess = loadSuccess,
        uuid = uuid,
        zid = zid,
        id = id,
    )

    private companion object {
        const val PREFS = "accountData"
        const val KEY_CURRENT = "now"
    }
}

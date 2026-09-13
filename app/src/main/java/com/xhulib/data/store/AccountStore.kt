package com.xhulib.data.store

import com.xhulib.data.Opac
import org.json.JSONArray
import org.json.JSONObject

/** 本地保存的一个账号（不含密码明文）。 */
data class SavedAccount(
    val number: String,
    val displayName: String,
    val loginType: Opac.LoginType,
    val lastUsedAt: Long,
)

/**
 * 多账号管理。
 *
 * - 账号列表与当前选中账号记在 [SecurePrefs]（加密）
 * - 每个账号的密码单独加密存放，键为 `pwd:<学号>`
 * - 退出登录时可选择仅清会话、保留账号，或连同账号一起删除
 */
class AccountStore(private val secure: SecurePrefs) {

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT = "current_account"

        private fun passwordKey(number: String) = "pwd:$number"

        /** 默认显示名：保留学号后 4 位，其余打码（与服务端打码风格一致）。 */
        fun maskNumber(number: String): String =
            if (number.length <= 4) number else "****" + number.takeLast(4)
    }

    fun accounts(): List<SavedAccount> {
        val raw = secure.getString(KEY_ACCOUNTS) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SavedAccount(
                    number = obj.getString("number"),
                    displayName = obj.optString("displayName"),
                    loginType = runCatching {
                        Opac.LoginType.valueOf(obj.optString("loginType"))
                    }.getOrDefault(Opac.LoginType.CERT_NO),
                    lastUsedAt = obj.optLong("lastUsedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAccounts(list: List<SavedAccount>) {
        val array = JSONArray()
        list.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("number", account.number)
                    put("displayName", account.displayName)
                    put("loginType", account.loginType.name)
                    put("lastUsedAt", account.lastUsedAt)
                },
            )
        }
        secure.putString(KEY_ACCOUNTS, array.toString())
    }

    /** 保存或更新账号；[rememberPassword] 为 false 时只记账号不记密码。 */
    fun save(
        number: String,
        displayName: String,
        loginType: Opac.LoginType,
        password: String?,
        lastUsedAt: Long = System.currentTimeMillis(),
    ) {
        val updated = accounts().filterNot { it.number == number } +
            SavedAccount(
                number = number,
                displayName = displayName.ifBlank { maskNumber(number) },
                loginType = loginType,
                lastUsedAt = lastUsedAt,
            )
        writeAccounts(updated.sortedByDescending { it.lastUsedAt })
        if (password != null) secure.putString(passwordKey(number), password)
        secure.putString(KEY_CURRENT, number)
    }

    fun passwordOf(number: String): String? = secure.getString(passwordKey(number))

    fun currentAccount(): SavedAccount? {
        val number = secure.getString(KEY_CURRENT) ?: return null
        return accounts().firstOrNull { it.number == number }
    }

    fun setCurrent(number: String) {
        secure.putString(KEY_CURRENT, number)
    }

    /** 删除账号及其密码。 */
    fun remove(number: String) {
        writeAccounts(accounts().filterNot { it.number == number })
        secure.remove(passwordKey(number))
        if (secure.getString(KEY_CURRENT) == number) secure.remove(KEY_CURRENT)
    }
}

package com.xhulib.data.net

import kotlin.random.Random

/**
 * 汇文 OPAC 登录页 `encode()` 函数的等价实现。
 *
 * 页面从 `reader/ajax_ep.php` 异步取到一张 **62 字符的混淆表** `sca`（每次会话都不同），
 * 然后把密码逐字符膨胀成 4 个字符：
 *
 * ```
 * 明文字符 c：
 *   idx = sca.indexOf(c)
 *   code = (idx == -1) ? hex(c 的码点) : hex(sca[(idx + 3) % 62] 的码点)
 *   输出 = sca[随机] + code + sca[随机]
 * ```
 *
 * 关键点：`login.php` 的 HTML 里**没有** `sca` 字段，它由
 * `$("#epa").load("ajax_ep.php")` 注入。漏掉这一步，服务器只会回
 * 「用户名或密码错误」，不会提示是编码问题。
 */
object PasswordCodec {

    private const val TABLE_SIZE = 62
    private const val SHIFT = 3

    fun encode(password: String, sca: String, random: Random = Random.Default): String {
        require(sca.length == TABLE_SIZE) { "sca 长度应为 $TABLE_SIZE，实际为 ${sca.length}" }

        val out = StringBuilder(password.length * 4)
        for (ch in password) {
            val idx = sca.indexOf(ch)
            val code = if (idx == -1) {
                ch.code.toString(16)
            } else {
                sca[(idx + SHIFT) % TABLE_SIZE].code.toString(16)
            }
            out.append(sca[random.nextInt(TABLE_SIZE)])
            out.append(code)
            out.append(sca[random.nextInt(TABLE_SIZE)])
        }
        return out.toString()
    }
}

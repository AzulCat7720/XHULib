package com.xhulib.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 密码混淆算法是登录成败的关键，且必须与登录页的 JavaScript 逐字符一致。
 *
 * 算法（来自 `reader/login.php` 的 `encode()`）：
 * ```
 * 明文字符 c -> sca[随机] + hex(code) + sca[随机]，共 4 个字符
 * 其中 code = (c 在 sca 中) ? sca[(idx + 3) % 62] : c 本身
 * ```
 */
class PasswordCodecTest {

    /** 62 字符的混淆表，顺序随意，但长度必须正好是 62。 */
    private val sca = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private fun decodeGroup(group: String): String {
        // group = [随机, 十六进制高位, 十六进制低位, 随机]
        assertEquals("每 4 个字符一组", 4, group.length)
        return group.substring(1, 3)
    }

    @Test
    fun `每个明文字符膨胀成 4 个字符`() {
        val encoded = PasswordCodec.encode("abc", sca, Random(42))
        assertEquals(12, encoded.length)
    }

    @Test
    fun `表内字符按后移三位编码`() {
        // 'a' 的下标是 0，后移 3 位得到 sca[3] = 'd'，'d' 的码点 100 = 0x64
        val encoded = PasswordCodec.encode("a", sca, Random(1))
        assertEquals("64", decodeGroup(encoded))

        // 'b' 的下标是 1 -> sca[4] = 'e' -> 101 = 0x65
        assertEquals("65", decodeGroup(PasswordCodec.encode("b", sca, Random(1))))
    }

    @Test
    fun `表尾字符按 62 取模回绕`() {
        // sca[61] = '9'，下标 61 -> (61 + 3) % 62 = 2 -> sca[2] = 'c' -> 99 = 0x63
        assertEquals("63", decodeGroup(PasswordCodec.encode("9", sca, Random(7))))
    }

    @Test
    fun `不在表内的字符直接使用码点`() {
        // '中' 的码点 0x4E2D，JS 的 toString(16) 同样输出 4 位十六进制
        val encoded = PasswordCodec.encode("中", sca, Random(3))
        assertEquals("4e2d", encoded.substring(1, encoded.length - 1))
    }

    @Test
    fun `填充字符一定取自混淆表`() {
        // 用虚构样本，测试里绝不出现任何真实账号或密码
        val encoded = PasswordCodec.encode("Sample-Pw-2024", sca, Random(99))
        assertEquals(56, encoded.length)
        encoded.forEach { ch ->
            assertTrue("填充字符 $ch 应属于混淆表", sca.contains(ch))
        }
    }

    @Test
    fun `混淆表长度必须为 62`() {
        val error = runCatching { PasswordCodec.encode("x", "too-short") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

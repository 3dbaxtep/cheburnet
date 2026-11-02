package ru.cheburnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictLogicTest {

    private fun target(id: String, country: String, group: TargetGroup) =
        Target(id, id, "$id.com", country, group)

    @Test
    fun `score weights world 10000 ru 100 whitelist 1`() {
        assertEquals(0, VerdictLogic.score(0, 0, 0))
        assertEquals(10_000, VerdictLogic.score(1, 0, 0))
        assertEquals(100, VerdictLogic.score(0, 1, 0))
        assertEquals(1, VerdictLogic.score(0, 0, 1))
        assertEquals(10_801, VerdictLogic.score(1, 8, 1))
    }

    @Test
    fun `more than sixty percent of world means open`() {
        // 13 из 20 = 65% > 60%
        assertEquals(Verdict.OPEN, VerdictLogic.decide(13, 20, 0, 10, 0))
        // 12 из 20 = 60% — ровно на границе, ещё частичная
        assertEquals(Verdict.PARTIAL, VerdictLogic.decide(12, 20, 0, 10, 0))
    }

    @Test
    fun `some world means partial`() {
        assertEquals(Verdict.PARTIAL, VerdictLogic.decide(1, 20, 0, 10, 10))
        assertEquals(Verdict.PARTIAL, VerdictLogic.decide(5, 20, 0, 10, 10))
    }

    @Test
    fun `more than fifty percent of russia means cheburnet`() {
        // 6 из 10 = 60% > 50%
        assertEquals(Verdict.CHEBURNET, VerdictLogic.decide(0, 20, 6, 10, 0))
        assertEquals(Verdict.CHEBURNET, VerdictLogic.decide(0, 20, 10, 10, 10))
    }

    @Test
    fun `fifty percent or less of russia is still bs`() {
        // 5 из 10 = ровно 50% — ещё БС
        assertEquals(Verdict.WHITELIST_ONLY, VerdictLogic.decide(0, 20, 5, 10, 9))
        // 2 из 10 = 20% — БС (ложное срабатывание из лога)
        assertEquals(Verdict.WHITELIST_ONLY, VerdictLogic.decide(0, 20, 2, 10, 10))
        assertEquals(Verdict.WHITELIST_ONLY, VerdictLogic.decide(0, 20, 1, 10, 9))
    }

    @Test
    fun `only whitelist alive means whitelist only`() {
        assertEquals(Verdict.WHITELIST_ONLY, VerdictLogic.decide(0, 20, 0, 10, 9))
    }

    @Test
    fun `nothing works means offline`() {
        assertEquals(Verdict.OFFLINE, VerdictLogic.decide(0, 20, 0, 10, 0))
    }

    @Test
    fun `partial lights only countries that answered`() {
        val usa = TargetResult(target("google", "USA", TargetGroup.INTERNATIONAL), 40, true)
        val jpn = TargetResult(target("yahoo", "JPN", TargetGroup.INTERNATIONAL), null, false)
        val rus = TargetResult(target("sber", "RUS", TargetGroup.RU_BLOCKED), 20, true)

        val lit = VerdictLogic.litCountries(listOf(usa, jpn, rus), Verdict.PARTIAL)
        assertEquals(setOf("USA", "RUS"), lit)
        assertTrue("JPN" !in lit)
    }

    @Test
    fun `cheburnet fills only russia`() {
        val rus = TargetResult(target("sber", "RUS", TargetGroup.RU_BLOCKED), 20, true)
        assertEquals(setOf("RUS"), VerdictLogic.litCountries(listOf(rus), Verdict.CHEBURNET))
    }

    @Test
    fun `whitelist only lights no countries`() {
        val rus = TargetResult(target("yandex", "RUS", TargetGroup.WHITELIST), 20, true)
        assertTrue(VerdictLogic.litCountries(listOf(rus), Verdict.WHITELIST_ONLY).isEmpty())
    }

    @Test
    fun `open internet is handled as whole world`() {
        val usa = TargetResult(target("google", "USA", TargetGroup.INTERNATIONAL), 40, true)
        assertTrue(VerdictLogic.litCountries(listOf(usa), Verdict.OPEN).isEmpty())
    }
}

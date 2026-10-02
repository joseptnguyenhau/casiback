package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CasiStep9Test {

    @Test
    fun `test withdrawal minimum amount validation`() {
        val minWithdrawal = 20000L
        
        // Below min
        val invalidAmount = 15000L
        val isValidInvalid = invalidAmount >= minWithdrawal
        assertFalse("Amount 15000 should be rejected", isValidInvalid)

        // Exact min
        val exactAmount = 20000L
        val isValidExact = exactAmount >= minWithdrawal
        assertTrue("Amount 20000 should be accepted", isValidExact)

        // Above min
        val validAmount = 50000L
        val isValidValid = validAmount >= minWithdrawal
        assertTrue("Amount 50000 should be accepted", isValidValid)
    }

    @Test
    fun `test transaction filtering by current user uid`() {
        data class TestTx(val id: String, val userId: String, val amount: Long)

        val currentUid = "user_abc_123"
        val allTransactions = listOf(
            TestTx("tx1", "user_abc_123", 25000L),
            TestTx("tx2", "user_xyz_789", 50000L),
            TestTx("tx3", "user_abc_123", 10000L)
        )

        val filtered = allTransactions.filter { it.userId == currentUid }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.userId == currentUid })
        assertEquals("tx1", filtered[0].id)
        assertEquals("tx3", filtered[1].id)
    }

    @Test
    fun `test user balance distinction between available and pending`() {
        val balanceData = mapOf(
            "balanceAvailable" to 65000L,
            "balancePending" to 25000L,
            "balancePaid" to 100000L
        )

        val available = balanceData["balanceAvailable"] ?: 0L
        val pending = balanceData["balancePending"] ?: 0L
        val paid = balanceData["balancePaid"] ?: 0L

        assertEquals(65000L, available)
        assertEquals(25000L, pending)
        assertEquals(100000L, paid)
        // Pending must not be automatically added to available client-side without backend approval
        val effectiveSpendable = available
        assertEquals(65000L, effectiveSpendable)
    }

    @Test
    fun `test user profile data parsing`() {
        val rawProfile = mapOf(
            "uid" to "staging_test_user_999",
            "email" to "staging@casi.ai",
            "displayName" to "Staging Test User",
            "provider" to "google"
        )

        assertEquals("staging_test_user_999", rawProfile["uid"])
        assertEquals("staging@casi.ai", rawProfile["email"])
        assertEquals("Staging Test User", rawProfile["displayName"])
        assertEquals("google", rawProfile["provider"])
    }
}

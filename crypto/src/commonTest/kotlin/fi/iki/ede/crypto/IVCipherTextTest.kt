package fi.iki.ede.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class IVCipherTextTest {

    @Test
    fun `primary constructor and properties`() {
        val iv = byteArrayOf(1, 2, 3)
        val cipherText = byteArrayOf(4, 5, 6)
        val ivCipherText = IVCipherText(iv, cipherText)

        assertContentEquals(iv, ivCipherText.iv)
        assertContentEquals(cipherText, ivCipherText.cipherText)
    }

    @Test
    fun `isEmpty and isNotEmpty`() {
        val empty = IVCipherText(byteArrayOf(), byteArrayOf())
        val notEmpty = IVCipherText(byteArrayOf(1), byteArrayOf(2))

        assertTrue(empty.isEmpty())
        assertFalse(empty.isNotEmpty())
        assertFalse(notEmpty.isEmpty())
        assertTrue(notEmpty.isNotEmpty())
    }

    @Test
    fun `equals and hashCode`() {
        val iv1 = byteArrayOf(1, 2, 3)
        val cipherText1 = byteArrayOf(4, 5, 6)
        val ivCipherText1 = IVCipherText(iv1, cipherText1)

        val iv2 = byteArrayOf(1, 2, 3)
        val cipherText2 = byteArrayOf(4, 5, 6)
        val ivCipherText2 = IVCipherText(iv2, cipherText2)

        val iv3 = byteArrayOf(7, 8, 9)
        val cipherText3 = byteArrayOf(10, 11, 12)
        val ivCipherText3 = IVCipherText(iv3, cipherText3)

        assertEquals(ivCipherText1, ivCipherText2)
        assertEquals(ivCipherText1.hashCode(), ivCipherText2.hashCode())

        assertNotEquals(ivCipherText1, ivCipherText3)
        assertNotEquals(ivCipherText1.hashCode(), ivCipherText3.hashCode())
    }
}
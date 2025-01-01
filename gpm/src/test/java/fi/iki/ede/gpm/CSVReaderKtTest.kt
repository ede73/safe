package fi.iki.ede.gpm

import android.util.Log
import fi.iki.ede.gpm.csv.processInputLine
import fi.iki.ede.gpm.csv.readCsv
import fi.iki.ede.gpm.model.IncomingGPM.Companion.makeFromCSVImport
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val TAG = "CSVReaderKtTest"

class CSVReaderKtTest {

    @Before
    fun before() {
        mockkStatic(android.util.Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun after() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun a() {
        val x = "            d,b,\"c,a\","
        val q = processInputLine(x, 5)
        q.forEach {
            Log.d(TAG, it)
        }
    }

    @Test
    fun readCsvTest() {
        val input = """
            name,url,username,password,note
            a,b,c,d,
            
            a, b,c,d,
             a,b, c,d,
            a,b,'c,',d,
            a, b,'c,',d
            a,b,,/,c,d,
            a,b,c,'d'''
        """.trimIndent().replace('\'', '"')

        // this is REAL! 322 -> NAME,https://WEIRD_URL,,/,USER,PASSWORD,
        // https://www.apress.com/customer/account/login/referer/aHR0cHM6Ly93d3cuYXByZXNzLmNvbS9jdXN0b21lci9hY2NvdW50L2luZGV4Lw,,/
        // Also a,b,c,"d""d", (password is d"d
        val results = readCsv(input.byteInputStream())
        // yes 2, identical lines compressed
        results.forEach { result ->
            Log.d(TAG, result.toString())
        }
        Log.d(TAG, "-----")
        assertEquals(4, results.size)
        assertEquals("b,,/", results.elementAt(2).url)
        assertEquals("d\"", results.elementAt(3).password)
        assertEquals("c,", results.elementAt(1).username)

        results.forEach { result ->
            Log.d(TAG, result.toString())
            assertEquals("a", result.name)
            assertEquals("b", result.url.replace(",,/", "")) // naive, should be positional
            assertEquals("c", result.username.replace(",", "")) // naive, should be positional
            assertEquals("d", result.password.replace("\"", "")) // naive, should be positional
            assertEquals("", result.note)
        }
    }

    @Test
    fun readCsvTest2() {
        val input = """
            name,url,username,password,note
            a,"",c,d,
            a,b,""c"",d,
            d,b,"c,a",
        """.trimIndent()
        val results = readCsv(input.byteInputStream())
        assert(results.elementAt(0) == makeFromCSVImport("a", "", "c", "d", ""))
        assert(results.elementAt(1) == makeFromCSVImport("a", "b", "\"c\"", "d", ""))
        // TODO: STUPID but documenting
        assert(results.elementAt(2) == makeFromCSVImport("d", "b", "c,a", "", ""))
        assert(results.size == 3) { "Expected 3, got ${results.size}" }
    }

    //    }
//        splitInputLine("a,b,c,d,e,f")
//    fun tooManyInputs() {
    @Test
    fun splitInputLineTest() {
        // stupid, but documenting
        assertArrayEquals(
            arrayOf("a", "b", "c", "d", "e"),
            processInputLine("a,b,c,d,e", 5).toTypedArray()
        )
        assertArrayEquals(
            arrayOf("a", "b", "c", "d", "e"),
            processInputLine("a,b,c,d,e", 5).toTypedArray()
        )
        assertArrayEquals(
            arrayOf("a", "b", "c", "d", ""),
            processInputLine(" a, b, c, d, ", 5).toTypedArray()
        )
        assertArrayEquals(
            arrayOf("a", "b", "c", "\"d\"", ""),
            processInputLine(" a, b, c, \"d\" ", 5).toTypedArray()
        )
    }
}

package fi.iki.ede.gpm

import fi.iki.ede.gpm.csv.readCsv
import fi.iki.ede.gpm.csv.splitInputLine
import fi.iki.ede.gpm.model.IncomingGPM.Companion.makeFromCSVImport
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CSVReaderKtTest {

//    @Test(expected = IndexOutOfBoundsException::class)

    @Test
    fun readCsvTest() {
        val input = """
            name,url,username,password,note
            a,b,c,d,
            
            a,b,c,d,
            d,b,c,a,
        """.trimIndent()
        val results = readCsv(input.byteInputStream())
        // yes 2, identical lines compressed
        assert(results.size == 2) { "Expected 2, got ${results.size}" }
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
        print(results)
        assert(results.elementAt(0) == makeFromCSVImport("a", "", "c", "d", ""))
        assert(results.elementAt(1) == makeFromCSVImport("a", "b", "c", "d", ""))
        // TODO: STUPID but documenting
        assert(results.elementAt(2) == makeFromCSVImport("d", "b", "\"c", "a\"", ""))
        assert(results.size == 3) { "Expected 3, got ${results.size}" }
    }

    //    }
//        splitInputLine("a,b,c,d,e,f")
//    fun tooManyInputs() {
    @Test
    fun splitInputLineTest() {
        // stupid, but documenting
        assertArrayEquals(
            arrayOf("a", "b", "c", "d", "e,f,g,h"),
            splitInputLine("a,b,c,d,e,f,g,h").toTypedArray()
        )
        assertArrayEquals(
            arrayOf("a", "b", "c", "d", "e"),
            splitInputLine("a,b,c,d,e").toTypedArray()
        )
        assertArrayEquals(
            arrayOf("a", "b", "c", "d"),
            splitInputLine(" a, b, c, d ").toTypedArray()
        )
        assertArrayEquals(arrayOf("a", "b", "c", ""), splitInputLine(" a, b, c,  ").toTypedArray())
    }
}

//package fi.iki.ede.safe
//
//import fi.iki.ede.crypto.IVCipherText
//import fi.iki.ede.crypto.Salt
//import fi.iki.ede.safe.db.DBHelper
//import fi.iki.ede.safe.db.DBHelperFactory
//import io.mockk.every
//import io.mockk.mockk
//import io.mockk.mockkObject
//
//object DBMocks {
//    fun mockDb(salt: Salt, ivCipher: IVCipherText) {
//        val db = mockk<DBHelper>()
//        every { db.fetchSaltAndEncryptedMasterKey() } returns Pair(salt, ivCipher)
//
//        mockkObject(DBHelperFactory)
//        every { DBHelperFactory.getDBHelper(any()) } returns db
//    }
//}
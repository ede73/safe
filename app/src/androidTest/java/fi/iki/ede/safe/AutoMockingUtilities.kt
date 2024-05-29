package fi.iki.ede.safe

import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Assert.assertArrayEquals

interface AutoMockingUtilities {
    companion object {
        fun getBiometricsEnabled(biometrics: () -> Boolean) {
            mockkObject(Preferences)
            every { BiometricsActivity.isBiometricEnabled() } returns biometrics()
        }

        fun fetchDBKeys(
            masterKey: () -> IVCipherText,//TODO: Should ne SaltedEncryptedPassword these two
            salt: () -> Salt,
            fetchPasswordsOfCategory: () -> List<DecryptableSiteEntry>,
            fetchCategories: () -> List<DecryptableCategoryEntry>,
            isUninitializedDatabase: () -> Boolean = { false }
        ) {
            // Any mock I tried results in android verifier exception - indicating
            // class modification failed, there's also mock error
            // Full error:

            /*
2023-06-11 06:50:21.779 14587-14612 fi.iki.ede.safe         fi.iki.ede.safe                      W  Current dex file has more than one class in it. Calling RetransformClasses on this class might fail if no transformations are applied to it!
2023-06-11 06:50:21.779 14587-14612 fi.iki.ede.safe         fi.iki.ede.safe                      W  Verification error in void fi.iki.ede.safe.db.DBHelper.<clinit>()
                                                                                                    void fi.iki.ede.safe.db.DBHelper.<clinit>(): [0xFFFFFFFF] register index out of range (7 >= 7)
2023-06-11 06:50:21.781 14587-14612 fi.iki.ede.safe         fi.iki.ede.safe                      W  FAILURE TO RETRANSFORM Unable to perform redefinition of 'Lfi/iki/ede/safe/db/DBHelper;': Failed to verify class. Error was: Verifier rejected class fi.iki.ede.safe.db.DBHelper: void fi.iki.ede.safe.db.DBHelper.<clinit>(): [0xFFFFFFFF] register index out of range (7 >= 7)
2023-06-11 06:50:21.784 14587-14612 idInlineInstrumentation fi.iki.ede.safe                      W  Failed to transform classes [class fi.iki.ede.safe.db.DBHelper]
                                                                                                    java.lang.RuntimeException: Could not retransform classes: 62
                                                                                                    	at io.mockk.proxy.android.JvmtiAgent.nativeRetransformClasses(Native Method)
                                                                                                    	at io.mockk.proxy.android.JvmtiAgent.requestTransformClasses(JvmtiAgent.kt:66)
                                                                                                    	at io.mockk.proxy.android.transformation.AndroidInlineInstrumentation.retransform(AndroidInlineInstrumentation.kt:19
             */
            //mockkConstructor(DBHelper::class)
            //every { anyConstructed<DBHelper>().fetchMasterKey() } returns xx()

            val db = mockk<DBHelper>()
            every { db.fetchSaltAndEncryptedMasterKey() } returns Pair(
                salt(),
                masterKey()
            )

            mockkObject(DBHelperFactory)
            every { DBHelperFactory.getDBHelper(any()) } returns db

            every { db.fetchAllRows(any()) } returns fetchPasswordsOfCategory()
            every { db.fetchAllCategoryRows() } returns fetchCategories()
            every { db.getCategoryCount(any()) } returns 1
            //every { db.isUninitializedDatabase() } returns isUninitializedDatabase()
            val context =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            val dbinst = DBHelperFactory.getDBHelper(context)

            assertArrayEquals(
                masterKey().iv,
                dbinst.fetchSaltAndEncryptedMasterKey().second.iv
            )
            assertArrayEquals(
                masterKey().cipherText,
                dbinst.fetchSaltAndEncryptedMasterKey().second.cipherText
            )
        }
    }
}
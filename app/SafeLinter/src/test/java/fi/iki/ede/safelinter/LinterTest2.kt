package fi.iki.ede.safelinter

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Ignore

@Ignore
class LinterTest2 : LintDetectorTest() {

    fun testImplicitToString() {
        lint().files(
            kotlin(
                """
            package fi.iki.ede.crypto.support
            interface DisallowedFunctions
            data class Password(val password: String) : DisallowedFunctions
            class EmptyOne {
                val p: Password? = null
                val x = "${'$'}{p?.toString()}"
            }
            """
            ).indented()
        )
            .issues(SafeImplicitDisallowedFunctionsDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect(
                """
            src/test/pkg/Test.kt:6: Error: Implicit toString() call on Password type [ImplicitToString]
                    val log = "Password: password"
                                       ~~~~~~~~~
            1 errors, 0 warnings
        """.trimIndent()
            )
    }

    override fun getDetector(): Detector {
        return SafeImplicitDisallowedFunctionsDetector()
    }

    override fun getIssues(): List<Issue> {
        return listOf(SafeImplicitDisallowedFunctionsDetector.ISSUE)
    }
}

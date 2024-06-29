package fi.iki.ede.safelinter

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

// ./gradlew lintDebug
// https://googlesamples.github.io/android-custom-lint-rules/api-guide.html#writingalintcheck:basics
class SafeDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("println")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "kotlin.io.ConsoleKt")) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Avoid using `println` in Android code, do Log.*(TAG, \"\")"
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "AvoidPrintln",
            briefDescription = "println usage",
            explanation = """
                `System.out.println` should not be used in Android code as it can slow down the application.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(SafeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}

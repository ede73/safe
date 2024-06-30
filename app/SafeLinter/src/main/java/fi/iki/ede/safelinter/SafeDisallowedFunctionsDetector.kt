package fi.iki.ede.safelinter

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement

private const val MARKER_IF = "fi.iki.ede.crypto.support.DisallowedFunctions"

class SafeDisallowedFunctionsDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    private fun hasDisallowedFunctionsInterface(method: PsiMethod) =
        method.containingClass?.interfaces?.any { it.qualifiedName == MARKER_IF } ?: false

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method != null && method.name == "toString" &&
                    hasDisallowedFunctionsInterface(method)
                ) {
                    context.report(
                        ISSUE, node, context.getLocation(node),
                        "Usage of `.toString()` on classes implementing `DisallowedFunctions` is not allowed."
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "DisallowedToString",
            briefDescription = "Disallowed toString usage",
            explanation = """
                Be very careful where you expose textual representation of this class!
                It contains very sensitive information.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                SafeDisallowedFunctionsDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

package fi.iki.ede.safelinter

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression
import org.jetbrains.uast.kotlin.KotlinUSimpleReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val MARKER_IF = "fi.iki.ede.crypto.support.DisallowedFunctions"

fun hasDisallowedFunctions(method: PsiMethod): Boolean {
    // Implement logic to get the corresponding property from the getter method
    println("getPropertyFromGetter -> method.name = ${method.name}")
    println("${method.containingClass}")
    println("${method.containingClass?.qualifiedName}")
    val propertyName = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
    val q = method.containingClass?.fields?.firstOrNull { field ->
        field.name == propertyName
    }
    val interfaces = q?.type?.superTypes?.map { it.canonicalText }?.toSet() ?: emptySet()
    println("interfaces=$interfaces")

    return MARKER_IF in interfaces
}

class SafeImplicitDisallowedFunctionsDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UElement::class.java)
    }


    // Password? "${p}" and Password "$p"
    class TemplateVisitor(private val context: Context) : AbstractUastVisitor() {
        override fun visitElement(node: UElement): Boolean {
            val originNode = node
            // needed in TEST, not in prod!?
            if (node !is KotlinStringTemplateUPolyadicExpression) return false // return children though, we dont know what is to be found

            node.operands.forEach { operand ->
                println("$operand")
                if (operand is KotlinUSimpleReferenceExpression) {
                    // "${p}"
                    val resolved: PsiElement? = operand.resolve()
                    if (resolved is PsiMethod && hasDisallowedFunctions(resolved)) {
                        context.report(
                            ISSUE, context.getLocation(originNode),
                            "Be careful when implicitly printing sensitive information marked with `DisallowedFunctions`."
                        )
                    }
                }
                if (operand is KotlinUSimpleReferenceExpression) {
                    // "${p}"
                    val resolved: PsiElement? = operand.resolve()
                    if (resolved is PsiMethod && resolved != null && hasDisallowedFunctions(resolved)) {
                        context.report(
                            ISSUE, context.getLocation(originNode),
                            "Be careful when implicitly printing sensitive information marked with `DisallowedFunctions`."
                        )
                    }
                }
            }
            return false
        }
    }

    //    // Password? "${p}"
//    class TemplateVisitor1(private val context: Context) : AbstractUastVisitor() {
//        override fun visitElement(node: UElement): Boolean {
//            val originNode = node
//            // needed in TEST, not in prod!?
//            if (node !is KotlinStringTemplateUPolyadicExpression) return false // return children though, we dont know what is to be found
//
//            node.operands.forEach { operand ->
//                if (operand is KotlinUSimpleReferenceExpression) {
//                    // "${p}"
//                    val resolved: PsiElement? = operand.resolve()
//                    if (resolved is PsiMethod && hasDisallowedFunctions(resolved)) {
//                        context.report(
//                            ISSUE, context.getLocation(originNode),
//                            "Be careful when implicitly printing sensitive information marked with `DisallowedFunctions`."
//                        )
//                    }
//                }
//            }
//            return false
//        }
//    }
//
//    // Password! "${p}"
//    class TemplateVisitor3(private val context: Context) : AbstractUastVisitor() {
//        override fun visitElement(node: UElement): Boolean {
//            val originNode = node
//            // needed in TEST, not in prod!?
//            if (!(node is KotlinStringTemplateUPolyadicExpression)) {
//                return super.visitElement(node)
//            }
//            node.operands.forEach { operand ->
//                if (operand is KotlinUSimpleReferenceExpression) {
//                    // "${p}"
//                    val resolved: PsiElement? = operand.resolve()
//                    if (resolved is PsiMethod && resolved != null && hasDisallowedFunctions(resolved)) {
//                        context.report(
//                            ISSUE, context.getLocation(originNode),
//                            "Be careful when implicitly printing sensitive information marked with `DisallowedFunctions`."
//                        )
//                    }
//                }
//            }
//            return super.visitElement(node)
//        }
//    }
//
//    class FilteringVisitor(
//        private val context: Context,
//        val visitor: AbstractUastVisitor,
//        val packageName: String,
//        val fileName: String
//    ) : AbstractUastVisitor() {
//        override fun visitElement(node: UElement): Boolean {
//            val pkg = node.getContainingUFile()?.packageName
//            val name = node.getContainingUFile()?.psi?.name
//            if (pkg == packageName && name == fileName) {
//                if (node is KotlinStringTemplateUPolyadicExpression) {
//                    node.accept(visitor)
//                }
//            }
//            return super.visitElement(node)
//        }
//    }
//
    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitElement(node: UElement) {
                if (node is KotlinStringTemplateUPolyadicExpression) {
                    node.accept(TemplateVisitor(context))
//                    node.accept(
//                        FilteringVisitor(
//                            context,
//                            TemplateVisitor3(context),
//                            "fi.iki.ede.safe.backupandrestore",
//                            "EmptyThree.kt"
//                        )
//                    )
                }
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "DisallowedImplicitToString",
            briefDescription = "Disallowed implicit toString usage",
            explanation = """
                Be very careful where you expose textual representation of this class!
                It contains very sensitive information.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                SafeImplicitDisallowedFunctionsDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

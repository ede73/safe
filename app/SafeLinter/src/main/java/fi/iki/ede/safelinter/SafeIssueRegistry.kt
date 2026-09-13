package fi.iki.ede.safelinter

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class SafeIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            SafeDetector.ISSUE,
            SafeDisallowedFunctionsDetector.ISSUE,
            SafeImplicitDisallowedFunctionsDetector.ISSUE
        )

    override val api: Int get() = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Safe",
        identifier = "fi.iki.ede.safelinter"
    )

    // works with Studio 4.1 or later; see com.android.tools.lint.detector.api.Api / ApiKt
    override val minApi: Int get() = 8
}

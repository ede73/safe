package fi.iki.ede.safelinter

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class SafeIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> get() = listOf(SafeDetector.ISSUE)

    override val api: Int get() = CURRENT_API

    // works with Studio 4.1 or later; see com.android.tools.lint.detector.api.Api / ApiKt
    override val minApi: Int get() = 8
}

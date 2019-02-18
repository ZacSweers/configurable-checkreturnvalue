package io.sweers.configurablecheckreturnvalue.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.google.auto.service.AutoService

internal const val PRIORITY = 10 // Does not matter anyways within Lint.

@AutoService(IssueRegistry::class)
class CheckOptionalReturnValueIssueRegistry : IssueRegistry() {
  override val api = CURRENT_API
  override val issues
    get() = listOf(ConfigurableCheckResultDetector.CONFIGURABLE_CHECK_RETURN_VALUE)
}
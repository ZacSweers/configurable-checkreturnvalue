package io.sweers.rxjava2optionalcheckreturnvaluechecker.lint

/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.containsAnnotation
import com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValue
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getParentOfType
import java.util.EnumSet

/**
 * Adapted from https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/CheckResultDetector.kt
 *
 * Modified to only check `OptionalCheckReturnValue`.
 */
class OptionalCheckReturnValueDetector : Detector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> = listOf(
      "io.reactivex.annotations.OptionalCheckReturnValue"
  )

  override fun visitAnnotationUsage(
      context: JavaContext,
      usage: UElement,
      type: AnnotationUsageType,
      annotation: UAnnotation,
      qualifiedName: String,
      method: PsiMethod?,
      annotations: List<UAnnotation>,
      allMemberAnnotations: List<UAnnotation>,
      allClassAnnotations: List<UAnnotation>,
      allPackageAnnotations: List<UAnnotation>
  ) {
    method ?: return
    if (qualifiedName == ERRORPRONE_CAN_IGNORE_RETURN_VALUE) {
      return
    }
    checkResult(
        context = context,
        element = usage,
        method = method,
        annotation = annotation,
        allMemberAnnotations = allMemberAnnotations,
        allClassAnnotations = allClassAnnotations,
        allPackageAnnotations = allPackageAnnotations
    )
  }

  private fun checkResult(
      context: JavaContext,
      element: UElement,
      method: PsiMethod,
      annotation: UAnnotation,
      allMemberAnnotations: List<UAnnotation>,
      allClassAnnotations: List<UAnnotation>,
      allPackageAnnotations: List<UAnnotation>
  ) {
    if (isExpressionValueUnused(element)) {
      // If this OptionalCheckReturnValue annotations is from a class, check to see
      // if it's been reversed with @CanIgnoreReturnValue
      if (containsAnnotation(allMemberAnnotations, ERRORPRONE_CAN_IGNORE_RETURN_VALUE) ||
          containsAnnotation(
              allClassAnnotations,
              ERRORPRONE_CAN_IGNORE_RETURN_VALUE
          ) ||
          containsAnnotation(
              allPackageAnnotations,
              ERRORPRONE_CAN_IGNORE_RETURN_VALUE
          )
      ) {
        return
      }
      val methodName = JavaContext.getMethodName(element)
      val suggested = getAnnotationStringValue(
          annotation,
          ATTR_SUGGEST
      )
      var message = String.format(
          "The result of `%1\$s` is not used",
          methodName
      )
      if (suggested != null) {
        // TODO: Resolve suggest attribute (e.g. prefix annotations class if it starts
        // with "#" etc?
        message = String.format(
            "The result of `%1\$s` is not used; did you mean to call `%2\$s`?",
            methodName, suggested
        )
      } else if ("intersect" == methodName && context.evaluator.isMemberInClass(
              method,
              "android.graphics.Rect"
          )
      ) {
        message += ". If the rectangles do not intersect, no change is made and the " +
            "original rectangle is not modified. These methods return false to " +
            "indicate that this has happened."
      }
      val fix = if (suggested != null) {
        fix().data(suggested)
      } else {
        null
      }
      val location = context.getLocation(element)
      context.report(OPTIONAL_CHECK_RETURN_VALUE, element, location, message, fix)
    }
  }

  private fun isExpressionValueUnused(element: UElement): Boolean {
    var prev = element.getParentOfType<UExpression>(
        UExpression::class.java, false
    ) ?: return true
    var curr = prev.uastParent ?: return true
    while (curr is UQualifiedReferenceExpression && curr.selector === prev) {
      prev = curr
      curr = curr.uastParent ?: return true
    }
    @Suppress("RedundantIf")
    if (curr is UBlockExpression) {
      if (curr.uastParent is ULambdaExpression) {
        // Lambda block: for now assume used (e.g. parameter
        // in call. Later consider recursing here to
        // detect if the lambda itself is unused.
        return false
      }
      // In Java, it's apparent when an expression is unused:
      // the parent is a block expression. However, in Kotlin it's
      // much trickier: values can flow through blocks and up through
      // if statements, try statements.
      //
      // In Kotlin, we consider an expression unused if its parent
      // is not a block, OR, the expression is not the last statement
      // in the block, OR, recursively the parent expression is not
      // used (e.g. you're in an if, but that if statement is itself
      // not doing anything with the value.)
      val block = curr
      val expression = prev
      val index = block.expressions.indexOf(expression)
      if (index == -1) {
        return true
      }
      if (index < block.expressions.size - 1) {
        // Not last child
        return true
      }
      // It's the last child: see if the parent is unused
      val parent = curr.uastParent ?: return true
      if (parent is UMethod || parent is UClassInitializer) {
        return true
      }
      return isExpressionValueUnused(parent)
    } else if (curr is UMethod && curr.isConstructor) {
      return true
    } else {
      // Some other non block node type, such as assignment,
      // method declaration etc: not unused
      // TODO: Make sure that a void/unit method inline declaration
      // works correctly
      return false
    }
  }

  companion object {
    const val ERRORPRONE_CAN_IGNORE_RETURN_VALUE = "com.google.errorprone.annotations.CanIgnoreReturnValue"
    const val ATTR_SUGGEST = "suggest"
    private val IMPLEMENTATION = Implementation(
        OptionalCheckReturnValueDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
    )
    /** Method result should be used  */
    @JvmField
    val OPTIONAL_CHECK_RETURN_VALUE = Issue.create(
        id = "OptionalCheckReturnValue",
        briefDescription = "Ignoring results",
        explanation = """
                Some methods have no side effects, and calling them without doing something \
                without the result is suspicious.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
    )
  }
}
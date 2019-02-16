/*
 * Copyright 2012 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sweers.configurablecheckreturnvalue.errorprone;

import static com.google.errorprone.BugPattern.Category.JDK;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.enclosingPackage;
import static com.google.errorprone.util.ASTHelpers.hasDirectAnnotationWithSimpleName;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.Optional;
import javax.lang.model.element.ElementKind;

/**
 * Adapted from https://github
 * .com/google/error-prone/blob/976ab7c62771d54dfe11bde9b01dc301d364957b/core/src/main/java/com
 * /google/errorprone/bugpatterns/CheckReturnValue.java
 *
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@BugPattern(name = "OptionalCheckReturnValue", summary = "Ignored return value of method that is "
    + "annotated with @OptionalCheckReturnValue", category = JDK, severity = ERROR)
public class OptionalCheckReturnValue extends AbstractReturnValueIgnored
    implements MethodTreeMatcher, ClassTreeMatcher {

  private static final String OPTIONAL_CHECK_RETURN_VALUE = "OptionalCheckReturnValue";
  private static final String CAN_IGNORE_RETURN_VALUE = "CanIgnoreReturnValue";

  private static Optional<Boolean> shouldCheckReturnValue(Symbol sym) {
    if (hasDirectAnnotationWithSimpleName(sym, CAN_IGNORE_RETURN_VALUE)) {
      return Optional.of(false);
    }
    if (hasDirectAnnotationWithSimpleName(sym, OPTIONAL_CHECK_RETURN_VALUE)) {
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private static Optional<Boolean> checkEnclosingClasses(MethodSymbol method) {
    Symbol enclosingClass = enclosingClass(method);
    while (enclosingClass instanceof ClassSymbol) {
      Optional<Boolean> result = shouldCheckReturnValue(enclosingClass);
      if (result.isPresent()) {
        return result;
      }
      enclosingClass = enclosingClass.owner;
    }
    return Optional.empty();
  }

  private static Optional<Boolean> checkPackage(MethodSymbol method) {
    return shouldCheckReturnValue(enclosingPackage(method));
  }

  private static final Matcher<ExpressionTree> MATCHER =
      (Matcher<ExpressionTree>) (tree, state) -> {
        Symbol sym = ASTHelpers.getSymbol(tree);
        if (!(sym instanceof MethodSymbol)) {
          return false;
        }
        MethodSymbol method = (MethodSymbol) sym;
        Optional<Boolean> result = shouldCheckReturnValue(method);
        if (result.isPresent()) {
          return result.get();
        }

        result = checkEnclosingClasses(method);
        if (result.isPresent()) {
          return result.get();
        }

        result = checkPackage(method);
        if (result.isPresent()) {
          return result.get();
        }

        return false;
      };

  /**
   * Return a matcher for method invocations in which the method being called has the
   * {@code @OptionalCheckReturnValue} annotation.
   */
  @Override public Matcher<ExpressionTree> specializedMatcher() {
    return MATCHER;
  }

  private static final String BOTH_ERROR =
      "@OptionalCheckReturnValue and @CanIgnoreReturnValue cannot both be applied to the same %s";

  /**
   * Validate {@code @OptionalCheckReturnValue} and {@link CanIgnoreReturnValue} usage on methods.
   *
   * <p>The annotations should not both be appled to the same method.
   *
   * <p>The annotations should not be applied to void-returning methods. Doing so makes no sense,
   * because there is no return value to check.
   */
  @Override public Description matchMethod(MethodTree tree, VisitorState state) {
    MethodSymbol method = ASTHelpers.getSymbol(tree);

    boolean checkReturn = hasDirectAnnotationWithSimpleName(method, OPTIONAL_CHECK_RETURN_VALUE);
    boolean canIgnore = hasDirectAnnotationWithSimpleName(method, CAN_IGNORE_RETURN_VALUE);

    if (checkReturn && canIgnore) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "method"))
          .build();
    }

    String annotationToValidate;
    if (checkReturn) {
      annotationToValidate = OPTIONAL_CHECK_RETURN_VALUE;
    } else if (canIgnore) {
      annotationToValidate = CAN_IGNORE_RETURN_VALUE;
    } else {
      return Description.NO_MATCH;
    }
    if (method.getKind() != ElementKind.METHOD) {
      // skip contructors (which javac thinks are void-returning)
      return Description.NO_MATCH;
    }
    if (!ASTHelpers.isVoidType(method.getReturnType(), state)) {
      return Description.NO_MATCH;
    }
    String message =
        String.format("@%s may not be applied to void-returning methods", annotationToValidate);
    return buildDescription(tree).setMessage(message)
        .build();
  }

  /**
   * Validate that at most one of {@code OptionalCheckReturnValue} and {@code CanIgnoreReturnValue}
   * are
   * applied to a class (or interface or enum).
   */
  @Override public Description matchClass(ClassTree tree, VisitorState state) {
    if (hasDirectAnnotationWithSimpleName(ASTHelpers.getSymbol(tree), OPTIONAL_CHECK_RETURN_VALUE)
        && hasDirectAnnotationWithSimpleName(ASTHelpers.getSymbol(tree), CAN_IGNORE_RETURN_VALUE)) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "class"))
          .build();
    }
    return Description.NO_MATCH;
  }
}
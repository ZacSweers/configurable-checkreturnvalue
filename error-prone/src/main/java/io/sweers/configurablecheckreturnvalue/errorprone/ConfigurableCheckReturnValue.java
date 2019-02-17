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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.enclosingPackage;
import static com.google.errorprone.util.ASTHelpers.hasDirectAnnotationWithSimpleName;

/**
 * Configurable version of
 * <a href="https://github.com/google/error-prone/blob/f14fb18bb05c7e9f10794771df692a42b333f18c/core/src/main/java/com/google/errorprone/bugpatterns/CheckReturnValue.java">The
 * error prone version.</a>
 *
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@BugPattern(name = "ConfigurableCheckReturnValue", summary = "Ignored return value of method that"
    + " is annotated with @CheckReturnValue or specified alternatives", severity = ERROR)
public class ConfigurableCheckReturnValue extends AbstractReturnValueIgnored
    implements MethodTreeMatcher, ClassTreeMatcher {

  private static final String CAN_IGNORE_RETURN_VALUE = "CanIgnoreReturnValue";

  private static final ImmutableSet<String> DEFAULT_ANNOTATIONS = ImmutableSet.of(
      "CheckReturnValue",
      "androidx.annotation.CheckResult",
      "com.support.annotation.CheckResult",
      "edu.umd.cs.findbugs.annotations.CheckReturnValue",
      "javax.annotation.CheckReturnValue",
      "io.reactivex.annotations.CheckReturnValue",
      "com.google.errorprone.annotations.CheckReturnValue");
  private static final String BOTH_ERROR =
      "@CheckReturnValue and @CanIgnoreReturnValue cannot both be applied to the same %s";

  private ImmutableSet<String> annotationsToCheck;

  @SuppressWarnings("unused") // Default constructor used for SPI
  public ConfigurableCheckReturnValue() {
    this(ErrorProneFlags.empty());
  }

  @SuppressWarnings("WeakerAccess") // Public for ErrorProne
  public ConfigurableCheckReturnValue(ErrorProneFlags flags) {
    ImmutableSet<String> excludedAnnotations = flags.getList("ExcludeAnnotations")
        .map(ImmutableSet::copyOf)
        .orElse(ImmutableSet.of());

    annotationsToCheck = flags.getList("CustomAnnotations")
        .map(ImmutableSet::copyOf)
        .orElse(DEFAULT_ANNOTATIONS)
        .stream()
        .filter(e -> !excludedAnnotations.contains(e))
        .collect(toImmutableSet());
  }

  private Optional<String> checkReturn(Symbol sym) {
    for (String annotation : annotationsToCheck) {
      if (hasDirectAnnotationWithName(sym, annotation)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  /**
   * Check for the presence of an annotation with a specific simple name directly on this symbol.
   * Does *not* consider annotation inheritance.
   *
   * @param sym the symbol to check for the presence of the annotation
   * @param name TODO
   */
  public static boolean hasDirectAnnotationWithName(Symbol sym, String name) {
    boolean isSimple = !name.contains(".");
    for (AnnotationMirror annotation : sym.getAnnotationMirrors()) {
      Element element = annotation.getAnnotationType()
          .asElement();
      if (isSimple && element.getSimpleName().contentEquals(name)) {
        return true;
      } else if (element instanceof TypeElement && ((TypeElement) element).getQualifiedName()
          .contentEquals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validate {@code @CheckReturnValue} and {@link CanIgnoreReturnValue} usage on methods.
   *
   * <p>The annotations should not both be appled to the same method.
   *
   * <p>The annotations should not be applied to void-returning methods. Doing so makes no sense,
   * because there is no return value to check.
   */
  @Override public Description matchMethod(MethodTree tree, VisitorState state) {
    MethodSymbol method = ASTHelpers.getSymbol(tree);

    Optional<String> checkReturn = checkReturn(method);
    boolean canIgnore = hasDirectAnnotationWithSimpleName(method, CAN_IGNORE_RETURN_VALUE);

    if (checkReturn.isPresent() && canIgnore) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "method"))
          .build();
    }

    String annotationToValidate;
    if (checkReturn.isPresent()) {
      annotationToValidate = checkReturn.get();
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
        String.format("@%s may not be applied to void-returning methods", shortName(annotationToValidate));
    return buildDescription(tree).setMessage(message)
        .build();
  }

  private static String shortName(String name) {
    if (name.contains(".")) {
      return name.substring(name.lastIndexOf(".") + 1);
    } else {
      return name;
    }
  }

  /**
   * Validate that at most one of {@code CheckReturnValue} and {@code CanIgnoreReturnValue} are
   * applied to a class (or interface or enum).
   */
  @Override public Description matchClass(ClassTree tree, VisitorState state) {
    if (checkReturn(ASTHelpers.getSymbol(tree)).isPresent() && hasDirectAnnotationWithSimpleName(
        ASTHelpers.getSymbol(tree),
        CAN_IGNORE_RETURN_VALUE)) {
      return buildDescription(tree).setMessage(String.format(BOTH_ERROR, "class"))
          .build();
    }
    return Description.NO_MATCH;
  }

  /**
   * Return a matcher for method invocations in which the method being called has the
   * {@code @CheckReturnValue} annotation.
   */
  @Override public Matcher<ExpressionTree> specializedMatcher() {
    return MATCHER;
  }

  private Optional<Boolean> shouldCheckReturnValue(Symbol sym, VisitorState state) {
    if (hasDirectAnnotationWithSimpleName(sym, CAN_IGNORE_RETURN_VALUE)) {
      return Optional.of(false);
    }
    if (checkReturn(sym).isPresent()) {
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private Optional<Boolean> checkEnclosingClasses(MethodSymbol method, VisitorState state) {
    Symbol enclosingClass = enclosingClass(method);
    while (enclosingClass instanceof ClassSymbol) {
      Optional<Boolean> result = shouldCheckReturnValue(enclosingClass, state);
      if (result.isPresent()) {
        return result;
      }
      enclosingClass = enclosingClass.owner;
    }
    return Optional.empty();
  }

  private Optional<Boolean> checkPackage(MethodSymbol method, VisitorState state) {
    return shouldCheckReturnValue(enclosingPackage(method), state);
  }

  private final Matcher<ExpressionTree> MATCHER = (Matcher<ExpressionTree>) (tree, state) -> {
    Symbol sym = ASTHelpers.getSymbol(tree);
    if (!(sym instanceof MethodSymbol)) {
      return false;
    }
    MethodSymbol method = (MethodSymbol) sym;
    Optional<Boolean> result = shouldCheckReturnValue(method, state);
    if (result.isPresent()) {
      return result.get();
    }

    result = checkEnclosingClasses(method, state);
    if (result.isPresent()) {
      return result.get();
    }

    result = checkPackage(method, state);
    if (result.isPresent()) {
      return result.get();
    }

    return false;
  };
}
package io.sweers.rxjava2optionalcheckreturnvaluechecker.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Ignore
import org.junit.Test

class OptionalCheckReturnValueDetectorTest {

  private val annotation = java("""
    package io.reactivex.annotations;

    public @interface OptionalCheckReturnValue {
    }
  """).indented()

  private val canIgnoreReturnValue = java("""
    package com.google.errorprone.annotations;

    public @interface CanIgnoreReturnValue {
    }
  """).indented()

  @Test
  fun basicCheck() {
    lint()
        .files(annotation, java("""
          package test.foo;
          import io.reactivex.annotations.OptionalCheckReturnValue;
          class Example {
            @OptionalCheckReturnValue
            public int foo() {
                return 2;
            }
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(OptionalCheckReturnValueDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expect("""
          |src/test/foo/Example.java:9: Error: The result of foo is not used [OptionalCheckReturnValue]
          |    foo();
          |    ~~~~~
          |1 errors, 0 warnings""".trimMargin()
        )
  }

  @Ignore("Class annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnClass() {
    lint()
        .files(annotation, canIgnoreReturnValue, java("""
          package test.foo;
          import com.google.errorprone.annotations.CanIgnoreReturnValue;
          import io.reactivex.annotations.OptionalCheckReturnValue;
          @CanIgnoreReturnValue
          class Example {
            @OptionalCheckReturnValue
            public int foo() {
                return 2;
            }

            public void bar() {
              foo();
            }
          }""").indented())
        .issues(OptionalCheckReturnValueDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  @Ignore("Local method annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnMethod() {
    lint()
        .files(annotation, canIgnoreReturnValue, java("""
          package test.foo;
          import com.google.errorprone.annotations.CanIgnoreReturnValue;
          import io.reactivex.annotations.OptionalCheckReturnValue;
          class Example {
            @OptionalCheckReturnValue
            public int foo() {
                return 2;
            }

            @CanIgnoreReturnValue
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(OptionalCheckReturnValueDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  @Ignore("Package annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnPackage() {
    val packageInfo = java("""
        @com.google.errorprone.annotations.CanIgnoreReturnValue
        package test.lib;""").indented()
    lint()
        .files(annotation, canIgnoreReturnValue, packageInfo, java("""
          package test.lib;
          import io.reactivex.annotations.OptionalCheckReturnValue;
          class Example {
            @OptionalCheckReturnValue
            public int foo() {
                return 2;
            }

            public void bar() {
              foo();
            }
          }""").indented())
        .issues(OptionalCheckReturnValueDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }
}
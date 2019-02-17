package io.sweers.configurablecheckreturnvalue.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.projectProperties
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import io.sweers.configurablecheckreturnvalue.lint.ConfigurableCheckResultDetector.Companion.CUSTOM_ANNOTATIONS_KEY
import io.sweers.configurablecheckreturnvalue.lint.ConfigurableCheckResultDetector.Companion.EXCLUDE_ANNOTATIONS_KEY
import org.junit.Ignore
import org.junit.Test

class ConfigurableCheckResultDetectorTest {

  @Test
  fun basicCheck() {
    lint()
        .files(annotation, java("test/test/foo/Example.java", """
          package test.foo;
          import com.google.errorprone.annotations.CheckReturnValue;
          class Example {
            @CheckReturnValue
            public int foo() {
                return 2;
            }
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expect("""
          |test/test/foo/Example.java:9: Error: The result of foo is not used [ConfigurableCheckReturnValue]
          |    foo();
          |    ~~~~~
          |1 errors, 0 warnings""".trimMargin()
        )
  }

  @Test
  fun basicCheck_exclude() {
    lint()
        .files(propertiesFile(excludeAnnotations = listOf(
            "foo.bar.CustomAnnotation"
        )),
            customAnnotation("foo.bar", "CustomAnnotation"),
            java("test/test/foo/Example.java", """
          package test.foo;
          import foo.bar.CustomAnnotation;
          class Example {
            @CustomAnnotation
            public int foo() {
                return 2;
            }
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  @Test
  fun basicCheck_custom() {
    lint()
        .files(propertiesFile(customAnnotations = listOf(
            "foo.bar.CustomAnnotation"
        )),
            customAnnotation("foo.bar", "CustomAnnotation"),
            java("test/test/foo/Example.java", """
          package test.foo;
          import foo.bar.CustomAnnotation;
          class Example {
            @CustomAnnotation
            public int foo() {
                return 2;
            }
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expect("""
          |test/test/foo/Example.java:9: Error: The result of foo is not used [ConfigurableCheckReturnValue]
          |    foo();
          |    ~~~~~
          |1 errors, 0 warnings""".trimMargin()
        )
  }

  @Ignore("Class annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnClass() {
    lint()
        .files(annotation, canIgnoreReturnValue, java("test/test/foo/Example.java", """
          package test.foo;
          import com.google.errorprone.annotations.CanIgnoreReturnValue;
          import com.google.errorprone.annotations.CheckReturnValue;
          @CanIgnoreReturnValue
          class Example {
            @CheckReturnValue
            public int foo() {
                return 2;
            }

            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  @Ignore("Local method annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnMethod() {
    lint()
        .files(annotation, canIgnoreReturnValue, java("test/test/foo/Example.java", """
          package test.foo;
          import com.google.errorprone.annotations.CanIgnoreReturnValue;
          import com.google.errorprone.annotations.CheckReturnValue;
          class Example {
            @CheckReturnValue
            public int foo() {
                return 2;
            }

            @CanIgnoreReturnValue
            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  @Ignore("Package annotations aren't getting parsed?")
  @Test
  fun canIgnoreOnPackage() {
    val packageInfo = java("test/test/foo/package-info.java", """
        @com.google.errorprone.annotations.CanIgnoreReturnValue
        package test.lib;""").indented()
    lint()
        .files(annotation, canIgnoreReturnValue, packageInfo, java("test/test/foo/Example.java", """
          package test.lib;
          import com.google.errorprone.annotations.CheckReturnValue;
          class Example {
            @CheckReturnValue
            public int foo() {
                return 2;
            }

            public void bar() {
              foo();
            }
          }""").indented())
        .issues(ConfigurableCheckResultDetector.OPTIONAL_CHECK_RETURN_VALUE)
        .run()
        .expectClean()
  }

  companion object {
    private val annotation = java("test/com/google/errorprone/annotations/CheckReturnValue.java",
        """
    package com.google.errorprone.annotations;

    public @interface CheckReturnValue {
    }
  """).indented()

    private val canIgnoreReturnValue = java(
        "test/com/google/errorprone/annotations/CanIgnoreReturnValue.java", """
    package com.google.errorprone.annotations;

    public @interface CanIgnoreReturnValue {
    }
  """).indented()

    private fun customAnnotation(packageName: String, name: String): LintDetectorTest.TestFile {
      return java("test/${packageName.replace(".", "/")}/$name.java",
          """
    package $packageName;

    public @interface $name {
    }
  """).indented()
    }

    private fun propertiesFile(
        customAnnotations: List<String>? = null,
        excludeAnnotations: List<String>? = null
    ): TestFile.PropertyTestFile {
      val properties = projectProperties()
      customAnnotations?.let {
        properties.property(CUSTOM_ANNOTATIONS_KEY, it.joinToString(":"))
      }
      excludeAnnotations?.let {
        properties.property(EXCLUDE_ANNOTATIONS_KEY, it.joinToString(":"))
      }
      properties.to(ConfigurableCheckResultDetector.PROPERTY_FILE)
      return properties
    }
  }
}
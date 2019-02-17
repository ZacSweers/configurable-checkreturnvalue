Configurable CheckReturnValue
=========================================

[![Build Status](https://travis-ci.org/ZacSweers/configurablecheckreturnvalue.svg?branch=master)](https://travis-ci.org/ZacSweers/configurablecheckreturnvalue)

Configurable Lint and Error-Prone alternative checkers for `@CheckReturnValue`.

Integration
-----------

## Lint



## Error Prone

Gradle, using [`net.ltgt.errorprone` plugin](https://github.com/tbroyer/gradle-errorprone-plugin):

[![Maven Central](https://img.shields.io/maven-central/v/io.sweers.configurablecheckreturnvalue/error-prone.svg)](https://mvnrepository.com/artifact/io.sweers.configurablecheckreturnvalue/error-prone)
```groovy
dependencies {
  errorprone 'io.sweers.configurablecheckreturnvalue:error-prone:x.y.z'
}
```

By default, a common set of known `CheckReturnValue`-esque annotations will be used to match the
standard Error-Prone `CheckReturnValue` checker, as well as fuzzy matching on any annotation with 
simple name `CheckReturnValue`. To configure a custom set, you can define them with a `:`-delimited
string of fully qualified class names or simple names via `CustomAnnotations`. These override the default
 set. 
 
If you want to use the standard set but just exclude some, you can define them as a `:`-delimited string
of fully qualified class names or simple names via `ExcludeAnnotations`.

For Java projects:
```groovy
import net.ltgt.gradle.errorprone.CheckSeverity

def customAnnotations = [
    "my.custom.annotation.CheckReturn"
]
def excludeAnnotations = [
    "some.excluded.CheckReturnAnnotation"
]
tasks.withType(JavaCompile).configure {
  check("ConfigurableCheckReturnValue", CheckSeverity.ERROR)
  options.errorprone.option("ConfigurableCheckReturnValue:CustomAnnotations", customAnnotations.join(":"))
  options.errorprone.option("ConfigurableCheckReturnValue:ExcludeAnnotations", excludeAnnotations.join(":"))
}
```

For Android projects:
```groovy
import net.ltgt.gradle.errorprone.CheckSeverity

def customAnnotations = [
  "my.custom.annotation.CheckReturn"
]
def excludeAnnotations = [
  "some.excluded.CheckReturnAnnotation"
]
// Use `libraryVariants` for com.android.library, `applicationVariants` for com.android.application
android.(libraryVariants|applicationVariants).all { variant ->
  variant.javaCompileProvider.configure {
    options.errorprone {
      check("ConfigurableCheckReturnValue", CheckSeverity.ERROR)
      options.errorprone.option("ConfigurableCheckReturnValue:CustomAnnotations", customAnnotations.join(":"))
      options.errorprone.option("ConfigurableCheckReturnValue:ExcludeAnnotations", excludeAnnotations.join(":"))
    }
  }
}
```

Snapshots of the development version are available in [Sonatype's snapshots repository][snapshots].

License
-------

    Copyright (C) 2018 Zac Sweers

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 [snapshots]: https://oss.sonatype.org/content/repositories/snapshots/

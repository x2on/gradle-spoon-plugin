# gradle-spoon-plugin [![Build Status](https://travis-ci.org/x2on/gradle-spoon-plugin.png)](https://travis-ci.org/x2on/gradle-spoon-plugin)
A Gradle plugin for running Android instrumentation tests with Spoon.

## Basic usage

Add to your build.gradle

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'de.felixschulze.gradle:gradle-spoon-plugin:0.9.1+'
    }
}

apply plugin: 'spoon'
```

## License

gradle-hockeyapp-plugin is available under the MIT license. See the LICENSE file for more info.

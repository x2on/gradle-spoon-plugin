# gradle-spoon-plugin [![Build Status](https://travis-ci.org/x2on/gradle-spoon-plugin.png)](https://travis-ci.org/x2on/gradle-spoon-plugin)
A Gradle plugin for running Android instrumentation tests with [Spoon](http://square.github.io/spoon/).

## Basic usage

Add to your build.gradle

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'de.felixschulze.gradle:gradle-spoon-plugin:0.9.6+'
    }
}

apply plugin: 'spoon'
```

## Advanced usage

Add to your build.gradle

```gradle
spoon {
    teamCityLog = true
    debug = true
    failOnFailure = false
}
```

* `teamCityLog`: Add features for [TeamCity](http://www.jetbrains.com/teamcity/)
* `debug`: Activate debug output for spoon
* `failOnFailure`: Deactivate exit code on failure

## Changelog

[Releases](https://github.com/x2on/gradle-spoon-plugin/releases)

## License

gradle-spoon-plugin is available under the MIT license. See the LICENSE file for more info.

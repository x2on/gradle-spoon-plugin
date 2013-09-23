/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Felix Schulze
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.felixschulze.gradle

import com.squareup.spoon.SpoonRunner
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern

class SpoonTestTask extends DefaultTask {

    SpoonTestTask() {
        super()
        this.description = "Run instrumentation tests on all connected devices."
    }

    @TaskAction
    def runInstrumentationTests() throws IOException {

        def applicationApk = getFile(project.spoon.apkFileNameRegex)
        def instrumentationApk = getFile(project.spoon.testApkFileNameRegex)

        println "ApplicationApk: " + applicationApk
        println "InstrumentationApk: " + instrumentationApk

        if (!applicationApk || !instrumentationApk) {
            throw new IllegalArgumentException("APK not found.")
        }

        SpoonRunner spoonRunner = new SpoonRunner.Builder() //
                .setTitle("Spoon Execution from gradle-spoon-plugin")
                .setApplicationApk(applicationApk)
                .setInstrumentationApk(instrumentationApk)
                .setOutputDirectory(project.spoon.outputDirectory)
                .setAndroidSdk(cleanFile(System.getenv("ANDROID_HOME")))
                .useAllAttachedDevices()
                .build();


        if (!spoonRunner.run() && project.spoon.failOnFailure) {
            if (project.spoon.teamCityLog) {
                logJUnitXmlToTeamCity()
            }
            System.exit(1)
        }
        else {
            if (project.spoon.teamCityLog) {
                logJUnitXmlToTeamCity()
            }
        }

    }

    def logJUnitXmlToTeamCity() {
        File junitDir = new File(project.spoon.outputDirectory, "junit-reports")
        if (junitDir.exists()) {
            junitDir.eachFile {
                if(it.name.endsWith('.xml')) {
                    println "##teamcity[importData type='junit' path='${it.canonicalPath}']"
                }
            }
        }
    }

    def getFile(String regex) {
        def pattern = Pattern.compile(regex)

        if (!project.spoon.apkDirectory.exists()) {
            throw new IllegalStateException("OutputDirectory not found")
        }

        def fileList = project.spoon.apkDirectory.list(
                [accept: { d, f -> f ==~ pattern }] as FilenameFilter
        ).toList()

        if (fileList == null || fileList.size() == 0) {
            return null
        }
        return new File(project.spoon.apkDirectory, fileList[0])
    }

    private static File cleanFile(String path) {
        if (path == null) {
            return null;
        }
        return new File(path);
    }


}

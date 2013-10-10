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

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner
import com.squareup.spoon.SpoonRunner
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.TestVariant
import org.gradle.api.plugins.JavaBasePlugin

class SpoonPlugin implements Plugin<Project> {

    void apply(Project project) {
        configureDependencies(project)
        applyExtensions(project)
    }

    void applyExtensions(final Project project) {
        project.extensions.create('spoon', SpoonPluginExtension, project)
        project.afterEvaluate {
            applyTasks(project)
        }
    }

    void applyTasks(final Project project) {
        if (!project.plugins.hasPlugin(AppPlugin)) {
            throw new IllegalStateException("gradle-android-plugin not found")
        } else {
            AppExtension android = project.android
            android.testVariants.all { TestVariant variant ->
                createTask(project, variant, null) // Always create an 'all' target
                project.spoon.testSizes.each { String testSize ->
                    createTask(project, variant, IRemoteAndroidTestRunner.TestSize.getTestSize(testSize))
                }
            }
        }
    }

    void createTask(final Project project, final TestVariant variant,
                    final IRemoteAndroidTestRunner.TestSize testSize) {

        String sizeString = testSize ? testSize.name().toLowerCase() : "all"

        SpoonTestTask task = project.tasks.create("spoon${testSize ? sizeString.capitalize() : ""}${variant.name}", SpoonTestTask)
        task.group = JavaBasePlugin.VERIFICATION_GROUP
        task.description = "Run ${sizeString} instrumentation tests on all connected devices for '${variant.name}'"
        task.title = "$variant.name (gradle-spoon-plugin)"
        task.output = new File(project.buildDir, SpoonRunner.DEFAULT_OUTPUT_DIRECTORY + "/${testSize ? sizeString : ""}${variant.name}")
        task.applicationApk = variant.testedVariant.outputFile
        task.instrumentationApk = variant.outputFile
        task.setTestSize(testSize)
        task.outputs.upToDateWhen { false }

        task.dependsOn variant.assemble, variant.testedVariant.assemble
    }

    void configureDependencies(final Project project) {
        project.repositories {
            mavenCentral()
        }
    }

}
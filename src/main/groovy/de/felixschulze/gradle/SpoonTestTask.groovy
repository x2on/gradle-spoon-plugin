/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013-2015 Felix Schulze
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

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner
import com.squareup.spoon.DeviceResult
import com.squareup.spoon.SpoonRunner
import com.squareup.spoon.SpoonSummary
import com.squareup.spoon.SpoonUtils
import de.felixschulze.teamcity.TeamCityImportDataType
import de.felixschulze.teamcity.TeamCityProgressType
import de.felixschulze.teamcity.TeamCityStatusMessageHelper
import de.felixschulze.teamcity.TeamCityStatusType
import org.gradle.api.DefaultTask
import org.gradle.api.Nullable
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import static com.squareup.spoon.SpoonUtils.GSON as GSON

class SpoonTestTask extends DefaultTask {

    @InputFile
    File instrumentationApk

    @InputFile
    File applicationApk

    @OutputDirectory
    File output

    String title

    String sdkDir

    String testClassName

    String testMethodName

    Collection<String> excludedDevices

    @Nullable
    IRemoteAndroidTestRunner.TestSize testSize;

    @TaskAction
    def runInstrumentationTests() throws IOException {

        if (!applicationApk || !instrumentationApk) {
            throw new IllegalArgumentException("apk files not found.")
        }

        Boolean isDebugEnabled = logger.isDebugEnabled() || project.spoon.debug;
        Boolean isTeamCityLogEnabled = project.spoon.teamCityLog

        excludedDevices = project.spoon.excludedDevices

        // Workaround for bug in gradle-android-plugin 1.5.0
        // @see https://code.google.com/p/android/issues/detail?id=194609
        // @see http://stackoverflow.com/a/33232335/268795
        if (project.spoon.disableDexVerification) {
            logger.info("Dex verification disabled on connected devices.")
            IDevice[] devices = SpoonUtils.initAdb(cleanFile(sdkDir), project.spoon.adbTimeout * 1000).getDevices()
            IShellOutputReceiver outputReceiver = new IShellOutputReceiver() {
                @Override
                void addOutput(byte[] data, int offset, int length) {}

                @Override
                void flush() {}

                @Override
                boolean isCancelled() {
                    return false
                }
            }
            if (devices.size() > 0) {
                logger.debug("Run adb shell command: setprop dalvik.vm.dexopt-flags v=n,o=v")
                logger.debug("Run adb shell command: stop installd")
                logger.debug("Run adb shell command: start installd")
            }
            devices.each {
                if (!excludedDevices.contains(it.serialNumber)) {
                    try {
                        it.executeShellCommand("setprop dalvik.vm.dexopt-flags v=n,o=v", outputReceiver)
                        it.executeShellCommand("stop installd", outputReceiver)
                        it.executeShellCommand("start installd", outputReceiver)
                    }
                    catch (all) {
                        logger.warn("Exception while executing adb shell command on '" + it.serialNumber + "': " + all.message)
                    }
                }
            }
        }

        SpoonRunner.Builder spoonRunnerBuilder = new SpoonRunner.Builder()
                .setTitle(title)
                .setApplicationApk(applicationApk)
                .setInstrumentationApk(instrumentationApk)
                .setOutputDirectory(output)
                .setAndroidSdk(cleanFile(sdkDir))
                .setDebug(isDebugEnabled)
                .setTestSize(testSize)
                .setClasspath(project.buildscript.configurations.classpath.asPath)
                .setFailIfNoDeviceConnected(project.spoon.failIfNoDeviceConnected)
                .setAdbTimeout(project.spoon.adbTimeout * 1000)
                .setClassName(testClassName)
                .setMethodName(testMethodName)

        if (excludedDevices.empty) {
            spoonRunnerBuilder.useAllAttachedDevices()
        }
        else {
            Set<String> devices = SpoonUtils.findAllDevices(SpoonUtils.initAdb(cleanFile(sdkDir), project.spoon.adbTimeout * 1000))
            devices.each {
                if (excludedDevices.contains(it)) {
                    logger.info("Skip device: ${it}")
                }
                else {
                    logger.info("Use device: ${it}")
                    spoonRunnerBuilder.addDevice(it)
                }
            }
        }

        Collection<String> instrumentationArgs = project.spoon.instrumentationArgs
        if (!instrumentationArgs.empty) {
            spoonRunnerBuilder.setInstrumentationArgs(instrumentationArgs.toList())
        }

        SpoonRunner spoonRunner = spoonRunnerBuilder.build()

        if (isTeamCityLogEnabled) {
            println TeamCityStatusMessageHelper.buildProgressString(TeamCityProgressType.START, "Spoon-Tests running...")
        }

        boolean succeeded = spoonRunner.run()

        if (isTeamCityLogEnabled) {
            println TeamCityStatusMessageHelper.buildProgressString(TeamCityProgressType.FINISH, succeeded ? "Spoon-Tests finished." : "Spoon-Test failed.")
        }

        if (isTeamCityLogEnabled) {
            boolean logSuccessful = logJUnitXmlToTeamCity()
            if (succeeded && !logSuccessful) {
                succeeded = false
            }
        }

        if (project.spoon.zipReport) {
            new AntBuilder().zip(
                    destfile: new File(project.getBuildDir(),"spoon.zip").absolutePath,
                    basedir: output.absolutePath
            )
        }

        if (!succeeded && project.spoon.failOnFailure) {
            //Check if apk could install on all devices
            output.eachFile {
                if (it.name.endsWith('.json')) {
                    if (it.exists()) {
                        FileReader resultFile = new FileReader(it);
                        SpoonSummary result = GSON.fromJson(resultFile, SpoonSummary.class);
                        if (result) {
                            Map<String, DeviceResult> deviceResultMap = result.results;
                            for (DeviceResult deviceResult in deviceResultMap.values()) {
                                if (deviceResult.installFailed && deviceResult.installMessage) {
                                    if (isTeamCityLogEnabled) {
                                        println TeamCityStatusMessageHelper.buildStatusString(TeamCityStatusType.ERROR, "Error: " + deviceResult.installMessage)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            System.exit(1)
        }
    }

    private boolean logJUnitXmlToTeamCity() {
        File jUnitDir = new File(output, "junit-reports")
        logger.debug("Looking for junit-reports in: "+jUnitDir.absolutePath)
        if (jUnitDir.exists()) {
            jUnitDir.eachFile {
                if (it.name.endsWith('.xml')) {
                    println TeamCityStatusMessageHelper.importDataString(TeamCityImportDataType.JUNIT, it.canonicalPath);

                    try {
                        String fileContents = new File(it.canonicalPath).text
                        def testsuite = new XmlParser().parseText(fileContents)
                        if (testsuite.name() == "testsuite") {
                            if (testsuite.@tests == "0") {
                                logger.error("No tests executed")
                                println TeamCityStatusMessageHelper.buildStatusString(TeamCityStatusType.ERROR, "No tests executed")
                            }
                        }
                    } catch (all) {
                        logger.error(all.message)
                    }

                }
            }
            return true
        } else {
            return false
        }
    }

    private static File cleanFile(String path) {
        if (path == null) {
            return null;
        }
        return new File(path);
    }


}

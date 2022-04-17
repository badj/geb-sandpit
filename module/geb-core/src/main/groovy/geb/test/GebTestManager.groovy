/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geb.test

import geb.Browser

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

import static geb.report.ReporterSupport.toTestReportLabel

class GebTestManager {

    private final static Map<Class<?>, AtomicInteger> TEST_COUNTERS = new ConcurrentHashMap<>()
    private final static Map<Class<?>, Consumer<Browser>> BROWSER_CONFIGURERS = new ConcurrentHashMap<>()

    private final Supplier<Browser> browserCreator
    private final Predicate<Class<?>> resetBrowserAfterEachTestPredicate
    final boolean reportingEnabled

    protected Browser browser
    private final Deque<Class<?>> currentTestClassStack = new ArrayDeque()
    private final Deque<Integer> perTestReportCounter = new ArrayDeque()
    private final Deque<Integer> testCounter = new ArrayDeque()
    private String currentTestName

    GebTestManager(
            Supplier<Browser> browserCreator, Predicate<Class<?>> resetBrowserAfterEachTestPredicate,
            boolean reportingEnabled
    ) {
        this.browserCreator = browserCreator
        this.resetBrowserAfterEachTestPredicate = resetBrowserAfterEachTestPredicate
        this.reportingEnabled = reportingEnabled
    }

    Browser getBrowser() {
        if (browser == null) {
            browser = createBrowser()
        }
        browser
    }

    void report(String label = "") {
        if (!reportingEnabled) {
            throw new IllegalStateException("Reporting has not been enabled on this GebTestManager yet report() was called")
        }
        getBrowser().report(createReportLabel(label))
        perTestReportCounter.push(perTestReportCounter.pop() + 1)
    }

    void reportFailure() {
        report("failure")
    }

    void beforeTestClass(Class<?> testClass) {
        currentTestClassStack.push(testClass)
        if (reportingEnabled) {
            getBrowser().reportGroup(testClass)
            getBrowser().cleanReportGroupDir()
            BROWSER_CONFIGURERS.put(testClass, { Browser browser ->
                browser.reportGroup(testClass)
            } as Consumer<Browser>)
            testCounter.push(nextTestCounter(testClass))
            perTestReportCounter.push(1)
        }
    }

    void beforeTest(Class<?> testClass, String testName) {
        currentTestClassStack.push(testClass)
        currentTestName = testName
        if (reportingEnabled) {
            testCounter.push(nextTestCounter(testClass))
            perTestReportCounter.push(1)
        }
    }

    void afterTest() {
        if (reportingEnabled) {
            if (browser && !browser.config.reportOnTestFailureOnly) {
                report("end")
            }
            perTestReportCounter.pop()
            testCounter.pop()
        }

        if (resetBrowserAfterEachTest) {
            resetBrowser()
        }
        currentTestName = null
        currentTestClassStack.pop()
    }

    void afterTestClass() {
        if (reportingEnabled) {
            perTestReportCounter.pop()
            testCounter.pop()
            BROWSER_CONFIGURERS.remove(currentTestClass)
        }

        if (!resetBrowserAfterEachTest) {
            resetBrowser()
        }

        currentTestClassStack.pop()
    }

    String createReportLabel(String label) {
        def methodName = currentTestName ?: 'fixture'
        toTestReportLabel(currentTestCounter, currentPerTestReportCounter, methodName, label)
    }

    void resetBrowser() {
        def config = browser?.config
        if (config?.autoClearCookies) {
            browser.clearCookiesQuietly()
        }
        if (config?.autoClearWebStorage) {
            browser.clearWebStorage()
        }
        if (config?.quitDriverOnBrowserReset) {
            browser.driver.quit()
        }
        browser = null
    }

    private int nextTestCounter(Class<?> testClass) {
        TEST_COUNTERS.putIfAbsent(testClass, new AtomicInteger(0))
        TEST_COUNTERS[testClass].getAndIncrement()
    }

    private Browser createBrowser() {
        def browser = browserCreator ? browserCreator.get() : new Browser()
        currentTestClass?.with(BROWSER_CONFIGURERS.&get)?.accept(browser)
        browser
    }

    private boolean getResetBrowserAfterEachTest() {
        resetBrowserAfterEachTestPredicate.test(currentTestClass)
    }

    private int getCurrentTestCounter() {
        testCounter.peek()
    }

    private int getCurrentPerTestReportCounter() {
        perTestReportCounter.peek()
    }

    private Class<?> getCurrentTestClass() {
        currentTestClassStack.peek()
    }

}

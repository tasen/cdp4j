/**
 * The MIT License
 * Copyright © 2017 WebFolder OÜ
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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.webfolder.cdp;

import static io.webfolder.cdp.session.SessionFactory.DEFAULT_HOST;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.file.Paths.get;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Locale.ENGLISH;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import io.webfolder.cdp.exception.CdpException;
import io.webfolder.cdp.session.SessionFactory;
import io.webfolder.cdp.session.SessionInfo;

public class Launcher {

    private final SessionFactory factory;

    public Launcher() {
        this(new SessionFactory());
    }

    public Launcher(final SessionFactory factory) {
        this.factory = factory;
    }

    public String findChrome() {
        String os = getProperty("os.name")
                        .toLowerCase(ENGLISH);
        boolean windows = os.startsWith("windows");
        if (windows) {
            try {
                for (String path : getChromeWinPaths()) {
                    final Process process = getRuntime().exec(new String[] {
                            "cmd", "/c", "echo", path
                    });
                    final int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        final String location = toString(process.getInputStream()).trim().replace("\"", "");
                        final File chrome = new File(location);
                        if (chrome.exists() && chrome.canExecute()) {
                            return chrome.toString();
                        }
                    }                    
                }
                throw new CdpException("Unable to find chrome.exe");
            } catch (Throwable e) {
                // ignore
            }
        } else {
            return "google-chrome";
        }
        return null;
    }

    protected List<String> getChromeWinPaths() {
        return asList(
                "%localappdata%\\Google\\Chrome SxS\\Application\\chrome.exe", // Chrome Canary
                "%programfiles%\\Google\\Chrome\\Application\\chrome.exe",     // Chrome Stable 64-bit
                "%programfiles(x86)%\\Google\\Chrome\\Application\\chrome.exe" // Chrome Stable 32-bit
        );
    }

    public SessionFactory launch() {
        return launch(new String[] { });
    }

    public SessionFactory launch(String... arguments) {
        if (launched()) {
            return factory;
        }
        String chromePath = findChrome();
        Path remoteProfileData = get(getProperty("java.io.tmpdir"))
                                        .resolve("remote-profile");
        List<String> list = new ArrayList<>();
        list.add(chromePath);
        list.add(format("--remote-debugging-port=%d", factory.getPort()));
        list.add(format("--user-data-dir=%s", remoteProfileData.toString()));
        if ( ! DEFAULT_HOST.equals(factory.getHost()) ) {
            list.add(format("--remote-debugging-address=%s", factory.getHost()));
        }
        list.add("--disable-translate");
        list.add("--disable-extensions");
        list.add("--no-default-browser-check");
        list.add("--disable-plugin-power-saver");
        list.add("--disable-sync");
        list.add("--no-first-run");
        list.add("--safebrowsing-disable-auto-update");
        list.add("--disable-popup-blocking");

        if (arguments != null) {
            list.addAll(asList(arguments));
        }

        try {
            Process process = getRuntime().exec(list.toArray(new String[0]));
            process.getOutputStream().close();
            process.getInputStream().close();
        } catch (IOException e) {
            throw new CdpException(e);
        }

        return factory;
    }

    protected String toString(InputStream is) {
        try (Scanner scanner = new Scanner(is)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public boolean launched() {
        List<SessionInfo> list = emptyList();
        try {
            list = factory.list();
        } catch (Throwable t) {
            // ignore
        }
        return ! list.isEmpty() ? true : false;
    }
}

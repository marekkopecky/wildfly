/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.eap.expansion.pack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.version.ProductConfig;
import org.jboss.eap.expansion.pack._private.ExpansionPackLogger;
import org.jboss.msc.Service;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

public final class ExpansionPackDependencyVerifier implements Service {

    private static final AtomicBoolean installed = new AtomicBoolean();

    @SuppressWarnings("unused")
    public static void installVerifier(ServiceTarget target) {

        synchronized (installed) {
            if (!installed.get()) {
                ServiceName svcName = ServiceName.JBOSS.append("eap", "expansion", "pack", "verifier");

                try {
                    ServiceBuilder<?> builder = target.addService(svcName);
                    Supplier<ProductConfig> supplier = builder.requires(ServiceName.JBOSS.append("as", "product-config")); // TODO use a capability
                    builder.setInstance(new ExpansionPackDependencyVerifier(supplier)).install();
                    installed.set(true);
                } catch (ServiceRegistryException e) {
                    // ignore. Just means this check doesn't happen which isn't critical
                }
            }
        }
    }

    private final Supplier<ProductConfig> supplier;

    private ExpansionPackDependencyVerifier(final Supplier<ProductConfig> supplier) {
        this.supplier = supplier;
    }

    @Override
    public void start(StartContext startContext) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("validation.properties");
        if (is != null) {
            try {
                try {
                    Properties properties = new Properties();
                    properties.load(is);
                    String requiredBaseVersion = properties.getProperty("required.base.version");
                    String xpName = properties.getProperty("expansion.pack.release.name");
                    xpName = xpName == null ? "JBoss EAP XP" : xpName;
                    if (requiredBaseVersion != null && !isBaseCompatible(requiredBaseVersion, supplier.get().getProductVersion())) {
                        String xpVer = properties.getProperty("expansion.pack.release.version");
                        ExpansionPackLogger.VERIFY_LOGGER.incorrectBaseVersion(
                                xpName,
                                xpVer == null ? "" : xpVer,
                                supplier.get().getProductName(),
                                requiredBaseVersion,
                                supplier.get().getProductVersion()
                        );
                    } else {
                        ExpansionPackLogger.VERIFY_LOGGER.correctBaseVersion(requiredBaseVersion, supplier.get().getProductVersion());
                        ExpansionPackLogger.LOGGER.expansionPackActive(xpName);
                    }

                } finally {
                    is.close();
                }
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        installed.set(false);
    }

    // Package protected to allow unit testing
    static boolean isBaseCompatible(String requiredBaseVersion, String actualBaseVersion) {
        boolean result = requiredBaseVersion.equals(actualBaseVersion);
        if (!result && actualBaseVersion != null) {
            // 8.0 Update pattern
            Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+) Update (\\d+)\\.(\\d+).*");
            Matcher requiredMatcher = pattern.matcher(requiredBaseVersion);
            Matcher actualMatcher = pattern.matcher(actualBaseVersion);

            if (actualMatcher.matches() && requiredMatcher.matches()) {
                String requiredMajor = requiredMatcher.group(1);
                String requiredMinor = requiredMatcher.group(2);
                String actualMajor = actualMatcher.group(1);
                String actualMinor = actualMatcher.group(2);
                if (requiredMajor.equals(actualMajor) && requiredMinor.equals(actualMinor)) {
                    try {
                        int actualUpdate = Integer.parseInt(actualMatcher.group(3));
                        int requiredUpdate = Integer.parseInt(requiredMatcher.group(3));
                        if (actualUpdate < requiredUpdate) {
                            return false;
                        }
                        if (actualUpdate > requiredUpdate) {
                            return true;
                        }
                        int actualExpeditedFix = Integer.parseInt(actualMatcher.group(4));
                        int requiredExpeditedFix = Integer.parseInt(requiredMatcher.group(4));
                        return actualExpeditedFix >= requiredExpeditedFix;
                    } catch (NumberFormatException nfe) {
                        logIncomparable(requiredBaseVersion, actualBaseVersion);
                        return false;
                    }
                } else {
                    return false;
                }
            }

            // If the versions can be parsed into major.minor.micro[.suffix...], all match but micro,
            // and required micro is <= the actual micro, then actual is compatible.
            String[] requiredSplit = requiredBaseVersion.split("\\.");
            String[] actualSplit = actualBaseVersion.split("\\.");

            if (requiredSplit.length > 2 && requiredSplit.length == actualSplit.length) {
                try {
                    if (Integer.parseInt(requiredSplit[0]) == Integer.parseInt(actualSplit[0])
                            && Integer.parseInt(requiredSplit[1]) == Integer.parseInt(actualSplit[1])
                            && Integer.parseInt(requiredSplit[2]) <= Integer.parseInt(actualSplit[2])
                    ) {
                        result = true;
                        if (requiredSplit.length > 3) {
                            for (int i = 3; result && i < requiredSplit.length; i++) {
                                result = requiredSplit[i].equals(actualSplit[i]);
                            }
                        }
                    }
                } catch (NumberFormatException nfe) {
                    logIncomparable(requiredBaseVersion, actualBaseVersion);
                    // fall through and return false
                }
            } else {
                logIncomparable(requiredBaseVersion, actualBaseVersion);
                // fall through and return false
            }
        }
        return result;
    }

    private static void logIncomparable(String requiredBaseVersion, String actualBaseVersion) {
        ExpansionPackLogger.VERIFY_LOGGER.debugf("Unable to compare required version %s to actual version %s", requiredBaseVersion, actualBaseVersion);
    }
}
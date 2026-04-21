/*
 * Copyright 2023 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.eap.insights.report;

import com.redhat.insights.jars.JarAnalyzer;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.jars.JarInfoSubreport;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.client.ModelControllerClient;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import org.jboss.dmr.ModelNode;
import org.jboss.eap.insights.report.logging.InsightsReportLogger;
import org.jboss.eap.insights.report.logging.JbossLoggingInsightsLogger;

/**
 *
 * @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc.
 */
class JBossJarInfoModuleSubReport extends JarInfoSubreport {

    private final JarAnalyzer analyzer;
    private final boolean rescan;
    private final Set<String> processedJars;
    private final ModelControllerClient modelControllerClient;

    JBossJarInfoModuleSubReport(boolean rescan, final ModelControllerClientFactory clientFactory, final Executor executor) {
        super(JbossLoggingInsightsLogger.INSTANCE);
        this.analyzer = new JarAnalyzer(JbossLoggingInsightsLogger.INSTANCE, true);
        this.processedJars = new HashSet<>();
        this.rescan = rescan;
        this.modelControllerClient = clientFactory.createSuperUserClient(executor, false);
    }

    @Override
    public void generateReport() {
        if (rescan) {
            processedJars.clear();
            jarInfos.clear();
        }
        Set<String> alreadyProcessedModules = new HashSet<>();
        Set<Path> modulesRoots = listRepoRoots();
        for (Path modulePath : modulesRoots) {
            InsightsReportLogger.ROOT_LOGGER.startProcessingModulePath(modulePath);
            try {
                Files.walkFileTree(modulePath, new FileVisitor<Path>() {
                    private boolean overridden = false;

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        String relativeModulePath = formatPath(modulePath.relativize(dir));
                        if (shouldSkipModuleRoot(modulePath, modulesRoots, dir.toAbsolutePath())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        //If the module has already been processed or if it is a part of ".overlays" directory without being a module-root (aka the current overlay).
                        if (alreadyProcessedModules.contains(relativeModulePath) || isOverlays(relativeModulePath)) {
                            overridden = true;
                            return FileVisitResult.CONTINUE;
                        }
                        if (!relativeModulePath.isEmpty() && "main".equals(dir.getFileName().toString())) {
                            overridden = false;
                            alreadyProcessedModules.add(relativeModulePath);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!processedJars.contains(file.toAbsolutePath().toString())) {
                            processedJars.add(file.toAbsolutePath().toString());
                            if (isArchive(file)) {
                                if (overridden) {
                                    JarInfo jarInfo = new JarInfo(file.getFileName().toString(), "", new HashMap<>());
                                    jarInfo.attributes().put("module-root", modulePath.toString());
                                    jarInfo.attributes().put("path", formatPath(modulePath.relativize(file)));
                                    jarInfo.attributes().put("overridden", "true");
                                    InsightsReportLogger.ROOT_LOGGER.addingOverriddenJar(jarInfo);
                                    jarInfos.add(jarInfo);
                                } else {
                                    try {
                                        Optional<JarInfo> info = analyzer.process(file.toUri().toURL());
                                        if (info.isPresent()) {
                                            JarInfo jarInfo = info.get();
                                            jarInfo.attributes().put("path", formatPath(modulePath.relativize(file)));
                                            jarInfo.attributes().put("overridden", "unknown");
                                            InsightsReportLogger.ROOT_LOGGER.addingAnalyzedJar(jarInfo);
                                            jarInfos.add(jarInfo);
                                        }
                                    } catch (URISyntaxException ex) {
                                        InsightsReportLogger.ROOT_LOGGER.errorAnalyzingJar(file, ex);
                                    }
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    private String formatPath(Path path) {
                        return path.toString().replace(File.separatorChar, '/');
                    }

                    /**
                     * We don't want to analyze '.overlays' folders unless this
                     * is a module-root. Thus
                     * "/opt/wildfly/modules/system/layers/base/.overlays/layer-base-wildfly-28.0.1.CP"
                     * will be analyzed but not
                     * "/opt/wildfly/modules/system/layers/base/.overlays/layer-base-wildfly-26.1.3.CP"
                     * if we have module roots: - "/opt/wildfly/modules", -
                     * "/opt/wildfly/modules/system/layers/base/.overlays/layer-base-wildfly-28.0.1.CP",
                     * - "/opt/wildfly/modules/system/layers/base", -
                     * "/opt/wildfly/modules/system/add-ons/wildfly-deployment-transformer"
                     */
                    private boolean isOverlays(String path) {
                        return path.contains(".overlays" + File.separatorChar);
                    }

                    /**
                     * If a module-root is a sub-directory of another
                     * module-root we don't want to process it in the wrong
                     * order. For example: we don't want
                     * "modules/system/layers/base" to be processed as part of
                     * "modules" otherwise a module in "modules" might not take
                     * precedence over one in "modules/system/layers/base".
                     */
                    private boolean shouldSkipModuleRoot(Path currentModulePath, Set<Path> modulesRoots, Path dir) {
                        boolean isCurrent = currentModulePath.equals(dir);
                        boolean isModuleRoot = modulesRoots.contains(dir);
                        if (isModuleRoot) {
                            return !isCurrent;
                        }
                        return false;
                    }
                });
            } catch (IOException ex) {
                throw InsightsReportLogger.ROOT_LOGGER.failedToReadModules(ex);
            }
        }
        InsightsReportLogger.ROOT_LOGGER.endProcessingModules();
        Collections.sort((List<JarInfo>) jarInfos, Comparator.nullsFirst(
                Comparator.comparing(JarInfo::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.comparing(JarInfo::version, String.CASE_INSENSITIVE_ORDER))));
    }

    /**
     * We expect a list of module roots: - "/opt/wildfly/modules", -
     * "/opt/wildfly/modules/system/layers/base/.overlays/layer-base-wildfly-28.0.1.CP",
     * - "/opt/wildfly/modules/system/layers/base", -
     * "/opt/wildfly/modules/system/add-ons/wildfly-deployment-transformer"
     *
     * @return the list of repo roots.
     */
    private Set<Path> listRepoRoots() {
        try {
            Set<Path> moduleRoots = new LinkedHashSet<>();
            final ModelNode op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(OP_ADDR).setEmptyList();
            op.get(OP_ADDR).add("core-service", "module-loading");
            op.get(NAME).set("module-roots");
            op.get(INCLUDE_DEFAULTS).set(true);
            ModelNode response = this.modelControllerClient.execute(op);
            if (SUCCESS.equals(response.get(OUTCOME).asString())) {
                for (ModelNode node : response.get(RESULT).asList()) {
                    moduleRoots.add(new File(node.asString()).toPath().toAbsolutePath());
                }
            } else {
                throw InsightsReportLogger.ROOT_LOGGER.failedToReadModules(new IOException(response.get(FAILURE_DESCRIPTION).asString()));
            }
            return moduleRoots;
        } catch (IOException ex) {
            throw InsightsReportLogger.ROOT_LOGGER.failedToReadModules(ex);
        }
    }

    /**
     * Test if the target path is an archive.
     *
     * @param path path to the file.
     * @return true if the path points to a zip file - false otherwise.
     * @throws IOException
     */
    public static final boolean isArchive(Path path) throws IOException {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".zip") || fileName.endsWith(".jar") || fileName.endsWith(".war") || fileName.endsWith(".rar") || fileName.endsWith(".ear")) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    return true;
                } catch (ZipException e) {
                    return false;
                }
            }
        }
        return false;
    }
}

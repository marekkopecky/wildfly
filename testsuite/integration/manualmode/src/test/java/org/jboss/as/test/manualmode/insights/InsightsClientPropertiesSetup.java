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
package org.jboss.as.test.manualmode.insights;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class InsightsClientPropertiesSetup {

    public static final String MACHINE_ID_FILE_PATH_PROPERTY = "unsupported.machine.id.file.path";
    public static final Path JAVA_ARCHIVE_UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "insights-runtimes", "uploads");
    public static final String MOCK_TOKEN = "amRvZTp2UUpBOXdJb3BxM1VhbkpJ";

    private static final String LOG_FILE_NAME = "insights.log";
    private static final String CLIENT_MAX_RETRY = "rht.insights.java.http.client.retry.max.attempts";
    private static final String MOCK_URL = "http://localhost:1080";
    private static final String LOG_DIR_PROPERTY_PATH = "/path=jboss.server.log.dir";
    private static final String INSIGHTS_CONSOLE_DEBUG_PROPERTY = "test.insights.console.debug";
    private static final String FILE_HANDLER_NAME = "insights";
    private static final String INSIGHTS_LOGGER_CATEGORY_NAME = "org.jboss.eap.insights";

    private final String certFilePath = getClass().getResource("dummy.cert").getPath();
    private final String keyFilePath = getClass().getResource("dummy.key").getPath();

    protected void setup(ManagementClient managementClient) throws Exception {
        final ModelControllerClient controllerClient = managementClient.getControllerClient();
        addProperty(controllerClient, "rht.insights.java.upload.base.url", MOCK_URL);
        addProperty(controllerClient, CLIENT_MAX_RETRY, "1");
        setupCertAndKeyProperties(controllerClient);

        setupInsightsLogger(controllerClient);
    }

    protected void setupFileWriter(ManagementClient managementClient) throws Exception {
        Files.createDirectories(JAVA_ARCHIVE_UPLOAD_DIR);
        final ModelControllerClient controllerClient = managementClient.getControllerClient();
        addProperty(controllerClient, "rht.insights.java.archive.upload.dir", JAVA_ARCHIVE_UPLOAD_DIR.toString());
        addProperty(controllerClient, CLIENT_MAX_RETRY, "1");
        setupCertAndKeyProperties(controllerClient);
        setupInsightsLogger(controllerClient);
    }

    protected void setupTokenHttp(ManagementClient managementClient) throws Exception {
        final ModelControllerClient controllerClient = managementClient.getControllerClient();
        addProperty(controllerClient, CLIENT_MAX_RETRY, "1");
        addProperty(controllerClient, "rht.insights.java.upload.base.url", System.getProperty("test.insights.java.upload.base.url", MOCK_URL));
        addProperty(controllerClient, "rht.insights.java.auth.token", System.getProperty("test.insights.java.auth.token", MOCK_TOKEN));

        setupInsightsLogger(controllerClient);
    }

    protected void setupInsightsLogger(ModelControllerClient controllerClient) throws Exception {
        if (Boolean.getBoolean(INSIGHTS_CONSOLE_DEBUG_PROPERTY)) {
            ModelNode operation = Operations.createWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=logging/console-handler=CONSOLE").toModelNode(), "level", "DEBUG");
            ManagementOperations.executeOperation(controllerClient, operation);

            operation = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/subsystem=logging/logger=" + INSIGHTS_LOGGER_CATEGORY_NAME).toModelNode());
            operation.get("category").set(INSIGHTS_LOGGER_CATEGORY_NAME);
            operation.get("level").set("DEBUG");
            ManagementOperations.executeOperation(controllerClient, operation);
        }
        setupInsightsLogFile();
    }

    private void setupInsightsLogFile() {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine("/subsystem=logging/file-handler=" + FILE_HANDLER_NAME + ":add(append=false,autoflush=true,file={relative-to=jboss.server.log.dir, path="
                    + "insights.log" + "}, level=DEBUG, formatter=\"%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n\")", false);
            cli.sendLine("/subsystem=logging/root-logger=ROOT:add-handler(name=" + FILE_HANDLER_NAME + ")", true);
            cli.sendLine("/subsystem=logging/root-logger=ROOT:write-attribute(name=level, value=DEBUG");
            cli.sendLine("/subsystem=logging/periodic-rotating-file-handler=FILE:write-attribute(name=level,value=INFO)", true);
            // Avoid hitting issue https://issues.redhat.com/browse/JBEAP-26023 with excessive logging
            cli.sendLine("/subsystem=logging/logger=com.networknt.schema:add(level=ERROR)", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupCertAndKeyProperties(ModelControllerClient controllerClient) throws Exception {
        String machineIdFilePath = File.createTempFile("machine-id", null).getPath();
        addProperty(controllerClient, "rht.insights.java.key.file.path", keyFilePath);
        addProperty(controllerClient, "rht.insights.java.cert.file.path", certFilePath);
        addProperty(controllerClient, MACHINE_ID_FILE_PATH_PROPERTY, machineIdFilePath);
    }

    private void tearDownInsightsLogFile() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(" /subsystem=logging/root-logger=ROOT:remove-handler(name=" + FILE_HANDLER_NAME + ")", true);
            cli.sendLine("/subsystem=logging/file-handler=" + FILE_HANDLER_NAME + ":remove()", true);
            cli.sendLine("/subsystem=logging/root-logger=ROOT:write-attribute(name=level, value=INFO", true);
            cli.sendLine("/subsystem=logging/periodic-rotating-file-handler=FILE:undefine-attribute(name=level)", true);
            cli.sendLine("/subsystem=logging/logger=com.networknt.schema:remove()", true);
        }
    }

    protected void tearDown(ManagementClient managementClient) throws Exception {
        final ModelControllerClient controllerClient = managementClient.getControllerClient();

        ModelNode operation = Operations.createWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=logging/console-handler=CONSOLE").toModelNode(), "level", "INFO");
        ManagementOperations.executeOperation(controllerClient, operation);
        operation = Operations.createRemoveOperation(PathAddress.parseCLIStyleAddress("/subsystem=logging/logger=" + INSIGHTS_LOGGER_CATEGORY_NAME).toModelNode());
        try {
            ManagementOperations.executeOperation(controllerClient, operation);
        } catch (MgmtOperationException ex) {
            if (!ex.getMessage().contains("WFLYCTL0216")) {
                throw new RuntimeException(ex);
            }
        }

        tearDownInsightsLogFile();

        removeProperty(controllerClient, "rht.insights.java.upload.base.url");
        removeProperty(controllerClient, "rht.insights.java.key.file.path");
        removeProperty(controllerClient, "rht.insights.java.cert.file.path");
        removeProperty(controllerClient, "rht.insights.java.archive.upload.dir");
        removeProperty(controllerClient, "rht.insights.java.auth.token");
        removeProperty(controllerClient, MACHINE_ID_FILE_PATH_PROPERTY);

        if (Files.exists(JAVA_ARCHIVE_UPLOAD_DIR)) {
            FileUtils.deleteDirectory(JAVA_ARCHIVE_UPLOAD_DIR.toFile());
        }
    }

    protected void addProperty(ModelControllerClient client, String name, String value) throws Exception {
        removeProperty(client, name);
        if (value != null && !value.isEmpty()) {
            ModelNode operation = Operations.createAddOperation(PathAddress.pathAddress("system-property", name).toModelNode());
            operation.get("value").set(value);
            ManagementOperations.executeOperation(client, operation);
        }
    }

    protected void removeProperty(ModelControllerClient client, String name) throws Exception {
        ModelNode operation = Operations.createRemoveOperation(PathAddress.pathAddress("system-property", name).toModelNode());
        try {
            ManagementOperations.executeOperation(client, operation);
        } catch (MgmtOperationException ex) {
            // resource not found is OK here
            if (!ex.getMessage().contains("WFLYCTL0216")) {
                throw new RuntimeException(ex);
            }
        }
    }

    protected String readProperty(ModelControllerClient client, String name) throws Exception {
        ModelNode operation = Operations.createReadAttributeOperation(PathAddress.pathAddress("system-property", name).toModelNode(), "value");
        return getResultAsString(client.execute(operation));
    }

    protected Path getLogFilePath(ManagementClient managementClient) throws Exception {
        final ModelControllerClient controllerClient = managementClient.getControllerClient();

        ModelNode op = Operations.createReadAttributeOperation(PathAddress.parseCLIStyleAddress(LOG_DIR_PROPERTY_PATH).toModelNode(), "path");
        return Paths.get(getResultAsString(controllerClient.execute(op)), LOG_FILE_NAME);
    }

    private String getResultAsString(ModelNode result) {
        if (result.hasDefined(ModelDescriptionConstants.OUTCOME) && Operations.isSuccessfulOutcome(result)) {
            return Operations.readResult(result).asString();
        } else if (result.hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION)) {
            if (Operations.getFailureDescription(result).asString().contains("WFLYCTL0216")) {
                return null;
            }
            throw new RuntimeException(Operations.getFailureDescription(result).asString());
        } else {
            throw new RuntimeException("Operation not successful; outcome = " + result.get(ModelDescriptionConstants.OUTCOME));
        }
    }

    public String getCertFilePath() {
        return certFilePath;
    }
}

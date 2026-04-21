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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.awaitility.Awaitility;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.ClearType;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.model.StringBody;
import org.mockserver.verify.VerificationTimes;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;

import jakarta.inject.Inject;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jboss.as.test.manualmode.insights.InsightsLogsChecker.searchRegexInLog;
import static org.jboss.as.test.manualmode.insights.InsightsLogsChecker.searchTextInLog;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Configure 2-way SSL and test insights can connect to server or logs correct error codes.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class InsightsCertTestCase {

    private static final Logger log = Logger.getLogger(InsightsCertTestCase.class);

    protected static InsightsClientPropertiesSetup setupTask = new InsightsClientPropertiesSetup();

    private static final String LOG_MOCKSERVER = "mockserver.log";
    private static final int PORT = 8569;

    private static Path machineIdFilePath;
    private static ClientAndServer mockClientAndServer;
    private static HttpRequest connectRequest;
    private static Path serverLogFile;
    private static Path mockServerLogFile;
    private static String originalJbossArgsProperty;

    @Inject
    private static ServerController container;

    @BeforeClass
    public static void setupBeforeTestCase() throws Exception {
        // Insights is tested only RHEL
        Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
        // create fake machineId file which is normally used to check that RHEL Insights client is registered
        machineIdFilePath = createMachineIdFile();
        container.startInAdminMode();
        ManagementClient managementClient = container.getClient();
        setupTask.setupInsightsLogger(managementClient.getControllerClient());
        serverLogFile = setupTask.getLogFilePath(managementClient);
        setupMockServerLogging();
        container.stop();

        originalJbossArgsProperty = System.getProperty("jboss.args");
        log.debug("Original system property: " + originalJbossArgsProperty);

        startMockServer();
    }

    @Before
    public void setup() throws Exception {
        cleanLogFiles();
        mockClientAndServer.clear(request(), ClearType.LOG);
    }

    @Test
    public void testTwoWaySSL() throws Exception {
        configureInsightsClient(getFileFromResources("insights/mockserverclientkeycerts/client-cert.pem"), getFileFromResources("insights/mockserverclientkeycerts/client-key.pem"));

        container.start();
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        mockClientAndServer.verify(connectRequest, VerificationTimes.exactly(1))
                );

        String expectedLogMessage = "Red Hat Insights - Payload was accepted for processing";
        Assert.assertFalse("Log Message:\n\"" + expectedLogMessage + "\"\n is expected to be present in log but it's not.", searchTextInLog(expectedLogMessage, serverLogFile, Duration.ofSeconds(5)).isEmpty());

        RequestDefinition[] recordedRequests = mockClientAndServer
                .retrieveRecordedRequests(connectRequest);
        InsightsRequest request = InsightsHttpRequestParser.parse((HttpRequest) recordedRequests[0]);
        Assert.assertNotNull("Payload received by MockServer(simulating Insights server) did not receive any payload or could not be parsed.",
                request.getPayload().get("basic").asText());
    }

    @Test
    public void testNonReadableCert() throws Exception {
        configureInsightsClient(createNonReadableFile(), getFileFromResources("insights/mockserverclientkeycerts/client-key.pem"));
        container.start();

        String expectedLogMessage = ".*DEBUG.*insights.*java\\.nio\\.file\\.AccessDeniedException.*";
        Assert.assertFalse("Log Message:\n\"" + expectedLogMessage + "\"\n is expected to be present in log but it's not.", searchRegexInLog(expectedLogMessage, serverLogFile, Duration.ofSeconds(15)).isEmpty());

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @Test
    public void testNonReadableKey() throws Exception {
        configureInsightsClient(getFileFromResources("insights/mockserverclientkeycerts/client-cert.pem"), createNonReadableFile());
        container.start();

        String expectedLogMessage = ".*DEBUG.*insights.*java\\.nio\\.file\\.AccessDeniedException.*";
        Assert.assertFalse("Log Message:\n\"" + expectedLogMessage + "\"\n is expected to be present in log but it's not.", searchRegexInLog(expectedLogMessage, serverLogFile, Duration.ofSeconds(15)).isEmpty());

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());

    }

    @Test
    public void testBadKeyLocation() throws Exception {
        configureInsightsClient(getFileFromResources("insights/mockserverclientkeycerts/client-cert.pem"), "non-existent-file.txt");
        container.start();

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @Test
    public void testBadCertLocation() throws Exception {
        configureInsightsClient("non-existent-file.txt", getFileFromResources("insights/mockserverclientkeycerts/client-key.pem"));

        container.start();

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @Test
    public void testExpiredCert() throws Exception {
        configureInsightsClient(getFileFromResources("insights/mockserverclientkeycerts/client-expired-cert.pem"), getFileFromResources("insights/mockserverclientkeycerts/client-key.pem"));
        container.start();

        String expectedLogMessage1 = "java.security.cert.CertificateExpiredException";

        Assert.assertFalse("Log Message:\n\"" + expectedLogMessage1 + "\"\n is expected to be present in log but it's not. Printing log file: \n" + FileUtils.readFileToString(serverLogFile.toFile(), UTF_8),
                searchTextInLog(expectedLogMessage1, mockServerLogFile, Duration.ofSeconds(30)).isEmpty());

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @Test
    public void testNonExistentUploadUrl() throws Exception {
        configureInsightsClient(getFileFromResources("insights/mockserverclientkeycerts/client-cert.pem"), getFileFromResources("insights/mockserverclientkeycerts/client-key.pem"), "https://example.invalid:" + PORT);
        container.start();

        String expectedLogMessage = ".*DEBUG.*[java\\.net\\.UnknownHostException|java\\.nio\\.channels\\.UnresolvedAddressException].*";
        Assert.assertFalse("Log Message:\n\"" + expectedLogMessage + "\"\n is expected to be present in log but it's not.", searchRegexInLog(expectedLogMessage, serverLogFile, Duration.ofSeconds(15)).isEmpty());

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @Test
    public void testNoErrorIfNoClientIsReadyToSent() throws Exception {
        // Make sure that tested RHEL is not registered into Insights. Otherwise, this could send report to production Insights instance from test.
        Assume.assumeFalse("Certificate, key and machine-id are present in /etc/pki/consumer and /etc/insights-client directories. " +
                "Skipping test.", new File("/etc/pki/consumer/key.pem").exists() && new File("/etc/pki/consumer/cert.pem").exists() && new File("/etc/insights-client/machine-id").exists());

        configureInsightsClient(null, null, null);
        container.start();

        String unexpectedLogMessage = ".*(?:INFO|WARN|ERROR).*insights.*";
        List<String> logMessages = searchRegexInLog(unexpectedLogMessage, serverLogFile);
        Assert.assertTrue("Log Messages:\n\"" + logMessages + "\"\n are not expected to be present in log but it is there.", logMessages.isEmpty());
    }

    @After
    public void tearDown() {
        System.setProperty("jboss.args", originalJbossArgsProperty);
        container.stop();
    }

    @AfterClass
    public static void cleanEnvAfterTestCase() throws Exception {
        stopMockServer();
        container.startInAdminMode();
        setupTask.tearDown(container.getClient());
        container.stop();
        restoreTestLogging();
        if (originalJbossArgsProperty != null) {
            System.setProperty("jboss.args", originalJbossArgsProperty);
        }
        if (machineIdFilePath != null) {
            Files.deleteIfExists(machineIdFilePath);
        }

        // clean up insights.log
        if (serverLogFile != null) {
            Files.deleteIfExists(serverLogFile);
        }
    }

    private static void startMockServer() {
        ConfigurationProperties.logLevel("TRACE");
        ConfigurationProperties.tlsMutualAuthenticationRequired(true);
        ConfigurationProperties.privateKeyPath(getFileFromResources("insights/mockserverclientkeycerts/server-key.pem"));
        ConfigurationProperties.x509CertificatePath(getFileFromResources("insights/mockserverclientkeycerts/server-cert.pem"));
        ConfigurationProperties.certificateAuthorityCertificate(getFileFromResources("insights/mockserverclientkeycerts/ca.pem"));
        ConfigurationProperties.certificateAuthorityPrivateKey(getFileFromResources("insights/mockserverclientkeycerts/ca-key.pem"));
        ConfigurationProperties.tlsMutualAuthenticationCertificateChain(getFileFromResources("insights/mockserverclientkeycerts/ca.pem"));

        mockClientAndServer = startClientAndServer(PORT);
        mockClientAndServer.withSecure(true);

        connectRequest = request()
                .withMethod("POST")
                .withSecure(true)
                .withPath("/api/ingress/v1/upload");

        mockClientAndServer
                .when(connectRequest)
                .respond(
                        response()
                                .withBody("\"request_id\":\"103f2372045844c4a09a68fa67ed8ad5\",\"upload\":{\"account_number\":\"6430504\",\"org_id\":\"13510426\"}")
                                .withHeader("Content-Type", "application/json")
                                .withStatusCode(202)
                );

        HttpRequest updateRequest = HttpRequest.request()
                .withMethod("POST")
                .withSecure(true)
                .withPath("/api/ingress/v1/upload")
                .withHeaders(
                        Header.header("Content-Type", "multipart/form-data.*")
                )
                .withBody(StringBody.subString("_update.gz"));

        mockClientAndServer
                .when(updateRequest)
                .respond(
                        response()
                                .withBody("ACCEPTED")
                                .withStatusCode(202));

        mockClientAndServer
                .when(request()
                        .withMethod("POST")
                        .withPath("/api/ingress/v1/upload"))
                .respond(
                        response()
                                .withStatusCode(301)
                );
        log.debug("Mock server started.");
    }

    private static void setupMockServerLogging() throws IOException {
        mockServerLogFile = getAbsoluteLogFilePath(LOG_MOCKSERVER);
        mockServerLogFile.toFile().delete();
        mockServerLogFile.toFile().createNewFile();
        String mockServerFileHandlerName = "mockserver";
        // as MockServer will load logging.properties file from resources, we need to adjust it by "mockserver" FileHandler for this test
        String loggingConfiguration = Files.lines(Paths.get(getFileFromResources("logging.properties"))).map(line -> line.contains("logger.handlers=") ? line.concat("," + mockServerFileHandlerName) : line)
                .collect(Collectors.joining("\n")) +
                "# File handler configuration\n" +
                "handler." + mockServerFileHandlerName + "=org.jboss.logmanager.handlers.FileHandler\n" +
                "handler." + mockServerFileHandlerName + ".properties=autoFlush,fileName\n" +
                "handler." + mockServerFileHandlerName + ".autoFlush=true\n" +
                "handler." + mockServerFileHandlerName + ".fileName=" + mockServerLogFile.toAbsolutePath() + "\n" +
                "handler." + mockServerFileHandlerName + ".formatter=PATTERN\n";
        java.util.logging.LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(loggingConfiguration.getBytes(UTF_8)));
    }

    private static void restoreTestLogging() throws IOException {
        java.util.logging.LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(getFileFromResources("logging.properties").getBytes(UTF_8)));
    }

    private static void stopMockServer() {
        if (mockClientAndServer != null) {
            mockClientAndServer.stop(true);
            if (!mockClientAndServer.hasStopped(3, 2, TimeUnit.SECONDS)) {
                throw new RuntimeException("Mock server failed to stop.");
            }
        }
    }

    private static String createNonReadableFile() throws IOException {
        Path nonReadableCert = Files.createTempFile(null, null);
        Assert.assertTrue("It's not possible to set file non-readable: " + nonReadableCert, nonReadableCert.toFile().setReadable(false));
        return nonReadableCert.toString();
    }

    private static Path createMachineIdFile() throws IOException {
        return Files.createTempFile(null, null);
    }

    private void configureInsightsClient(String certFilePath, String keyFilePath) {
        configureInsightsClient(certFilePath, keyFilePath, "https://127.0.0.1:" + PORT);
    }

    private void configureInsightsClient(String certFilePath, String keyFilePath, String uploadUrl) {
        StringBuilder str = new StringBuilder(originalJbossArgsProperty);
        if (StringUtils.isNotEmpty(certFilePath)) {
            str.append(" -Drht.insights.java.cert.file.path=").append(certFilePath);
        }
        if (StringUtils.isNotEmpty(keyFilePath)) {
            str.append(" -Drht.insights.java.key.file.path=").append(keyFilePath);
        }

        if (StringUtils.isNotEmpty(uploadUrl)) {
            str.append(" -Drht.insights.java.upload.base.url=").append(uploadUrl);
        }

        // set machineId file path as no client will work without it
        str.append(" -Dunsupported.machine.id.file.path=").append(machineIdFilePath.toString());

        // set custom truststore with our custom CA certificate to server (it will be used by integrated Insights client for TLS authentication to MockServer)
        str.append(" -Djavax.net.ssl.trustStore=").append(getFileFromResources("insights/mockserverclientkeycerts/truststore.p12"));
        str.append(" -Djavax.net.ssl.trustStorePassword=changeit");

        System.setProperty("jboss.args", str.toString());
        log.debug("Set system property jboss.args=" + str);
    }

    private static Path getAbsoluteLogFilePath(final String filename) {
        return Paths.get(resolveRelativePath("jboss.server.log.dir"), filename);
    }

    private static String resolveRelativePath(final String relativePath) {
        final ModelNode address = PathAddress.pathAddress(
                PathElement.pathElement(ModelDescriptionConstants.PATH, relativePath)
        ).toModelNode();
        final ModelNode result;
        try {
            final ModelNode op = Operations.createReadAttributeOperation(address, ModelDescriptionConstants.PATH);
            result = container.getClient().getControllerClient().execute(op);
            if (Operations.isSuccessfulOutcome(result)) {
                return Operations.readResult(result).asString();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException(Operations.getFailureDescription(result).asString());
    }

    private void cleanLogFiles() throws Exception {
        serverLogFile.toFile().delete();
        serverLogFile.toFile().createNewFile();
    }

    private static String getFileFromResources(String path) {
        return InsightsCertTestCase.class
                .getClassLoader()
                .getResource(path)
                .getPath();
    }
}

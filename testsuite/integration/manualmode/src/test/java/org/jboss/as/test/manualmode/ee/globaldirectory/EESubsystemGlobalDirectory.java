/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.manualmode.ee.globaldirectory;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vratislav Marek (vmarek@redhat.com)
 * @author Tomas Terem (tterem@redhat.com)
 **/
public class EESubsystemGlobalDirectory extends AbstractCliTestBase {

    protected static final String SUBSYSTEM_EE = "ee";
    protected static final Path GLOBAL_DIRECTORY_PATH = Paths.get("global-directory");
    protected static final String GLOBAL_DIRECTORY_NAME = "global-directory";

    protected static final String TEMP_DIR_STRING = TestSuiteEnvironment.getTmpDir() + "/jars";
    protected static final File TEMP_DIR = new File(TEMP_DIR_STRING);
    protected static final int MAX_RECONNECTS_TRAY = 5;

    protected static final String CONTAINER = "default-jbossas";
    protected static final String DEPLOYMENT = "deployment";
    protected static final String DEPLOYMENT2 = "deployment2";
    protected static final String DEPLOYMENT3 = "deployment3";

    protected static final Logger LOGGER = Logger.getLogger(EESubsystemGlobalDirectory.class);

    protected ClientHolder clientHolder;
    protected Client client = ClientBuilder.newClient();

    @ArquillianResource
    protected static ContainerController containerController;

    @ArquillianResource
    protected static Deployer deployer;

    protected void createGlobalDirectoryFolder() {
        if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
            GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
        }
    }

    protected static void createLibrary(String name, Class library) {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, name + ".jar").addClasses(library);
        if (Files.notExists(Paths.get(TEMP_DIR.toString()))) {
            TEMP_DIR.mkdirs();
        }
        jar.as(ZipExporter.class).exportTo(new File(TEMP_DIR + "/" + name + ".jar"), true);
    }

    protected static void createTextFile(String name, List<String> content) throws IOException {
        if (Files.notExists(Paths.get(TEMP_DIR.toString()))) {
            TEMP_DIR.mkdirs();
        }
        Path file = Paths.get(TEMP_DIR_STRING + "/" + name + ".txt");
        Files.write(file, content, StandardCharsets.UTF_8);
    }

    protected static void createCorruptedLibrary(String name, List<String> content) throws IOException {
        if (Files.notExists(Paths.get(TEMP_DIR.toString()))) {
            TEMP_DIR.mkdirs();
        }
        Path file = Paths.get(TEMP_DIR_STRING + "/" + name + ".jar");
        Files.write(file, content, StandardCharsets.UTF_8);
    }

    protected void copyTextFileToGlobalDirectory(String name) throws IOException {
        if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
            GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
        }
        Path filePath = Paths.get(TEMP_DIR.toString() + "/" +  name + ".txt");
        Files.copy(filePath, Paths.get(GLOBAL_DIRECTORY_PATH.toString() + "/" + name + ".txt"), StandardCopyOption.REPLACE_EXISTING);
    }

    protected void copyLibraryToGlobalDirectory(String name) throws IOException {
        if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
            GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
        }
        Path jarPath = Paths.get(TEMP_DIR.toString() + "/" + name + ".jar");
        Files.copy(jarPath, Paths.get(GLOBAL_DIRECTORY_PATH.toString() + "/" + name + ".jar"), StandardCopyOption.REPLACE_EXISTING);
    }

    protected void copyLibraryToDirectory(String name, String path) throws IOException {
        if (Files.notExists(Paths.get(path))) {
            Paths.get(path).toFile().mkdirs();
        }
        Path jarPath = Paths.get(TEMP_DIR.toString() + "/" + name + ".jar");
        Files.copy(jarPath, Paths.get(path + "/" + name + ".jar"), StandardCopyOption.REPLACE_EXISTING);
    }

    protected void copyLibraryToGlobalDirectory(String name, Path path) throws IOException {
        if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
            GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
        }
        Files.copy(path, Paths.get(GLOBAL_DIRECTORY_PATH.toString() + "/" + name), StandardCopyOption.REPLACE_EXISTING);
    }

    protected void logContains(String expectedMessage) throws IOException {
        String JBOSS_HOME = System.getProperty("jboss.home", "jboss-as");
        String SERVER_MODE = Boolean.parseBoolean(System.getProperty("domain", "false")) ? "domain" : "standalone";
        Path serverLogPath = Paths.get(JBOSS_HOME, SERVER_MODE, "log", "server.log");
        String serverLog = String.join("\n", Files.readAllLines(serverLogPath));
        assertTrue("Log doesn't contain '" + expectedMessage + "'", serverLog.contains(expectedMessage));
    }

    @Before
    public void before() throws IOException {
        if (!containerController.isStarted(CONTAINER)) {
            containerController.start(CONTAINER);
        }
        createGlobalDirectoryFolder();
        connect();
    }

    @After
    public void after() {
        if (containerController.isStarted(CONTAINER)) {
            containerController.stop(CONTAINER);
        }
        client.close();
        disconnect();
    }

    protected void connect() {
        if (clientHolder == null) {
            clientHolder = ClientHolder.init();
        }
    }

    protected void disconnect() {
        clientHolder = null;
    }

    protected void restartServer() throws InterruptedException {
        containerController.stop(CONTAINER);
        containerController.start(CONTAINER);
        boolean connectionOnline = false;
        Exception lastEx = null;
        for (int iTry = 0; iTry < MAX_RECONNECTS_TRAY; iTry++) {
            try {
                checkConnectionLive();
                connectionOnline = true;
                break;
            } catch (Exception ex) {
                LOGGER.trace("Failed to reconnect cli! (try-" + iTry + ")", ex);
                Thread.sleep(10000);
                disconnect();
                connect();
                lastEx = ex;
            }
        }
        if (!connectionOnline) {
            throw new RuntimeException("Couldn't reconnect cli to the server!", lastEx);
        }
    }

    /**
     * It is used as easy request to check if cli is online
     *
     * @throws NullPointerException When the response from the server is null
     */
    private void checkConnectionLive() throws IOException, NullPointerException {
        // /subsystem=logging/log-file=*:read-resource
        final ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, "logging")
                .add("log-file", "*")
                .protect();
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);

        final ModelNode response = clientHolder.execute(operation);
        if (response == null) {
            throw new NullPointerException("Response from cli can't be null!");
        }
    }

    /**
     * @param expectedJars Expected Jars, the order matter, it will compare order of Jars in the log, null disable check
     */
    protected void checkDebugLogs(String[] expectedJars) throws IOException {
        String JBOSS_HOME = System.getProperty("jboss.home", "jboss-as");
        String SERVER_MODE = Boolean.parseBoolean(System.getProperty("domain", "false")) ? "domain" : "standalone";
        Path serverLogPath = Paths.get(JBOSS_HOME, SERVER_MODE, "log", "server.log");
        List<String> logs = Files.readAllLines(serverLogPath);
        int i = 0;
        for (String log : logs) {
            if (log.contains(expectedJars[i])) {
                i++;
            }
        }
        assertEquals("Expected jars were not found or were not in correct order", expectedJars.length, i);

    }

    /**
     * Register global directory
     * Verify the response for success
     *
     * @param name Name of new global directory
     */
    protected ModelNode register(String name) throws IOException {
        return register(name, GLOBAL_DIRECTORY_PATH.toString(), true);
    }

    /**
     * Register global directory
     *
     * @param name          Name of new global directory
     * @param path
     * @param expectSuccess If is true verify the response for success, If is false only return operation result
     */
    protected ModelNode register(String name, String path, boolean expectSuccess) throws IOException {
        // /subsystem=ee/global-directory=<<name>>:add(path=<<path>>)
        final ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, SUBSYSTEM_EE)
                .add(GLOBAL_DIRECTORY_NAME, name)
                .protect();
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);
        operation.get(PATH).set(path);

        ModelNode response = clientHolder.execute(operation);
        ModelNode outcome = response.get(OUTCOME);
        if (expectSuccess) {
            assertThat("Registration of global directory " + name + " failure!", outcome.asString(), is(SUCCESS));
        }
        return response;
    }

    /**
     * Remove global directory
     *
     * @param name Name of global directory for removing
     */
    protected ModelNode remove(String name) throws IOException {
        // /subsystem=ee/global-directory=<<name>>:remove
        final ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, SUBSYSTEM_EE)
                .add(GLOBAL_DIRECTORY_NAME, name)
                .protect();
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);

        ModelNode response = clientHolder.execute(operation);
        ModelNode outcome = response.get(OUTCOME);
        assertThat("Remove of global directory " + name + "  failure!", outcome.asString(), is(SUCCESS));
        return response;
    }

    /**
     * Verify if is global directory is registered and contains right path
     *
     * @param name Name of global directory for verify
     * @param path Expected set path for current global directory
     */
    protected ModelNode verifyProperlyRegistered(String name, String path) throws IOException {
        ModelNode response = readGlobalDirectory(name);
        ModelNode outcome = response.get(OUTCOME);
        assertThat("Read resource of global directory " + name + " failure!", outcome.asString(), is(SUCCESS));

        final ModelNode result = response.get(RESULT);
        assertThat("Global directory " + name + " have set wrong path!", result.get(PATH).asString(), is(path));
        return response;
    }

    /**
     * Read resource command for global directory
     *
     * @param name Name of global directory
     */
    private ModelNode readGlobalDirectory(String name) throws IOException {
        // /subsystem=ee/global-directory=<<name>>:read-resource
        final ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, SUBSYSTEM_EE)
                .add(GLOBAL_DIRECTORY_NAME, name)
                .protect();
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);

        return clientHolder.execute(operation);
    }

    /**
     * Verify that global directory doesn't exist
     *
     * @param name Name of global directory for verify
     */
    protected ModelNode verifyNonExist(String name) throws IOException {
        ModelNode response = readGlobalDirectory(name);
        ModelNode outcome = response.get(OUTCOME);
        assertThat("Global directory " + name + " still exist!", outcome.asString(), not(SUCCESS));
        return response;
    }

    protected static class ClientHolder {

        private final ManagementClient mgmtClient;

        private ClientHolder(ManagementClient mgmtClient) {
            this.mgmtClient = mgmtClient;
        }

        protected static ClientHolder init() {
            final ModelControllerClient clientHolder = TestSuiteEnvironment.getModelControllerClient();
            ManagementClient mgmtClient = new ManagementClient(clientHolder, TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), "http-remoting");
            return new ClientHolder(mgmtClient);
        }

        /**
         * Execute operation in wildfly
         *
         * @param operation Cli command represent in ModelNode interpretation
         */
        protected ModelNode execute(final ModelNode operation) throws
                IOException {
            return mgmtClient.getControllerClient().execute(operation);
        }

    }

}

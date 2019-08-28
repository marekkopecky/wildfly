/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.suites;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.cleanFile;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.safeClose;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Tomas Terem
 */
public class EeSubsystemGlobalDirectoryDomainTestCase extends AbstractCliTestBase {

   private static final Logger LOG = Logger.getLogger(EeSubsystemGlobalDirectoryDomainTestCase.class.getSimpleName());

   private static final String TEST = "test.war";
   private static final String REPLACEMENT = "test.war.v2";
   private static final ModelNode ROOT_ADDRESS = new ModelNode();
   private static final ModelNode ROOT_DEPLOYMENT_ADDRESS = new ModelNode();
   private static final ModelNode ROOT_REPLACEMENT_ADDRESS = new ModelNode();
   private static final ModelNode MAIN_SERVER_GROUP_ADDRESS = new ModelNode();
   private static final ModelNode MAIN_SERVER_GROUP_DEPLOYMENT_ADDRESS = new ModelNode();
   private static final ModelNode OTHER_SERVER_GROUP_ADDRESS = new ModelNode();
   private static final ModelNode OTHER_SERVER_GROUP_DEPLOYMENT_ADDRESS = new ModelNode();
   private static final ModelNode MAIN_RUNNING_SERVER_ADDRESS = new ModelNode();
   private static final ModelNode MAIN_RUNNING_SERVER_DEPLOYMENT_ADDRESS = new ModelNode();
   private static final ModelNode OTHER_RUNNING_SERVER_ADDRESS = new ModelNode();
   private static final ModelNode OTHER_RUNNING_SERVER_GROUP_ADDRESS = new ModelNode();

   protected static final String SUBSYSTEM_EE = "ee";
   protected static final Path GLOBAL_DIRECTORY_PATH = Paths.get(TestSuiteEnvironment.getTmpDir() + "/global-directory");
   protected static final String GLOBAL_DIRECTORY_NAME = "global-directory";

   protected static final String TEMP_DIR_STRING = TestSuiteEnvironment.getTmpDir() + "/jars";
   protected static final File TEMP_DIR = new File(TEMP_DIR_STRING);

   static {
      ROOT_ADDRESS.setEmptyList();
      ROOT_ADDRESS.protect();
      ROOT_DEPLOYMENT_ADDRESS.add(DEPLOYMENT, TEST);
      ROOT_DEPLOYMENT_ADDRESS.protect();
      ROOT_REPLACEMENT_ADDRESS.add(DEPLOYMENT, REPLACEMENT);
      ROOT_REPLACEMENT_ADDRESS.protect();
      MAIN_SERVER_GROUP_ADDRESS.add(SERVER_GROUP, "main-server-group");
      MAIN_SERVER_GROUP_ADDRESS.protect();
      MAIN_SERVER_GROUP_DEPLOYMENT_ADDRESS.add(SERVER_GROUP, "main-server-group");
      MAIN_SERVER_GROUP_DEPLOYMENT_ADDRESS.add(DEPLOYMENT, TEST);
      MAIN_SERVER_GROUP_DEPLOYMENT_ADDRESS.protect();
      OTHER_SERVER_GROUP_ADDRESS.add(SERVER_GROUP, "other-server-group");
      OTHER_SERVER_GROUP_ADDRESS.protect();
      OTHER_SERVER_GROUP_DEPLOYMENT_ADDRESS.add(SERVER_GROUP, "other-server-group");
      OTHER_SERVER_GROUP_DEPLOYMENT_ADDRESS.add(DEPLOYMENT, TEST);
      OTHER_SERVER_GROUP_DEPLOYMENT_ADDRESS.protect();
      MAIN_RUNNING_SERVER_ADDRESS.add(HOST, "master");
      MAIN_RUNNING_SERVER_ADDRESS.add(SERVER, "main-one");
      MAIN_RUNNING_SERVER_ADDRESS.protect();
      MAIN_RUNNING_SERVER_DEPLOYMENT_ADDRESS.add(HOST, "master");
      MAIN_RUNNING_SERVER_DEPLOYMENT_ADDRESS.add(SERVER, "main-one");
      MAIN_RUNNING_SERVER_DEPLOYMENT_ADDRESS.add(DEPLOYMENT, TEST);
      MAIN_RUNNING_SERVER_DEPLOYMENT_ADDRESS.protect();
      OTHER_RUNNING_SERVER_ADDRESS.add(HOST, "slave");
      OTHER_RUNNING_SERVER_ADDRESS.add(SERVER, "other-two");
      OTHER_RUNNING_SERVER_ADDRESS.protect();
      OTHER_RUNNING_SERVER_GROUP_ADDRESS.add(HOST, "slave");
      OTHER_RUNNING_SERVER_GROUP_ADDRESS.add(SERVER, "other-two");
      OTHER_RUNNING_SERVER_GROUP_ADDRESS.add(DEPLOYMENT, TEST);
      OTHER_RUNNING_SERVER_GROUP_ADDRESS.protect();

   }

   private static DomainTestSupport testSupport;
   private static WebArchive webArchive;
   private static File tmpDir;

   @BeforeClass
   public static void setupDomain() throws Exception {
      if (Files.notExists(Paths.get(TEMP_DIR.toString()))) {
         TEMP_DIR.mkdirs();
      }

      if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
         GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
      }

      // Create our deployments
      webArchive = ShrinkWrap.create(WebArchive.class, TEST).addClasses(GlobalDirectoryDeployment.class);

      JavaArchive library = ShrinkWrap.create(JavaArchive.class, "library.jar").addClasses(GlobalDirectoryLibrary.class);
      library.as(ZipExporter.class).exportTo(new File(TEMP_DIR + "/library.jar"), true);

      JavaArchive libraryImpl = ShrinkWrap.create(JavaArchive.class, "libraryImpl.jar").addClasses(GlobalDirectoryLibraryImpl.class);
      libraryImpl.as(ZipExporter.class).exportTo(new File(TEMP_DIR + "/libraryImpl.jar"), true);

      // Make versions on the filesystem for URL-based deploy and for unmanaged content testing
      tmpDir = new File("target/deployments/" + EeSubsystemGlobalDirectoryDomainTestCase.class.getSimpleName());
      new File(tmpDir, "archives").mkdirs();
      new File(tmpDir, "exploded").mkdirs();
      webArchive.as(ZipExporter.class).exportTo(new File(tmpDir, "archives/" + TEST), true);
      webArchive.as(ExplodedExporter.class).exportExploded(new File(tmpDir, "exploded"));

      // Launch the domain
      testSupport = DomainTestSuite.createSupport(EeSubsystemGlobalDirectoryDomainTestCase.class.getSimpleName());

   }

   @AfterClass
   public static void tearDownDomain() throws Exception {
      try {
         testSupport = null;
         DomainTestSuite.stopSupport();
      } finally {
         cleanFile(tmpDir);
      }
   }

   /**
    * Validate that there are no deployments; try and clean if there are.
    *
    * @throws Exception
    */
   @Before
   @After
   public void confirmNoDeployments() throws Exception {
      List<ModelNode> deploymentList = getDeploymentList(ROOT_ADDRESS);
      if (deploymentList.size() > 0) {
         cleanDeployments();
      }
      deploymentList = getDeploymentList(new ModelNode());
      assertEquals("Deployments are removed from the domain", 0, deploymentList.size());

      try {
         performHttpCall(DomainTestSupport.masterAddress, 8080);
         fail(TEST + " is available on main-one");
      } catch (IOException good) {
         // good
      }
      try {
         performHttpCall(DomainTestSupport.slaveAddress, 8630);
         fail(TEST + " is available on other-three");
      } catch (IOException good) {
         // good
      }
   }

   /**
    * Remove all deployments from the model.
    *
    * @throws IOException
    */
   private void cleanDeployments() throws IOException {
      List<ModelNode> deploymentList = getDeploymentList(MAIN_SERVER_GROUP_ADDRESS);
      for (ModelNode deployment : deploymentList) {
         removeDeployment(deployment.asString(), MAIN_SERVER_GROUP_ADDRESS);
      }
      deploymentList = getDeploymentList(OTHER_SERVER_GROUP_ADDRESS);
      for (ModelNode deployment : deploymentList) {
         removeDeployment(deployment.asString(), OTHER_SERVER_GROUP_ADDRESS);
      }
      deploymentList = getDeploymentList(ROOT_ADDRESS);
      for (ModelNode deployment : deploymentList) {
         removeDeployment(deployment.asString(), ROOT_ADDRESS);
      }
   }


   @Test
   public void testDeploymentViaUrl() throws Exception {
      copyLibraryToGlobalDirectory("library");
      copyLibraryToGlobalDirectory("libraryImpl");

      initCLI();
      // register(GLOBAL_DIRECTORY_NAME);
      cli.sendLine("/profile=default/subsystem=ee/global-directory=" + GLOBAL_DIRECTORY_NAME + ":add(path=" + GLOBAL_DIRECTORY_PATH.toString() + ")");
//      verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
//      cli.sendLine("shutdown --host=master --restart=true", true);

      testSupport.getDomainMasterLifecycleUtil().getDomainClient().restartServer("master", "main-one", 1, TimeUnit.MINUTES);
//      ModelNode restart = Util.createEmptyOperation("shutdown --host=master --restart=true", PathAddress.pathAddress("deployment", DEPENDEE.getName()));
//      ManagementOperations.executeOperation(testSupport.getDomainMasterLifecycleUtil().getDomainClient(), restart);
      LOG.info("------------");
      LOG.info("Sleeping");
      LOG.info("------------");
      Thread.sleep(5000);

      String url = new File(tmpDir, "archives/" + TEST).toURI().toURL().toString();
      ModelNode content = new ModelNode();
      content.get("url").set(url);
      ModelNode composite = createDeploymentOperation(content, MAIN_SERVER_GROUP_DEPLOYMENT_ADDRESS, OTHER_SERVER_GROUP_DEPLOYMENT_ADDRESS);
      executeOnMaster(composite);

      performHttpCall(DomainTestSupport.masterAddress, 8080, "global-directory/library");
      performHttpCall(DomainTestSupport.slaveAddress, 8630, "global-directory/library");
   }

   protected void copyLibraryToGlobalDirectory(String name) throws IOException {
      if (Files.notExists(GLOBAL_DIRECTORY_PATH)) {
         GLOBAL_DIRECTORY_PATH.toFile().mkdirs();
      }
      Path jarPath = Paths.get(TEMP_DIR.toString() + "/" + name + ".jar");
      Files.copy(jarPath, Paths.get(GLOBAL_DIRECTORY_PATH.toString() + "/" + name + ".jar"), StandardCopyOption.REPLACE_EXISTING);
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

      ModelNode response = testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(operation);
      ModelNode outcome = response.get(OUTCOME);
      if (expectSuccess) {
         assertThat("Registration of global directory " + name + " failure!", outcome.asString(), is(SUCCESS));
      }
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

      return testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(operation);
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


   private static ModelNode executeOnMaster(ModelNode op) throws IOException {
      return validateResponse(testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(op));
   }

   private static ModelNode createDeploymentOperation(ModelNode content, ModelNode... serverGroupAddressses) {
      ModelNode composite = getEmptyOperation(COMPOSITE, ROOT_ADDRESS);
      ModelNode steps = composite.get(STEPS);
      ModelNode step1 = steps.add();
      step1.set(getEmptyOperation(ADD, ROOT_DEPLOYMENT_ADDRESS));
      step1.get(CONTENT).add(content);
      for (ModelNode serverGroup : serverGroupAddressses) {
         ModelNode sg = steps.add();
         sg.set(getEmptyOperation(ADD, serverGroup));
         sg.get(ENABLED).set(true);
      }

      return composite;
   }

   private static List<ModelNode> getDeploymentList(ModelNode address) throws IOException {
      ModelNode op = getEmptyOperation("read-children-names", address);
      op.get("child-type").set("deployment");

      ModelNode response = testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(op);
      ModelNode result = validateResponse(response);
      return result.isDefined() ? result.asList() : Collections.<ModelNode>emptyList();
   }

   private static void removeDeployment(String deploymentName, ModelNode address) throws IOException {
      ModelNode deplAddr = new ModelNode();
      deplAddr.set(address);
      deplAddr.add("deployment", deploymentName);
      ModelNode op = getEmptyOperation(REMOVE, deplAddr);
      ModelNode response = testSupport.getDomainMasterLifecycleUtil().getDomainClient().execute(op);
      validateResponse(response);
   }

   private static ModelNode getEmptyOperation(String operationName, ModelNode address) {
      ModelNode op = new ModelNode();
      op.get(OP).set(operationName);
      if (address != null) {
         op.get(OP_ADDR).set(address);
      }
      else {
         // Just establish the standard structure; caller can fill in address later
         op.get(OP_ADDR);
      }
      return op;
   }

   private static void performHttpCall(String host, int port) throws IOException {
      performHttpCall(host, port, "test");
   }

   private static void performHttpCall(String host, int port, String context) throws IOException {
      URLConnection conn = null;
      InputStream in = null;
      StringWriter writer = new StringWriter();
      try {
         URL url = new URL("http://" + TestSuiteEnvironment.formatPossibleIpv6Address(host) + ":" + port + "/" + context);
         conn = url.openConnection();
         conn.setDoInput(true);
         in = new BufferedInputStream(conn.getInputStream());
         int i = in.read();
         while (i != -1) {
            writer.write((char) i);
            i = in.read();
         }
         assertTrue(writer.toString().indexOf("Hello World") > -1);
      } finally {
         safeClose(in);
         safeClose(writer);
      }
   }

}

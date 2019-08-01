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

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.manualmode.ee.globaldirectory.deployments.GlobalDirectoryDeployment;
import org.jboss.as.test.manualmode.ee.globaldirectory.deployments.GlobalDirectoryDeployment2;
import org.jboss.as.test.manualmode.ee.globaldirectory.deployments.GlobalDirectoryDeployment3;
import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibrary;
import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibraryImpl;
import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibraryImpl2;
import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibraryImpl3;
import org.jboss.as.test.manualmode.ee.globaldirectory.util.MavenUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.fail;

/**
 * @author Vratislav Marek (vmarek@redhat.com)
 * @author Tomas Terem (tterem@redhat.com)
 **/
@RunWith(Arquillian.class)
@RunAsClient
public class EeSubsystemGlobalDirectoryTestCase extends EESubsystemGlobalDirectory {

    @Before
    public void setup() throws Exception {
        initCLI(true);
    }

    @After
    public void clean() throws IOException {
        remove(GLOBAL_DIRECTORY_NAME);
        verifyNonExist(GLOBAL_DIRECTORY_NAME);
        FileUtils.deleteDirectory(GLOBAL_DIRECTORY_PATH.toFile());
        FileUtils.deleteDirectory(TEMP_DIR);
    }

    @Deployment(name = DEPLOYMENT, managed = false, testable = false)
    @TargetsContainer(CONTAINER)
    public static WebArchive createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT + ".war");
        war.addClass(GlobalDirectoryDeployment.class);
        war.addAsWebInfResource(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?><web-app><servlet-mapping>\n" +
                "        <servlet-name>javax.ws.rs.core.Application</servlet-name>\n" +
                "        <url-pattern>/*</url-pattern>\n" +
                "    </servlet-mapping></web-app>"),"web.xml");
        return war;
    }

    @Deployment(name = DEPLOYMENT2, managed = false, testable = false)
    @TargetsContainer(CONTAINER)
    public static WebArchive createDeployment2() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT2 + ".war");
        war.addClass(GlobalDirectoryDeployment2.class);
        war.addAsWebInfResource(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?><web-app><servlet-mapping>\n" +
              "        <servlet-name>javax.ws.rs.core.Application</servlet-name>\n" +
              "        <url-pattern>/*</url-pattern>\n" +
              "    </servlet-mapping></web-app>"),"web.xml");
        return war;
    }

    @Deployment(name = DEPLOYMENT3, managed = false, testable = false)
    @TargetsContainer(CONTAINER)
    public static WebArchive createDeployment3() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT3 + ".war");
        war.addClass(GlobalDirectoryDeployment3.class);
        war.addAsWebInfResource(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?><web-app><servlet-mapping>\n" +
              "        <servlet-name>javax.ws.rs.core.Application</servlet-name>\n" +
              "        <url-pattern>/*</url-pattern>\n" +
              "    </servlet-mapping></web-app>"),"web.xml");
        return war;
    }

    @Test // works
    public void testModifyDependencySharedLibs() throws IOException, InterruptedException {
        ManagementClient managementClient = new ManagementClient(TestSuiteEnvironment.getModelControllerClient(),
              TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), "remote+http");
        URL url = new URL(managementClient.getWebUri().toURL(), '/' + DEPLOYMENT + "/");

        createLibrary(GlobalDirectoryLibrary.class.getSimpleName(), GlobalDirectoryLibrary.class);
        createLibrary(GlobalDirectoryLibraryImpl.class.getSimpleName(), GlobalDirectoryLibraryImpl.class);

        copyLibraryToGlobalDirectory(GlobalDirectoryLibrary.class.getSimpleName());
        copyLibraryToGlobalDirectory(GlobalDirectoryLibraryImpl.class.getSimpleName());

        register(GLOBAL_DIRECTORY_NAME);
        verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
        restartServer();

        deployer.deploy(DEPLOYMENT);

        Response response = client.target(url + "global-directory/library").request().get();
        String result = response.readEntity(String.class);
        Assert.assertEquals("HELLO WORLD", result);

        deployer.undeploy(DEPLOYMENT);
    }

    @Test // improve to check if it shows in cli as well
    public void testJBossModulesFoundCorruptedJarInSharedLibs() throws IOException, InterruptedException {
        createCorruptedLibrary("corrupted", Arrays.asList("hello world"));
        copyLibraryToGlobalDirectory("corrupted");
        register(GLOBAL_DIRECTORY_NAME);
        verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
        restartServer();

        try {
            deployer.deploy(DEPLOYMENT);
            fail("Exception should have been thrown.");
        } catch (Exception e) {
            Assert.assertEquals(DeploymentException.class, e.getClass());
        }
        logContains("WFLYSRV0276: There is an error in opening zip file " + GLOBAL_DIRECTORY_PATH.toFile().toPath().toAbsolutePath().toString() + "/corrupted.jar");
    }

    @Test // doesn't work as expected
    public void testVerifyLoadingOrderSharedLibs() throws IOException, InterruptedException {
        ManagementClient managementClient = new ManagementClient(TestSuiteEnvironment.getModelControllerClient(),
              TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), "remote+http");
        URL url = new URL(managementClient.getWebUri().toURL(), '/' + DEPLOYMENT3 + "/");

        MavenUtil mavenUtil;
        mavenUtil = MavenUtil.create(true);

        // File library1;
        File library2;
        File dependency;
        try {
            // library1 = mavenUtil.createMavenGavFile("org.eclipse.microprofile.config:microprofile-config-api:1.3");
            library2 = mavenUtil.createMavenGavFile("org.eclipse.microprofile.config:microprofile-config-api:1.2.1");
            dependency = mavenUtil.createMavenGavFile("org.osgi:org.osgi.annotation.versioning:1.0.0");
        } catch (Exception e) {
            throw new RuntimeException("Unable to get artifacts from maven via Aether library", e);
        }

        // copyLibraryToGlobalDirectory(library1.getName(), library1.toPath().toAbsolutePath());
        copyLibraryToGlobalDirectory(library2.getName(), library2.toPath().toAbsolutePath());
        copyLibraryToGlobalDirectory(dependency.getName(), dependency.toPath().toAbsolutePath());

        // FileUtils.deleteDirectory(library1);
        Files.delete(library2.toPath());
        Files.delete(dependency.toPath());

        createLibrary(GlobalDirectoryLibrary.class.getSimpleName(), GlobalDirectoryLibrary.class);
        createLibrary(GlobalDirectoryLibraryImpl.class.getSimpleName(), GlobalDirectoryLibraryImpl.class);

        copyLibraryToGlobalDirectory(GlobalDirectoryLibrary.class.getSimpleName());
        copyLibraryToGlobalDirectory(GlobalDirectoryLibraryImpl.class.getSimpleName());


        register(GLOBAL_DIRECTORY_NAME);
        verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
        restartServer();

        deployer.deploy(DEPLOYMENT3);

        Response response = client.target(url + "global-directory/library3").request().get();
        String result = response.readEntity(String.class);
        Assert.assertEquals("100", result);

        deployer.undeploy(DEPLOYMENT3);
    }

    @Test // test is ok, implementation is going to be fixed
    public void testVerifyLoadingOrderSharedLibs2() throws Exception {
        createLibrary(GlobalDirectoryLibrary.class.getSimpleName(), GlobalDirectoryLibrary.class);
        createLibrary(GlobalDirectoryLibraryImpl.class.getSimpleName(), GlobalDirectoryLibraryImpl.class);
        createLibrary(GlobalDirectoryLibraryImpl2.class.getSimpleName(), GlobalDirectoryLibraryImpl2.class);
        createLibrary(GlobalDirectoryLibraryImpl3.class.getSimpleName(), GlobalDirectoryLibraryImpl3.class);

        GLOBAL_DIRECTORY_PATH.toFile().mkdirs();

        File subDirectoryA = new File(GLOBAL_DIRECTORY_PATH.toFile(), "a");
        File subDirectoryAB = new File(GLOBAL_DIRECTORY_PATH.toFile(), "ab");
        File subDirectoryC = new File(GLOBAL_DIRECTORY_PATH.toFile(), "C");
        File subDirectoryC_D = new File(subDirectoryC, "D");
        File subDirectoryC_E = new File(subDirectoryC, "E");
        File subDirectoryC_D_F = new File(subDirectoryC_D, "F");

        copyLibraryToGlobalDirectory(GlobalDirectoryLibrary.class.getSimpleName());
        copyLibraryToDirectory(GlobalDirectoryLibrary.class.getSimpleName(), subDirectoryA.toString());
        copyLibraryToDirectory(GlobalDirectoryLibraryImpl.class.getSimpleName(), subDirectoryA.toString());
        copyLibraryToDirectory(GlobalDirectoryLibraryImpl.class.getSimpleName(), subDirectoryAB.toString());
        copyLibraryToDirectory(GlobalDirectoryLibraryImpl2.class.getSimpleName(), subDirectoryC_D_F.toString());
        copyLibraryToDirectory(GlobalDirectoryLibraryImpl2.class.getSimpleName(), subDirectoryC_E.toString());
        copyLibraryToDirectory(GlobalDirectoryLibraryImpl3.class.getSimpleName(), subDirectoryC_E.toString());

        register(GLOBAL_DIRECTORY_NAME);
        verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
        restartServer();

        initCLI(true);
        cli.sendLine("/subsystem=logging/logger=org.jboss.as.server.moduleservice:add(level=DEBUG)");

        deployer.deploy(DEPLOYMENT);

        checkDebugLogs(new String[]{
              "Added " + GLOBAL_DIRECTORY_PATH.toAbsolutePath().toFile().toString() + " directory as resource root",
              "Added " + GLOBAL_DIRECTORY_PATH.toAbsolutePath().toFile().toString() + "/GlobalDirectoryLibrary.jar jar file",
              "Added " + subDirectoryA.toString() + "/GlobalDirectoryLibraryImpl.jar jar file",
              "Added " + subDirectoryC_D_F.toString() + "/GlobalDirectoryLibraryImpl2.jar jar file",
              "Added " + subDirectoryC_E.toString() + "/GlobalDirectoryLibraryImpl3.jar jar file"
        });

        deployer.undeploy(DEPLOYMENT);
    }

    @Test // works
    public void testReadPropertyFilesSharedLibs() throws IOException, InterruptedException {
        ManagementClient managementClient = new ManagementClient(TestSuiteEnvironment.getModelControllerClient(),
              TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), "remote+http");
        URL url = new URL(managementClient.getWebUri().toURL(), '/' + DEPLOYMENT2 + "/");

        createLibrary(GlobalDirectoryLibrary.class.getSimpleName(), GlobalDirectoryLibrary.class);
        createLibrary(GlobalDirectoryLibraryImpl2.class.getSimpleName(), GlobalDirectoryLibraryImpl2.class);

        String propertyFileName = "properties";
        String propertyFileString = "PROPERTY FILE";

        createTextFile(propertyFileName, Arrays.asList(propertyFileString ));

        copyLibraryToGlobalDirectory(GlobalDirectoryLibrary.class.getSimpleName());
        copyLibraryToGlobalDirectory(GlobalDirectoryLibraryImpl2.class.getSimpleName());
        copyTextFileToGlobalDirectory(propertyFileName);

        register(GLOBAL_DIRECTORY_NAME);
        verifyProperlyRegistered(GLOBAL_DIRECTORY_NAME, GLOBAL_DIRECTORY_PATH.toString());
        restartServer();

        deployer.deploy(DEPLOYMENT2);

        Response response = client.target(url + "global-directory/library2").request().get();
        String result = response.readEntity(String.class);
        Assert.assertEquals(propertyFileString, result);

        deployer.undeploy(DEPLOYMENT2);
    }
}

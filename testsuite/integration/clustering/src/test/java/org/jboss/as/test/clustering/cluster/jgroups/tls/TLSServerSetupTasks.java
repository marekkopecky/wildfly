/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.tls;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jgroups.protocols.TCP;

/**
 * Utility interface containing {@link org.jboss.as.arquillian.api.ServerSetupTask}s for setting up TLS/SSL for JGroups channels.
 *
 * @author Radoslav Husar
 */
public interface TLSServerSetupTasks {

    /**
     * Server setup task that uses Elytron to create physical key and trust store files.
     */
    class PhysicalKeyStoresServerSetupTask extends ManagementServerSetupTask {
        /**
         * Define keystore and truststore for each container from containers parameter
         */
        @SuppressWarnings("deprecation")
        public PhysicalKeyStoresServerSetupTask(Set<String> containers) {
            super(defineManagementCommands(containers));
            this.containers = containers;
        }

        /**
         * Set of containers, each has its own keystore and truststore
         */
        Set<String> containers;

        /**
         * Define management commands for creating key stores and trust stores. All keys (for all nodes) are created on first node.
         * So first node could have access to public keys of other nodes during configuration of first node (before other nodes are started).
         *
         * Keys are generated in target directory in TS.
         *
         * If there is one public&private keys needed only (one shared key), files doesn't have node-name suffix.
         */
        private static ContainerSetConfiguration defineManagementCommands(Set<String> containers) {
            ContainerSetConfigurationBuilder builder = createContainerSetConfigurationBuilder();
            if (containers.size() > 0) {
                ScriptBuilder setupScriptBuilder = createScriptBuilder();
                ScriptBuilder tearDownScriptBuilder = createScriptBuilder();
                for (String container : containers) {
                    // n.b. we cannot use a batch here since we need to run the 'generate-key-pair' operation on already running store
                    // WFLYELY00007: The required service 'service org.wildfly.security.key-store.jgroupsKS' is not UP, it is currently 'STARTING'."}}

                    // Setup and populate shared KS
                    setupScriptBuilder
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:add(path=../../../server.keystore%s.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", container, containers.size() == 1 ? "" : container)
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:generate-key-pair(alias=localhost, algorithm=RSA, key-size=2048, validity=365, credential-reference={clear-text=secret}, distinguished-name=\"CN=localhost\")", container)
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:store", container)

                            // Export pem certificate
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:export-certificate(alias=localhost, path=server.keystore.pem, relative-to=jboss.server.config.dir, pem=true)", container)

                            // Setup and populate shared TS
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:add(path=../../../server.truststore%s.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", container, containers.size() == 1 ? "" : container)
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:import-certificate(alias=client, path=server.keystore.pem, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, trust-cacerts=true, validate=false)", container)
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:store", container);
                    // n.b. clearing these stores to avoid the following issue when reusing these setup tasks or rerunning the test sans clean
                    // WFLYELY01036: Alias 'localhost' already exists in KeyStore [ \"WFLYELY01036: Alias 'localhost' already exists in KeyStore\" ]"

                    // Remove certificates from the temporary physical file store
                    tearDownScriptBuilder
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:remove-alias(alias=localhost)", container)
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:store", container)
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:remove-alias(alias=client)", container)
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:store", container)

                            // Cleanup temporary store model resources
                            .add("/subsystem=elytron/key-store=jgroupsTS%s:remove", container)
                            .add("/subsystem=elytron/key-store=jgroupsKS%s:remove", container);
                }
                builder.addContainer(containers.iterator().next(), createContainerConfigurationBuilder()
                        .setupScript(setupScriptBuilder.build())
                        .tearDownScript(tearDownScriptBuilder.build())
                        .build());
            }
            return builder.build();
        }

        /**
         * Clean store files based on definition from defineManagementCommands method. After that, remove files from target folder.
         */
        @Override
        public void tearDown(ManagementClient client, String containerId) throws Exception {
            super.tearDown(client, containerId);
            // remove files
            for (String container : containers) {
                Files.deleteIfExists(Paths.get("target", String.format("server.keystore%s.pkcs12", containers.size() == 1 ? "" : container)));
                Files.deleteIfExists(Paths.get("target", String.format("server.truststore%s.pkcs12", containers.size() == 1 ? "" : container)));
            }
        }
    }

    /**
     * Server setup task that uses Elytron to create a shared physical key and trust store files containing a generated pre-shared key.
     */
    class SharedPhysicalKeyStoresServerSetupTask extends PhysicalKeyStoresServerSetupTask {
        public SharedPhysicalKeyStoresServerSetupTask() {
            super(Set.of(NODE_1));
        }
    }

    /**
     * Server setup task that uses Elytron to create a physical key and trust store for each server.
     *
     * This class assumes that two nodes are started.
     */
    class PhysicalKeyStoresServerSetupTask_NODE_1_2 extends PhysicalKeyStoresServerSetupTask {
        public PhysicalKeyStoresServerSetupTask_NODE_1_2() {
            super(NODE_1_2);
        }
    }

    /**
     * Secure JGroups transports by adding private and public keys
     */
    class SecureJGroupsTransportServerSetupTask extends ManagementServerSetupTask {
        /**
         * Basis configuration of TLS, see more details about parameters bellow.
         *
         * @param nodes List of nodes those needs to be secured
         * @param tp Transport name. Atm TCP transport is supported only.
         * @param sharedKS True - one shared key is generated only. False - each node has its own private key, each node doesn't trust any other node.
         */
        @SuppressWarnings("deprecation")
        public SecureJGroupsTransportServerSetupTask(Set<String> nodes, String tp, boolean sharedKS) {
            super(defineManagementCommands(nodes, tp, sharedKS, 0));
        }

        /**
         * Complex configuration of TLS, see more details about parameters bellow.
         *
         * @param nodes List of nodes those needs to be secured
         * @param tp Transport name. Atm TCP transport is supported only.
         * @param sharedKS True - one shared key is generated only. False - each node has its own private key
         * @param trustOthers 0 - any node doesn't trust any other node. 1 - 1 node trusts second node. 2 - both nodes trust each other.
         */
        public SecureJGroupsTransportServerSetupTask(Set<String> nodes, String tp, boolean sharedKS, int trustOthers) {
            super(defineManagementCommands(nodes, tp, sharedKS, trustOthers));
        }

        /**
         * Main configuration method for both constructors of this class
         */
        private static ContainerSetConfiguration defineManagementCommands(Set<String> containers, String tp, boolean sharedKS,  int trustOthers) {
            if (trustOthers != 0) {
                if (containers.size() != 2) {
                    throw new NotImplementedException("Current implementation allows to share other node's keys only with two nodes");
                }
            }
            ContainerSetConfigurationBuilder builder = createContainerSetConfigurationBuilder();
            boolean oneOppositeNodeTrusted = false;
            for (String container : containers) {
                String trustStoreFor = container;
                if (trustOthers == 2 || (trustOthers == 1 && !oneOppositeNodeTrusted)) {
                    trustStoreFor = findOpositeNode(containers, container);
                    oneOppositeNodeTrusted = true;
                }
                builder.addContainer(container, createContainerConfigurationBuilder()
                        .setupScript(createScriptBuilder()
                                .startBatch()
                                .add("/subsystem=elytron/key-store=jgroupsKS:add(path=../../../server.keystore%s.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)",
                                        sharedKS ? "" : container)
                                .add("/subsystem=elytron/key-manager=jgroupsKM:add(key-store=jgroupsKS, credential-reference={clear-text=secret})")
                                .add("/subsystem=elytron/key-store=jgroupsTS:add(path=../../../server.truststore%s.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)",
                                        sharedKS ? "" : trustStoreFor)
                                .add("/subsystem=elytron/trust-manager=jgroupsTM:add(key-store=jgroupsTS)")
                                .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"])")
                                .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"], authentication-optional=true, want-client-auth=true, need-client-auth=true)")
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=client-ssl-context, value=jgroupsCSC)", tp)
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=server-ssl-context, value=jgroupsSSC)", tp)
                                .endBatch()
                                .build())
                        .tearDownScript(createScriptBuilder()
                                .startBatch()
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=server-ssl-context)", tp)
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=client-ssl-context)", tp)
                                .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:remove")
                                .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:remove")
                                .add("/subsystem=elytron/trust-manager=jgroupsTM:remove")
                                .add("/subsystem=elytron/key-store=jgroupsTS:remove")
                                .add("/subsystem=elytron/key-manager=jgroupsKM:remove")
                                .add("/subsystem=elytron/key-store=jgroupsKS:remove")
                                .endBatch()
                                .build())
                        .build());
            }
            return builder.build();
        }

        /**
         * Find oposite node froom list of two nodes
         */
        private static String findOpositeNode(Set<String> nodes, String node) {
            return nodes.stream()
                    .filter(n -> !n.equals(node))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Two nodes.
     *
     * Each node uses private key in key-store and own public key in trust-store. So one node trusts only itself, doesn't trust any other node.
     *
     * TCP transport.
     */
    class UnsharedSecureJGroupsTransportServerSetupTask_NODE_1_2 extends SecureJGroupsTransportServerSetupTask {
        public UnsharedSecureJGroupsTransportServerSetupTask_NODE_1_2() {
            super(NODE_1_2, TCP.class.getSimpleName(), false);
        }
    }

    /**
     * Two nodes.
     *
     * Each node uses private key in key-store and other-node's public key in trust-store. So first node trusts second node and vice versa.
     *
     * TCP transport.
     */
    class UnsharedTrustedSecureJGroupsTransportServerSetupTask_NODE_1_2 extends SecureJGroupsTransportServerSetupTask {
        public UnsharedTrustedSecureJGroupsTransportServerSetupTask_NODE_1_2() {
            super(NODE_1_2, TCP.class.getSimpleName(), false, 2);
        }
    }

    /**
     * Two nodes.
     *
     * Each node uses private key in key-store and other-node's public key in trust-store. So first node trusts second node and vice versa.
     *
     * TCP transport.
     */
    class UnsharedOneTrustedOnlySecureJGroupsTransportServerSetupTask_NODE_1_2 extends SecureJGroupsTransportServerSetupTask {
        public UnsharedOneTrustedOnlySecureJGroupsTransportServerSetupTask_NODE_1_2() {
            super(NODE_1_2, TCP.class.getSimpleName(), false, 1);
        }
    }

    /**
     * Three nodes.
     *
     * Each node uses shared private key in key-store and shared public key in trust-store.
     *
     * TCP transport.
     */
    class SharedStoreSecureJGroupsTransportServerSetupTask_NODE_1_2_3 extends SecureJGroupsTransportServerSetupTask {
        public SharedStoreSecureJGroupsTransportServerSetupTask_NODE_1_2_3() {
            super(NODE_1_2_3, TCP.class.getSimpleName(), true);
        }
    }
}

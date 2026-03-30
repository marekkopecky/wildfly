package org.jboss.as.test.clustering.cluster.jgroups.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.junit.jupiter.api.Test;

/**
 * Variant of the {@link CommandDispatcherTestCase} with TLS-secured TCP transport protocol.
 *
 * Each node is using uniq private key in key-store and other node's public key in trust-store
 */
@ServerSetup({
        TLSServerSetupTasks.PhysicalKeyStoresServerSetupTask_NODE_1_2.class,
        TLSServerSetupTasks.UnsharedTrustedSecureJGroupsTransportServerSetupTask_NODE_1_2.class,
})
public class TLSTCPUniqueKeysCommandDispatcherTestCase extends CommandDispatcherTestCase {
    @Override
    @Test
    public void legacy() throws Exception {
        // This test variant is redundant
    }
}

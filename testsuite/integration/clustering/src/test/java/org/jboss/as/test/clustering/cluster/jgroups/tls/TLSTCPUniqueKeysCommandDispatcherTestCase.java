package org.jboss.as.test.clustering.cluster.jgroups.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.junit.jupiter.api.Test;

import org.jboss.as.test.clustering.cluster.jgroups.TCP_NIO2ServerSetupTask;


@ServerSetup({
        TLSServerSetupTasks.PhysicalKeyStoresServerSetupTask_NODE_1_2.class,
        TLSServerSetupTasks.UnsharedTrustedSecureJGroupsTransportServerSetupTask_NODE_1_2.class,
        TCP_NIO2ServerSetupTask.class
})
public class TLSTCPUniqueKeysCommandDispatcherTestCase extends CommandDispatcherTestCase {
    @Override
    @Test
    public void legacy() throws Exception {
        // This test variant is redundant
    }
}

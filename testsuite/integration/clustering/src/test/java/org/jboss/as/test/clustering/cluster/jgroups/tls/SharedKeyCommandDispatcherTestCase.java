package org.jboss.as.test.clustering.cluster.jgroups.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.junit.jupiter.api.Test;

@ServerSetup({
        TLSServerSetupTasks.SharedPhysicalKeyStoresServerSetupTask.class,
        TLSServerSetupTasks.SharedStoreSecureJGroupsTransportServerSetupTask_NODE_1_2.class,
})
public class SharedKeyCommandDispatcherTestCase extends CommandDispatcherTestCase {
    @Override
    @Test
    public void legacy() throws Exception {
        // This test variant is redundant
    }
}

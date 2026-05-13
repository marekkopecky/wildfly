/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.junit.jupiter.api.Test;

/**
 * Variant of the {@link CommandDispatcherTestCase} with TLS-secured TCP transport protocol.
 *
 * @author Radoslav Husar
 */
@ServerSetup({
        TLSServerSetupTask.PerNodeKeyStore_NODE_1_2.class,
        TLSServerSetupTask.PerNodeSecureJGroupsTransport_TCP_NODE_1_2.class,
})
class TLSTCPCommandDispatcherTestCase extends CommandDispatcherTestCase {

    @Override
    @Test
    public void legacy() throws Exception {
        // This test variant is redundant
    }

}

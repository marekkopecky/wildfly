/*
 * Copyright (c) 2021. Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.eap.expansion.pack;

import static org.jboss.eap.expansion.pack.ExpansionPackDependencyVerifier.isBaseCompatible;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ExpansionPackDependencyVerifierUnitTestCase {

    @Test
    public void testIsBaseCompatible() {
        assertTrue(isBaseCompatible("8.0 GA", "8.0 GA"));
        assertTrue(isBaseCompatible("8.0 Update 1.0", "8.0 Update 1.0"));
        assertTrue(isBaseCompatible("8.0 Update 1.0", "8.0 Update 1.1"));
        assertFalse(isBaseCompatible("8.0 Update 1.1", "8.0 Update 1.0"));
        assertTrue(isBaseCompatible("8.0 Update 1.0", "8.0 Update 2.0"));
        assertFalse(isBaseCompatible("8.0 Update 2.0", "8.0 Update 1.1"));
        // Make sure that the warning is raised when the EAP minor is higher than required
        assertFalse(isBaseCompatible("8.0 Update 1.0", "8.1 Update 1.0"));
        assertTrue(isBaseCompatible("7.4.0.GA", "7.4.1.GA"));
        assertTrue(isBaseCompatible("7.4.0.GA", "7.4.12.GA"));
        assertTrue(isBaseCompatible("7.4.11.GA", "7.4.20.GA"));
        assertFalse(isBaseCompatible("7.4.1.GA", "7.4.0.GA"));
        assertFalse(isBaseCompatible("7.4.0.GA", "7.3.0.GA"));
        assertFalse(isBaseCompatible("7.4.0.GA", "7.5.0.GA"));
        assertFalse(isBaseCompatible("7.4.0.GA", "6.4.0.GA"));
        assertFalse(isBaseCompatible("7.4.0.GA", "8.4.0.GA"));
        assertFalse(isBaseCompatible("7.4.1.CR1", "7.4.1.GA"));
        assertFalse(isBaseCompatible("7.4.1.GA", "7.4.1.CR1"));
        assertFalse(isBaseCompatible("7.4.1.GA", "7.4.1.GA.extra"));
        assertFalse(isBaseCompatible("7.4.1.GA.extra", "7.4.1.GA"));
        assertFalse(isBaseCompatible("7.4.1.GA.extra", "7.4.1.GA.special"));
        assertFalse(isBaseCompatible("Bob", "Mary"));
    }
}

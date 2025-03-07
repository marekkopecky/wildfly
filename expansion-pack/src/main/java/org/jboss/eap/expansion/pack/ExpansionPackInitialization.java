/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.eap.expansion.pack;


import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.ProcessType;
import org.jboss.eap.expansion.pack._private.ExpansionPackLogger;
import org.jboss.msc.service.ServiceTarget;

@SuppressWarnings("unused")
public final class ExpansionPackInitialization implements ModelControllerServiceInitialization {

    @Override
    public void initializeStandalone(ServiceTarget serviceTarget, ManagementModel managementModel, ProcessType processType) {
        logActive();
    }

    @Override
    public void initializeDomain(ServiceTarget serviceTarget, ManagementModel managementModel) {
        // no-op
    }

    @Override
    public void initializeHost(ServiceTarget serviceTarget, ManagementModel managementModel, String s, ProcessType processType) {
        logActive();
    }

    private static void logActive() {
        ExpansionPackLogger.LOGGER.expansionPackActive("JBoss EAP XP");
    }
}
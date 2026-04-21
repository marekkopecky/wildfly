/*
 * Copyright 2023 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.eap.insights.report;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITY_REGISTRY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.XML_NAMESPACES;
import static org.jboss.as.controller.PathElement.WILDCARD_VALUE;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVIDER;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.InsightsSubreport;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.eap.insights.report.logging.InsightsReportLogger;

/**
 *
 * @author Emmanuel Hugonnet (c) 2023 Red Hat, Inc.
 */
class JBossInsightsSubReport implements InsightsSubreport {

    private final ModelControllerClient modelControllerClient;
    private String report;
    private String version;

    JBossInsightsSubReport(final ModelControllerClientFactory clientFactory, final Executor executor) {
        this.modelControllerClient = clientFactory.createSuperUserClient(executor, false);
    }

    @Override
    public void generateReport() {
        try {
            InsightsReportLogger.ROOT_LOGGER.startGettingConfiguration();
            ModelNode configuration = readRootResource();
            Set<String> childTypes = readChildrenTypes(PathAddress.EMPTY_ADDRESS);
            childTypes.remove(SYSTEM_PROPERTY);
            configuration.remove(SYSTEM_PROPERTY);
            for (String childType : childTypes) {
                Set<String> childrenNames = readChildrenNames(PathAddress.EMPTY_ADDRESS, childType);
                configuration.remove(childType);
                for (String childName : childrenNames) {
                    if (CORE_SERVICE.equals(childType) && (PLATFORM_MBEAN.equals(childName) || CAPABILITY_REGISTRY.equals(childName))) {
                        continue;
                    }
                    if (CORE_SERVICE.equals(childType) && MANAGEMENT.equals(childName)) {
                        configuration.get(childType).get(childName).set(processManagement());
                        continue;
                    }
                    PathAddress address = PathAddress.pathAddress(childType, childName);
                    ModelNode child = readChildResource(address);
                    if (child.isDefined()) {
                        configuration.get(childType).get(childName).set(child);
                    } else {
                        configuration.get(childType).remove(childName);
                    }
                }
            }
            configuration = prune(configuration, PathAddress.pathAddress(
                    PathElement.pathElement(EXTENSION, WILDCARD_VALUE),
                    PathElement.pathElement(SUBSYSTEM, WILDCARD_VALUE),
                    PathElement.pathElement(XML_NAMESPACES, WILDCARD_VALUE)));
            version = configuration.get(MANAGEMENT_MAJOR_VERSION).asInt(1) + "." + configuration.get(MANAGEMENT_MINOR_VERSION).asInt(0) + "." + configuration.get(MANAGEMENT_MICRO_VERSION).asInt(0);
            report = new ModelNode("report").set(configuration).toJSONString(false);
            InsightsReportLogger.ROOT_LOGGER.endGettingConfiguration();
        } catch (IOException ex) {
            throw InsightsReportLogger.ROOT_LOGGER.failedToReadRuntimeConfiguration(ex);
        }
    }

    private ModelNode processManagement() throws IOException {
        PathAddress managementAddress = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT);
        Set<String> childTypes = readChildrenTypes(managementAddress);
        ModelNode configuration = new ModelNode();
        for (String childType : childTypes) {
            Set<String> childrenNames = readChildrenNames(managementAddress, childType);
            Set<String> undefinedChildren = new HashSet<>();
            for (String childName : childrenNames) {
                if (ACCESS.equals(childType) && AUTHORIZATION.equals(childName)) {
                    continue;
                }
                PathAddress address = PathAddress.pathAddress(childType, childName);
                ModelNode child = readChildResource(address);
                if (child.isDefined()) {
                    configuration.get(childType).get(childName).set(child);
                } else {
                    if (configuration.has(childType, childName)) {
                        configuration.get(childType).remove(childName);
                    }
                    undefinedChildren.add(childName);
                }
            }
            if (undefinedChildren.size() == childrenNames.size() && configuration.has(childType)) {
                configuration.remove(childType);
            }
        }
        configuration.get(ACCESS).get(AUTHORIZATION).get(PROVIDER).set(readAttribute(managementAddress.append(ACCESS, AUTHORIZATION), PROVIDER));
        return configuration;
    }

    private ModelNode readAttribute(PathAddress address, String attributeName) throws IOException {
        ModelNode op = Operations.createReadAttributeOperation(address.toModelNode(), attributeName);
        op.get(OPERATION_HEADERS).get(ROLES).add("Monitor");
        ModelNode response = this.modelControllerClient.execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT);
        }
        return new ModelNode();
    }

    private Set<String> readChildrenTypes(PathAddress address) throws IOException {
        ModelNode op = Operations.createOperation(READ_CHILDREN_TYPES_OPERATION, address.toModelNode());
        op.get(OPERATION_HEADERS).get(ROLES).add("Monitor");
        ModelNode response = this.modelControllerClient.execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT).asListOrEmpty().stream().map(ModelNode::asString).collect(Collectors.toSet());
        }
        InsightsReportLogger.ROOT_LOGGER.debug(InsightsReportLogger.ROOT_LOGGER.failedToReadRuntimeConfiguration(response.get(FAILURE_DESCRIPTION).asString()).getMessage());
        return Collections.emptySet();
    }

    private Set<String> readChildrenNames(PathAddress address, String childType) throws IOException {
        ModelNode op = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION, address.toModelNode());
        op.get("child-type").set(childType);
        op.get(OPERATION_HEADERS).get(ROLES).add("Monitor");
        ModelNode response = this.modelControllerClient.execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT).asListOrEmpty().stream().map(ModelNode::asString).collect(Collectors.toSet());
        }
        InsightsReportLogger.ROOT_LOGGER.debug(InsightsReportLogger.ROOT_LOGGER.failedToReadRuntimeConfiguration(response.get(FAILURE_DESCRIPTION).asString()).getMessage());
        return Collections.emptySet();
    }

    private ModelNode readRootResource() throws IOException {
        ModelNode op = Operations.createReadResourceOperation(PathAddress.EMPTY_ADDRESS.toModelNode(), false);
        op.get(OPERATION_HEADERS).get(ROLES).add("Monitor");
        op.get("include-runtime").set(true);
        op.get("include-defaults").set(true);
        ModelNode response = this.modelControllerClient.execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT);
        } else {
            throw InsightsReportLogger.ROOT_LOGGER.failedToReadRuntimeConfiguration(response.get(FAILURE_DESCRIPTION).asString());
        }
    }

    private ModelNode readChildResource(PathAddress address) throws IOException {
        ModelNode op = Operations.createReadResourceOperation(address.toModelNode(), true);
        op.get(OPERATION_HEADERS).get(ROLES).add("Monitor");
        op.get("include-runtime").set(true);
        op.get("include-defaults").set(true);
        ModelNode response = this.modelControllerClient.execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT);
        }
        InsightsReportLogger.ROOT_LOGGER.debug(InsightsReportLogger.ROOT_LOGGER.failedToReadRuntimeConfiguration(response.get(FAILURE_DESCRIPTION).asString()).getMessage());
        return new ModelNode();
    }

    public String getReport() {
        return report;
    }

    private ModelNode prune(ModelNode configuration, PathAddress address) {
        Iterator<PathElement> iter = address.iterator();
        int i = 0;
        ModelNode current = configuration;
        while (iter.hasNext()) {
            PathElement element = iter.next();
            i++;
            if (iter.hasNext()) {
                if (element.isWildcard() && current.hasDefined(element.getKey())) {
                    ModelNode children = current.get(element.getKey());
                    for (Property child : children.asPropertyList()) {
                        if (child.getValue().isDefined()) {
                            ModelNode result = prune(child.getValue().clone(), address.subAddress(i));
                            current.get(element.getKey()).get(child.getName()).set(result);
                        }
                    }
                } else {
                    current = current.get(element.getKey()).get(element.getValue());
                }
            } else {
                try {
                    if (element.isWildcard()) {
                        if (current.has(element.getKey())) {
                            current.remove(element.getKey());
                        }
                    } else {
                        if (current.get(element.getKey()).has(element.getValue())) {
                            current.get(element.getKey()).remove(element.getValue());
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    InsightsReportLogger.ROOT_LOGGER.error("Failed to remove " + element.getKey() + " from " + current, ex);
                }
            }
        }
        return configuration;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public JsonSerializer<InsightsSubreport> getSerializer() {
        return new JBossInsightsSubReportSerializer();
    }

}

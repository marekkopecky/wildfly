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
package org.jboss.as.test.manualmode.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.HttpRequest;

import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

public class InsightsHttpRequestParser {

    public static InsightsRequest parse(HttpRequest request) throws Exception {
        BodyPart bodyPart = extractPayloadBodyPart(request);
        return new InsightsRequest(bodyPart.getFileName(), decompressPayload(bodyPart));
    }

    private static BodyPart extractPayloadBodyPart(HttpRequest request) throws Exception {
        ByteArrayDataSource source = new ByteArrayDataSource(new ByteArrayInputStream(request.getBodyAsRawBytes()), "multipart/form-data");
        MimeMultipart multipart = new MimeMultipart(source);
        int partCount = multipart.getCount();
        BodyPart bodyPart = null;
        for (int i = 0; i < partCount; i++) {
            bodyPart = multipart.getBodyPart(i);
            if (bodyPart.getContentType().equals("application/vnd.redhat.runtimes-java-general.analytics+tgz")) {
                break;
            }
        }
        return bodyPart;
    }

    private static JsonNode decompressPayload(BodyPart payloadBodyPart) throws Exception {
        JsonNode payload = null;
        if (payloadBodyPart != null) {
            try (GZIPInputStream gis = new GZIPInputStream(payloadBodyPart.getInputStream())) {
                ObjectMapper mapper = new ObjectMapper();
                payload = mapper.readTree(gis);
            }
        }
        return payload;
    }
}

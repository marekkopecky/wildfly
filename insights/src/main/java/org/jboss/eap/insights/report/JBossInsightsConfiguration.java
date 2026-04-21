/* Copyright (C) Red Hat 2023 */
package org.jboss.eap.insights.report;

import com.redhat.insights.InsightsException;
import com.redhat.insights.config.DefaultInsightsConfiguration;
import com.redhat.insights.config.EnvAndSysPropsInsightsConfiguration;

import java.util.Locale;

/**
 * Configuration where values from {@link DefaultInsightsConfiguration} can be
 * overridden by environment variables and system properties.
 *
 * <p>
 * Environment variables take priority over system properties.
 */
class JBossInsightsConfiguration extends EnvAndSysPropsInsightsConfiguration {

    private static final String ENV_UNSUPPORTED_CONFIGURATION_TRUST_ALL = "UNSUPPORTED_CONFIGURATION_TRUST_ALL";
    private static final String ENV_UNSUPPORTED_MACHINE_ID_FILE_PATH = "UNSUPPORTED_MACHINE_ID_FILE_PATH";
    private static final String ENV_UNSUPPORTED_RESCAN_CLASSPATH = "UNSUPPORTED_RESCAN_CLASSPATH";

    private String identificationName;

    public boolean isTrustAll() {
        return Boolean.parseBoolean(lookup(ENV_UNSUPPORTED_CONFIGURATION_TRUST_ALL));
    }

    public boolean isRescanClasspath() {
        return Boolean.parseBoolean(lookup(ENV_UNSUPPORTED_RESCAN_CLASSPATH));
    }

    public void setIdentificationName(String identificationName) {
        this.identificationName = identificationName;
    }

    @Override
    public String getIdentificationName() {
        if(this.identificationName != null && ! this.identificationName.trim().isEmpty()) {
            return this.identificationName;
        }
        return super.getIdentificationName();
    }

    private String lookup(String env) {
        String value = System.getenv(env);
        if (value == null) {
            value = System.getProperty(env.toLowerCase(Locale.getDefault()).replace('_', '.'));
        }
        return value;
    }

    @Override
    public String getCertHelperBinary() {
        String value = lookup(ENV_CERT_HELPER_BINARY);
        if (value != null) {
            return value;
        }
        return "/opt/rh/eap8/root/opt/jboss-cert-helper";
    }

    @Override
    public String getMachineIdFilePath() {
        String value = lookup(ENV_UNSUPPORTED_MACHINE_ID_FILE_PATH);
        if (value != null) {
            return value;
        }
        return super.getMachineIdFilePath();
    }

    @Override
    public String toString() {
        String id;
        try {
            id = getIdentificationName();
        } catch(InsightsException ex) {
            id = "undefined";
        }
        return "JBossInsightsConfiguration{"
                + "identificationName = "
                + id
                + ", certFilePath = "
                + getCertFilePath()
                + ", keyFilePath = "
                + getKeyFilePath()
                + ", maybeAuthToken = "
                + getMaybeAuthToken()
                + ", uploadBaseURL  = "
                + getUploadBaseURL()
                + ", uploadUri = "
                + getUploadUri()
                + ", archiveUploadDir = "
                + getArchiveUploadDir()
                + ", proxyConfiguration ="
                + getProxyConfiguration()
                + ", isOptingOut = "
                + isOptingOut()
                + ", connectPeriod = "
                + getConnectPeriod()
                + ", updatePeriod = "
                + getUpdatePeriod()
                + ", httpClientRetryMaxAttempts = "
                + getHttpClientRetryMaxAttempts()
                + ", certHelperBinary = "
                + getCertHelperBinary()
                + ", machineIdFilePath = "
                + getMachineIdFilePath()
                + '}';
    }
}

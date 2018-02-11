# JBoss EAP - Red Hat Internal Readme

This is a Red Hat internal README file containing specific instructions relevant for building a version of JBoss EAP
that has not yet been officially released. In this case it may be necessary to have access to the internal JBoss EAP
Maven repository, to be able to download any artifacts not present in public repositories.

## Prerequisites

* Be connected to the Red Hat VPN. This is necessary to download the JBoss EAP maven artifacts from the internal maven repository.
* Add the Red Hat root certificate as trusted certificate on your JDK keystore. This is necessary to download the maven artifacts via HTTPS.
* Fedora users, if you get build errors like `PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target`, ensure that you are using a system installed JDK (e.g. `dnf install java-11-openjdk-devel`) that has access to the certificate.  You can download the certificate RPM install from https://hdn.corp.redhat.com/rhel8-csb/repoview/redhat-internal-cert-install.html and install the rpm via `dnf install redhat-internal-cert-install-0.1-29.el7.noarch.rpm`

## How to install the Red Hat root certificates on your JDK keystore

If you are using system provided JDK (rpm installed), you can install the Red Hat root certificates system-wide:
```
curl https://certs.corp.redhat.com/certs/Current-IT-Root-CAs.pem -o /etc/pki/ca-trust/source/anchors/Current-IT-Root-CAs.pem
update-ca-trust
```

Alternatively, you can follow these instructions to install the Red Hat root certificates as trusted certificate on your
JDK keystore:

1. Download the Red Hat root certificates (2022 and 2015):
    ```
    wget https://certs.corp.redhat.com/certs/2022-IT-Root-CA.pem
    wget https://certs.corp.redhat.com/certs/2015-IT-Root-CA.pem
    ```

2. Install the certificates on your JDK keystore:
    ```
    keytool -import -alias internal2022.redhat.com -keystore <JDK_Path>/lib/security/cacerts -file 2022-IT-Root-CA.pem -storepass changeit
    keytool -import -alias internal2015.redhat.com -keystore <JDK_Path>/lib/security/cacerts -file 2015-IT-Root-CA.pem -storepass changeit
    ```

From this point, you can follow the "Building" section in the main [README file](./README.md#building-1) to build
JBoss EAP from sources.

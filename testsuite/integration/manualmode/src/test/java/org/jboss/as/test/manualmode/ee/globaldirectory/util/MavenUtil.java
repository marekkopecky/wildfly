package org.jboss.as.test.manualmode.ee.globaldirectory.util;

import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

public class MavenUtil {

   private static final String AETHER_API_NAME = File.separatorChar == '/' ? "/org/eclipse/aether/aether-api/" : "\\org\\eclipse\\aether\\aether-api\\";

   private final RepositorySystem REPOSITORY_SYSTEM;
   private final List<RemoteRepository> remoteRepositories;

   private static final String PROXY_HTTP_PREFIX = "http.";
   private static final String PROXY_HTTPS_PREFIX = "https.";
   private static final String PROXY_HOST = "proxyHost";
   private static final String PROXY_PORT = "proxyPort";

   private static String mavenRepository;

   private MavenUtil(final RepositorySystem repositorySystem, final List<RemoteRepository> remoteRepositories) {
      this.REPOSITORY_SYSTEM = repositorySystem;
      this.remoteRepositories = remoteRepositories;
   }

   public static MavenUtil create(boolean useEapRepository) {
      return new MavenUtil(newRepositorySystem(), createRemoteRepositories(useEapRepository));
   }

   public File createMavenGavFile(String artifactGav) throws MalformedURLException {
      Artifact artifact = new DefaultArtifact(artifactGav);
      if (artifact.getVersion() == null) {
         throw new IllegalArgumentException("Null version");
      }

      VersionScheme versionScheme = new GenericVersionScheme();
      try {
         versionScheme.parseVersion(artifact.getVersion());
      } catch (InvalidVersionSpecificationException e) {
         throw new IllegalArgumentException(e);
      }

      try {
         versionScheme.parseVersionRange(artifact.getVersion());
         throw new IllegalArgumentException(artifact.getVersion() + " is a version range. A specific version is needed");
      } catch (InvalidVersionSpecificationException expected) {

      }

      RepositorySystemSession session = newRepositorySystemSession();

      ArtifactRequest artifactRequest = new ArtifactRequest();
      artifactRequest.setArtifact(artifact);
      for (RemoteRepository remoteRepo : remoteRepositories) {
         artifactRequest.addRepository(remoteRepo);
      }

      ArtifactResult artifactResult;
      try {
         artifactResult = REPOSITORY_SYSTEM.resolveArtifact(session, artifactRequest);
      } catch (ArtifactResolutionException e) {
         throw new RuntimeException(e);
      }


      File file = artifactResult.getArtifact().getFile().getAbsoluteFile();
      return file;
   }

   private static Integer getProxyPort(String systemProperty) {
      String port = System.getProperty(systemProperty);
      if (port != null && !port.isEmpty()) {
         try {
            Integer intPort = Integer.parseInt(port);
            return intPort;
         } catch (NumberFormatException e) {
            return null;
         }
      }
      return null;
   }

   private static List<RemoteRepository> createRemoteRepositories(boolean useEapRepository) {
      // prepare proxy
      String httpProxyHost = System.getProperty(String.format("%s%s", PROXY_HTTP_PREFIX, PROXY_HOST));
      String httpsProxyHost = System.getProperty(String.format("%s%s", PROXY_HTTPS_PREFIX, PROXY_HOST));
      Integer httpProxyPort = getProxyPort(String.format("%s%s", PROXY_HTTP_PREFIX, PROXY_PORT));
      Integer httpsProxyPort = getProxyPort(String.format("%s%s", PROXY_HTTPS_PREFIX, PROXY_PORT));
      Proxy httpProxy = null;
      Proxy httpsProxy = null;
      if (httpProxyHost != null && httpProxyPort != null && !httpProxyHost.isEmpty()) {
         httpProxy = new Proxy(Proxy.TYPE_HTTP, httpProxyHost, httpProxyPort);
      }
      if (httpsProxyHost != null && httpsProxyPort != null && !httpsProxyHost.isEmpty()) {
         httpsProxy = new Proxy(Proxy.TYPE_HTTPS, httpsProxyHost, httpsProxyPort);
      }

      List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

      if (useEapRepository) {
         RemoteRepository.Builder repository = new RemoteRepository.Builder("product-repository", "default", "https://maven.repository.redhat.com/nexus/content/groups/product-techpreview/");
         if (httpsProxy != null) {
            repository.setProxy(httpsProxy);
         }
         remoteRepositories.add(repository.build());
      }

      // always add jboss developer repository
      RemoteRepository.Builder repository = new RemoteRepository.Builder("jboss-developer", "default", "http://repository.jboss.org/nexus/content/groups/developer/");
      if (httpProxy != null) {
         repository.setProxy(httpProxy);
      }
      remoteRepositories.add(repository.build());

      // always add default maven repository
      RemoteRepository.Builder repository2 = new RemoteRepository.Builder("maven-default", "default", "https://repo.maven.apache.org/maven2/");
      if (httpProxy != null) {
         repository2.setProxy(httpProxy);
      }
      remoteRepositories.add(repository2.build());

      // always add spring repository
      RemoteRepository.Builder repository1 = new RemoteRepository.Builder("maven1", "default", "https://repo.spring.io/libs-milestone/");
      if (httpProxy != null) {
         repository1.setProxy(httpProxy);
      }
      remoteRepositories.add(repository1.build());

      return remoteRepositories;
   }

   private RepositorySystemSession newRepositorySystemSession() {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      LocalRepository localRepo = new LocalRepository(determineLocalMavenRepositoryHack());
      session.setLocalRepositoryManager(REPOSITORY_SYSTEM.newLocalRepositoryManager(session, localRepo));

      return session;
   }

   private static String determineLocalMavenRepositoryHack() {
      if (mavenRepository == null) {
         String classPath = System.getProperty("java.class.path");
         int end = classPath.indexOf(AETHER_API_NAME) + 1;
         int start = classPath.lastIndexOf(File.pathSeparatorChar, end) + 1;
         String localRepositoryRoot = classPath.substring(start, end);
         mavenRepository = localRepositoryRoot;
      }
      return mavenRepository;
   }

   /*
    * Aether's components implement
    * org.sonatype.aether.spi.locator.Service to ease manual wiring and
    * using the prepopulated DefaultServiceLocator, we only need to
    * register the repository connector factories.
    */
   public static RepositorySystem newRepositorySystem() {
      DefaultServiceLocator locator = new DefaultServiceLocator();
      locator.addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);
      locator.addService(VersionResolver.class, DefaultVersionResolver.class);
      locator.addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class);
      locator.addService(MetadataGeneratorFactory.class, SnapshotMetadataGeneratorFactory.class);
      locator.addService(MetadataGeneratorFactory.class, VersionsMetadataGeneratorFactory.class);

      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
      locator.addService(TransporterFactory.class, FileTransporterFactory.class);
      locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

      return locator.getService(RepositorySystem.class);
   }

}

package org.jboss.as.test.manualmode.ee.globaldirectory.deployments;

import org.eclipse.microprofile.config.spi.ConfigSource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * @author Tomas Terem (tterem@redhat.com)
 **/
@Path("global-directory")
public class GlobalDirectoryDeployment3 {

   @Path("/library3")
   @GET
   @Produces("text/plain")
   public String get() {
      return String.valueOf(ConfigSource.DEFAULT_ORDINAL);
   }
}
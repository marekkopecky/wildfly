package org.jboss.as.test.integration.domain.suites;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * @author Tomas Terem (tterem@redhat.com)
 **/
@Path("global-directory")
public class GlobalDirectoryDeployment {

   @Path("/library")
   @GET
   @Produces("text/plain")
   public String get() {
      GlobalDirectoryLibrary globalDirectoryLibrary = new GlobalDirectoryLibraryImpl();
      return globalDirectoryLibrary.get();
   }
}

package org.jboss.as.test.manualmode.ee.globaldirectory.deployments;

import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibrary;
import org.jboss.as.test.manualmode.ee.globaldirectory.libraries.GlobalDirectoryLibraryImpl2;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * @author Tomas Terem (tterem@redhat.com)
 **/
@Path("global-directory")
public class GlobalDirectoryDeployment2 {

   @Path("/library2")
   @GET
   @Produces("text/plain")
   public String get() {
      GlobalDirectoryLibrary globalDirectoryLibrary = new GlobalDirectoryLibraryImpl2();
      return globalDirectoryLibrary.get();
   }
}

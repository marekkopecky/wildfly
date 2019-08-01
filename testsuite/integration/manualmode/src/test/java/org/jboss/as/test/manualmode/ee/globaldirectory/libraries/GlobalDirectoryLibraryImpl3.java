package org.jboss.as.test.manualmode.ee.globaldirectory.libraries;

/**
 * @author Tomas Terem (tterem@redhat.com)
 **/
public class GlobalDirectoryLibraryImpl3 implements GlobalDirectoryLibrary {

   private String s1 = "HELLO WORLD";

   public String get() {
      return s1;
   }
}

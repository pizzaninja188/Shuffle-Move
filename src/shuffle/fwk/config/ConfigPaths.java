/*  ShuffleMove - A program for identifying and simulating ideal moves in the game
 *  called Pokemon Shuffle.
 *
 *  Copyright (C) 2015  Andrew Meyers
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package shuffle.fwk.config;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * Resolves user config files and bundled resources.
 * <p>
 * The desktop app keeps its config relative to the working directory, which is the default here.
 * Android has no usable working directory, so it calls {@link #setBaseDirectory(File)} with its
 * private files dir before loading any config. Bundled resources are looked up through this
 * library's class loader, since Android's system class loader cannot see app resources.
 */
public final class ConfigPaths {

   private static volatile File baseDirectory = null;

   private ConfigPaths() {
   }

   /**
    * Sets the directory that relative config file paths (such as "config/preferences.txt") resolve
    * against. Null restores the default, the process working directory.
    */
   public static void setBaseDirectory(File dir) {
      baseDirectory = dir == null ? null : dir.getAbsoluteFile();
   }

   /** @return The directory relative config paths resolve against. Never null. */
   public static File getBaseDirectory() {
      File dir = baseDirectory;
      return dir != null ? dir : new File(System.getProperty("user.dir")).getAbsoluteFile();
   }

   /** @return The absolute file for the given config path. */
   public static File resolve(String path) {
      File f = new File(path);
      if (f.isAbsolute()) {
         return f;
      }
      File dir = baseDirectory;
      return dir == null ? f.getAbsoluteFile() : new File(dir, path);
   }

   /** @return The bundled resource as a stream, or null if there is none. */
   public static InputStream openResource(String name) {
      if (name == null) {
         return null;
      }
      ClassLoader cl = ConfigPaths.class.getClassLoader();
      InputStream is = cl == null ? null : cl.getResourceAsStream(name);
      return is != null ? is : ClassLoader.getSystemResourceAsStream(name);
   }

   /** @return The URL of the bundled resource, or null if there is none. */
   public static URL getResource(String name) {
      if (name == null) {
         return null;
      }
      ClassLoader cl = ConfigPaths.class.getClassLoader();
      URL url = cl == null ? null : cl.getResource(name);
      return url != null ? url : ClassLoader.getSystemResource(name);
   }
}

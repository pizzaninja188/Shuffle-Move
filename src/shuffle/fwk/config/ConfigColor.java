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

/**
 * A toolkit-independent RGBA color stored in configuration (the COLOR entry type). The desktop UI
 * converts it to java.awt.Color; Android converts it to an ARGB int.
 */
public final class ConfigColor {

   private final int red;
   private final int green;
   private final int blue;
   private final int alpha;

   public ConfigColor(int red, int green, int blue) {
      this(red, green, blue, 255);
   }

   public ConfigColor(int red, int green, int blue, int alpha) {
      this.red = check("Red", red);
      this.green = check("Green", green);
      this.blue = check("Blue", blue);
      this.alpha = check("Alpha", alpha);
   }

   private static int check(String name, int value) {
      if (value < 0 || value > 255) {
         throw new NumberFormatException(name + " value invalid: " + value);
      }
      return value;
   }

   public int getRed() {
      return red;
   }

   public int getGreen() {
      return green;
   }

   public int getBlue() {
      return blue;
   }

   public int getAlpha() {
      return alpha;
   }

   /** @return This color packed as 0xAARRGGBB. */
   public int getArgb() {
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   @Override
   public int hashCode() {
      return getArgb();
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof ConfigColor && ((ConfigColor) obj).getArgb() == getArgb();
   }

   @Override
   public String toString() {
      return String.format("%d %d %d %d", red, green, blue, alpha);
   }
}

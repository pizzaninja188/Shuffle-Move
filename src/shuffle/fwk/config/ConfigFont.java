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
 * A toolkit-independent font description stored in configuration (the FONT entry type). Style bits
 * match java.awt.Font (PLAIN = 0, BOLD = 1, ITALIC = 2) so the stored format is unchanged.
 */
public final class ConfigFont {

   public static final int PLAIN = 0;
   public static final int BOLD = 1;
   public static final int ITALIC = 2;

   private final String family;
   private final int style;
   private final int size;

   public ConfigFont(String family, int style, int size) {
      this.family = family;
      this.style = style & (BOLD | ITALIC);
      this.size = size;
   }

   public String getFamily() {
      return family;
   }

   public int getStyle() {
      return style;
   }

   public int getSize() {
      return size;
   }

   public boolean isBold() {
      return (style & BOLD) != 0;
   }

   public boolean isItalic() {
      return (style & ITALIC) != 0;
   }

   @Override
   public int hashCode() {
      return (family == null ? 0 : family.hashCode()) * 31 * 31 + style * 31 + size;
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof ConfigFont)) {
         return false;
      }
      ConfigFont o = (ConfigFont) obj;
      return style == o.style && size == o.size && (family == null ? o.family == null : family.equals(o.family));
   }

   @Override
   public String toString() {
      return String.format("%s %d %d", family, style, size);
   }
}

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

package shuffle.fwk.gui;

import java.awt.Color;
import java.awt.Font;

import shuffle.fwk.config.ConfigColor;
import shuffle.fwk.config.ConfigFont;

/**
 * Converts the toolkit-independent config values from :core into AWT types for the Swing UI.
 */
public final class AwtConfig {

   private AwtConfig() {
   }

   /** @return The AWT color for the given config color, or null if it is null. */
   public static Color color(ConfigColor c) {
      return c == null ? null : new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
   }

   /** @return The AWT color for the given config color, or def if it is null. */
   public static Color color(ConfigColor c, Color def) {
      Color ret = color(c);
      return ret == null ? def : ret;
   }

   /** @return The AWT font for the given config font, or def if it is null. */
   public static Font font(ConfigFont f, Font def) {
      return f == null ? def : new Font(f.getFamily(), f.getStyle(), f.getSize());
   }
}

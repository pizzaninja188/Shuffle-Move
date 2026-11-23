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

package shuffle.fwk;

import java.util.List;

import shuffle.fwk.config.manager.SpeciesManager;
import shuffle.fwk.config.manager.TeamManager;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Species;
import shuffle.fwk.data.SpeciesPaint;
import shuffle.fwk.data.Team;

public enum EntryMode {
   PAINT("paint") {
      @Override
      public String getColorKey() {
         return "PAINT_MODE_COLOR";
      }
      
      @Override
      public void handleKeyPress(EntryModeUser user, int keyCode, char keyChar) {
         TeamManager tm = user.getTeamManager();
         if (tm.isFrozenBind(keyChar)) {
            user.toggleFrozen();
         } else if (keyCode == VK_TAB) {
            user.changeMode();
         } else {
            Species species = getSpeciesFor(user, keyChar);
            if (!species.equals(Species.AIR)) {
               user.setSelectedSpecies(species);
            }
         }
      }
      
      @Override
      public void handleGridEvent(EntryModeUser user, int row, int col, boolean isErase) {
         SpeciesPaint curPaint;
         if (isErase) {
            curPaint = SpeciesPaint.AIR;
         } else {
            curPaint = user.getCurrentSpeciesPaint();
         }
         user.paintAt(curPaint, row, col);
      }
      
      @Override
      public EntryMode getNextMode() {
         return EXPRESS;
      }
      
   },
   EXPRESS("express") {
      @Override
      public String getColorKey() {
         return "EXPRESS_MODE_COLOR";
      }
      
      @Override
      public void handleKeyPress(EntryModeUser user, int keyCode, char keyChar) {
         TeamManager tm = user.getTeamManager();
         if (tm.isFrozenBind(keyChar)) {
            List<Integer> prevCursor = user.getPreviousCursor();
            if (prevCursor != null && prevCursor.size() >= 2) {
               user.toggleFrozenAt(prevCursor.get(0), prevCursor.get(1));
            }
         } else if (keyChar == VK_TAB) {
            user.changeMode();
         } else if (keyChar == VK_SPACE) {
            user.advanceCursorBy(1);
         } else if (keyChar == VK_BACK_SPACE) {
            user.advanceCursorBy(-1);
         } else if (keyChar == VK_DELETE) {
            paintWith(user, SpeciesPaint.AIR);
         } else if (keyCode == VK_UP) {
            user.advanceCursorBy(-Board.NUM_COLS);
         } else if (keyCode == VK_DOWN) {
            user.advanceCursorBy(+Board.NUM_COLS);
         } else if (keyCode == VK_LEFT) {
            user.advanceCursorBy(-1);
         } else if (keyCode == VK_RIGHT) {
            user.advanceCursorBy(+1);
         } else {
            Species species = getSpeciesFor(user, keyChar);
            if (!Species.AIR.equals(species)) {
               user.setSelectedSpecies(species);
               SpeciesPaint curPaint = user.getCurrentSpeciesPaint();
               paintWith(user, curPaint);
               
            }
         }
      }
      
      @Override
      public void handleGridEvent(EntryModeUser user, int row, int col, boolean isErase) {
         user.setCursorTo(row, col);
      }
      
      @Override
      public EntryMode getNextMode() {
         return PAINT;
      }
   };
   
   // Values of java.awt.event.KeyEvent.VK_* used above.
   private static final int VK_TAB = 0x09;
   private static final int VK_SPACE = 0x20;
   private static final int VK_BACK_SPACE = 0x08;
   private static final int VK_DELETE = 0x7F;
   private static final int VK_LEFT = 0x25;
   private static final int VK_UP = 0x26;
   private static final int VK_RIGHT = 0x27;
   private static final int VK_DOWN = 0x28;
   
   /**
    * @param user
    * @param species
    */
   protected static void paintWith(EntryModeUser user, SpeciesPaint paint) {
      List<Integer> coords = user.getCurrentCursor();
      user.paintAt(paint, coords.get(0), coords.get(1));
      user.advanceCursorBy(1);
   }
   
   private String i18nKey;
   
   private EntryMode(String text) {
      i18nKey = text;
   }
   
   /**
    * @return
    */
   public String getI18nKey() {
      return i18nKey;
   }
   
   protected Species getSpeciesFor(EntryModeUser user, char keyChar) {
      Team team = user.getCurrentTeam();
      SpeciesManager speciesManager = user.getSpeciesManager();
      return team.getSpecies(keyChar, speciesManager);
   }
   
   public abstract String getColorKey();
   
   /**
    * Handles a key press. Key codes use the java.awt.event.KeyEvent VK_ values (mirrored below) so
    * this class has no AWT dependency and can run headless.
    * 
    * @param user
    *           The EntryModeUser to use.
    * @param keyCode
    *           The key code, as from KeyEvent.getKeyCode()
    * @param keyChar
    *           The key character, as from KeyEvent.getKeyChar()
    */
   public abstract void handleKeyPress(EntryModeUser user, int keyCode, char keyChar);
   
   /**
    * Handles the specified grid paint action, by row and column.
    * 
    * @param user
    *           The EntryModeUser to use.
    * @param row
    *           an int within [1, 6]
    * @param col
    *           an int within [1, 6]
    * @param isErase
    *           if true, this will try to erase the specified coordinate. Otherwise ignored.
    */
   public abstract void handleGridEvent(EntryModeUser user, int row, int col, boolean isErase);
   
   /**
    * Returns the mode that comes after this one.
    * 
    * @return An EntryMode, never null.
    */
   public abstract EntryMode getNextMode();
   
}
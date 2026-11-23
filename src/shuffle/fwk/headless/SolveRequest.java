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

package shuffle.fwk.headless;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import shuffle.fwk.GradingMode;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Effect;
import shuffle.fwk.data.Stage;
import shuffle.fwk.data.Team;

/**
 * Everything one simulation needs. Board, stage and team are required; the rest have the same
 * defaults the desktop app uses.
 */
public final class SolveRequest {

   /** Desktop default (preferences.txt NUM_FEEDERS). */
   public static final int DEFAULT_FEEDERS = 50;

   private final Board board;
   private final Stage stage;
   private final Team team;
   private int numFeeders = DEFAULT_FEEDERS;
   private Integer remainingMoves = null;
   private Integer remainingHealth = null;
   private GradingMode gradingMode = null;
   private Collection<Effect> disabledEffects = Collections.emptyList();
   private boolean attackPowerUp = false;
   private int effectThreshold = 0;

   /**
    * @param board
    *           The board to solve. Its mega progress is used as the current mega progress.
    * @param stage
    *           The stage being played.
    * @param team
    *           The team (support species, mega slot, non-support species) for that stage.
    */
   public SolveRequest(Board board, Stage stage, Team team) {
      if (board == null || stage == null || team == null) {
         throw new IllegalArgumentException("board, stage and team are required");
      }
      this.board = new Board(board);
      this.stage = stage;
      this.team = team;
   }

   public Board getBoard() {
      return new Board(board);
   }

   public Stage getStage() {
      return stage;
   }

   public Team getTeam() {
      return team;
   }

   public int getNumFeeders() {
      return numFeeders;
   }

   /** More feeders = more accurate averages over random skyfall, but proportionally slower. */
   public SolveRequest setNumFeeders(int numFeeders) {
      this.numFeeders = Math.max(1, numFeeders);
      return this;
   }

   /** @return Moves left including the one about to be made. Defaults to the stage's move count. */
   public int getRemainingMoves() {
      return remainingMoves != null ? remainingMoves : stage.getMoves();
   }

   public SolveRequest setRemainingMoves(int remainingMoves) {
      this.remainingMoves = Math.max(1, remainingMoves);
      return this;
   }

   /** @return Health left on the target. Defaults to the stage's full health. */
   public int getRemainingHealth() {
      return remainingHealth != null ? remainingHealth : stage.getHealth();
   }

   public SolveRequest setRemainingHealth(int remainingHealth) {
      this.remainingHealth = Math.max(0, remainingHealth);
      return this;
   }

   /** @return The grading mode to rank by, or null for the engine's current one. */
   public GradingMode getGradingMode() {
      return gradingMode;
   }

   public SolveRequest setGradingMode(GradingMode gradingMode) {
      this.gradingMode = gradingMode;
      return this;
   }

   public Collection<Effect> getDisabledEffects() {
      return disabledEffects;
   }

   public SolveRequest setDisabledEffects(Collection<Effect> disabledEffects) {
      this.disabledEffects = disabledEffects == null ? Collections.<Effect> emptyList()
            : Collections.unmodifiableList(new ArrayList<Effect>(disabledEffects));
      return this;
   }

   public boolean isAttackPowerUp() {
      return attackPowerUp;
   }

   public SolveRequest setAttackPowerUp(boolean attackPowerUp) {
      this.attackPowerUp = attackPowerUp;
      return this;
   }

   public int getEffectThreshold() {
      return effectThreshold;
   }

   public SolveRequest setEffectThreshold(int effectThreshold) {
      this.effectThreshold = Math.max(0, effectThreshold);
      return this;
   }
}

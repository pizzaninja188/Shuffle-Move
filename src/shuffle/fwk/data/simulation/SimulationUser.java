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

package shuffle.fwk.data.simulation;

import java.util.Collection;

import shuffle.fwk.config.provider.BoardManagerProvider;
import shuffle.fwk.config.provider.EffectManagerProvider;
import shuffle.fwk.config.provider.GradingModeManagerProvider;
import shuffle.fwk.config.provider.RosterManagerProvider;
import shuffle.fwk.config.provider.SpeciesManagerProvider;
import shuffle.fwk.data.Effect;
import shuffle.fwk.data.Team;
import shuffle.fwk.data.simulation.util.SimulationAcceptor;

/**
 * Everything {@link SimulationCore} reads to set up a simulation. This interface is deliberately
 * headless: the desktop UI's extra requirements live in ShuffleModelUser, so Android (or any other
 * front end) only has to supply these values.
 *
 * @author Andrew Meyers
 *
 */
public interface SimulationUser extends SimulationAcceptor, RosterManagerProvider, SpeciesManagerProvider,
      BoardManagerProvider, EffectManagerProvider, GradingModeManagerProvider {

   public boolean isMegaAllowed();

   /** @return The team for the current stage. */
   Team getCurrentTeam();

   /** @return How many random skyfall variations to simulate per move (accuracy vs. time). */
   int getPreferredNumFeeders();

   int getMegaProgress();

   int getRemainingHealth();

   int getRemainingMoves();

   Collection<Effect> getDisabledEffects();

   boolean getAttackPowerUp();

   int getEffectThreshold();

   boolean isMobileMode();

}

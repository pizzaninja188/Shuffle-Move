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
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import shuffle.fwk.GradingMode;
import shuffle.fwk.config.ConfigFactory;
import shuffle.fwk.config.ConfigManager;
import shuffle.fwk.config.EntryType;
import shuffle.fwk.config.manager.BoardManager;
import shuffle.fwk.config.manager.EffectManager;
import shuffle.fwk.config.manager.GradingModeManager;
import shuffle.fwk.config.manager.RosterManager;
import shuffle.fwk.config.manager.SpeciesManager;
import shuffle.fwk.config.manager.StageManager;
import shuffle.fwk.config.manager.TeamManager;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Effect;
import shuffle.fwk.data.Stage;
import shuffle.fwk.data.Team;
import shuffle.fwk.data.simulation.SimulationCore;
import shuffle.fwk.data.simulation.SimulationResult;
import shuffle.fwk.data.simulation.SimulationUser;

/**
 * Headless entry point to the Shuffle Move simulator: give it a board, stage and team and it
 * returns the possible moves ranked best first, using exactly the same simulation as the desktop
 * app.
 * <p>
 * Config (species, stages, effects, roster levels, saved teams) comes from the same text files as
 * the desktop app. Call {@link shuffle.fwk.config.ConfigPaths#setBaseDirectory} first if user config
 * should live somewhere other than the working directory. Instances are thread safe; one solve runs
 * at a time per engine.
 */
public class ShuffleEngine implements AutoCloseable {

   private static final String KEY_MOBILE_MODE = "MOBILE_MODE";

   private final ConfigFactory factory;
   private final BoardManager boardManager;
   private ForkJoinPool pool = newPool();

   /** Uses the default config locations (bundled defaults plus config/ under the base directory). */
   public ShuffleEngine() {
      this(new ConfigFactory());
   }

   public ShuffleEngine(ConfigFactory factory) {
      this.factory = factory;
      boardManager = new BoardManager(factory);
   }

   public ConfigFactory getConfigFactory() {
      return factory;
   }

   public SpeciesManager getSpeciesManager() {
      return factory.getSpeciesManager();
   }

   public StageManager getStageManager() {
      return factory.getStageManager();
   }

   public TeamManager getTeamManager() {
      return factory.getTeamManager();
   }

   public RosterManager getRosterManager() {
      return factory.getRosterManager();
   }

   public EffectManager getEffectManager() {
      return factory.getEffectManager();
   }

   public GradingModeManager getGradingModeManager() {
      return factory.getGradingModeManager();
   }

   /**
    * Mobile mode switches to the mobile game's stages and skill numbers (stages_mobile.txt,
    * effects_mobile.txt). The desktop default is off (3DS).
    */
   public boolean isMobileMode() {
      return factory.isMobileMode();
   }

   public synchronized void setMobileMode(boolean mobile) {
      factory.getPreferencesManager().setEntry(EntryType.BOOLEAN, KEY_MOBILE_MODE, mobile);
   }

   /** @return The stage with the given id (e.g. "001", "SP_084"), or null. */
   public Stage getStage(String id) {
      return getStageManager().getStageValue(id);
   }

   /** @return The saved team for the stage, or an empty team if none is saved. */
   public Team getTeamForStage(Stage stage) {
      return getTeamManager().getTeamForStage(stage);
   }

   /**
    * @return The bundled icon resource for a species or mega name (e.g. "img/icons/531.png" for
    *         "Audino"), readable with {@link shuffle.fwk.config.ConfigPaths#openResource}; or null.
    */
   public String getIconResource(String name) {
      return factory.getIconManager(ConfigManager.class).getResourceValue(name);
   }

   /** @return The bundled starting board for the stage (an empty board if there is none). */
   public synchronized Board getDefaultBoard(Stage stage) {
      BoardManager loader = new BoardManager(factory);
      loader.loadBoardForStage(stage, true);
      return new Board(loader.getBoard());
   }

   /**
    * Solves with no time limit.
    *
    * @return Every legal move's result, best first by the request's grading mode. If the board still
    *         has pending matches, a single result with an empty move (the settled board) is returned.
    *         Empty if the simulation failed.
    */
   public List<SimulationResult> solve(SolveRequest request) {
      try {
         return solve(request, 0);
      } catch (TimeoutException e) {
         throw new IllegalStateException(e); // cannot happen without a time limit
      }
   }

   /**
    * Solves, giving up after the time limit.
    *
    * @param timeoutMillis
    *           Maximum time to wait, or 0 for no limit.
    * @throws TimeoutException
    *            If the simulation did not finish in time; it is cancelled.
    */
   public synchronized List<SimulationResult> solve(SolveRequest request, long timeoutMillis)
         throws TimeoutException {
      GradingMode mode = request.getGradingMode() != null ? request.getGradingMode()
            : getGradingModeManager().getCurrentGradingMode();
      Collector user = new Collector(request);
      boardManager.setStage(request.getStage());
      boardManager.setBoard(request.getBoard());
      SimulationCore core = new SimulationCore(user, user.getAcceptedId());

      ForkJoinTask<Void> task = pool.submit(core);
      try {
         if (timeoutMillis > 0) {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS);
         } else {
            task.get();
         }
      } catch (TimeoutException e) {
         user.discard();
         task.cancel(true);
         pool.shutdownNow();
         pool = newPool();
         throw e;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return Collections.emptyList();
      } catch (ExecutionException e) {
         return Collections.emptyList();
      }

      Collection<SimulationResult> results = user.getResults();
      if (results == null || results.isEmpty()) {
         return Collections.emptyList();
      }
      TreeSet<SimulationResult> ranked = new TreeSet<SimulationResult>(mode.getGradingMetric());
      ranked.addAll(results);
      return new ArrayList<SimulationResult>(ranked);
   }

   @Override
   public synchronized void close() {
      pool.shutdownNow();
   }

   private static ForkJoinPool newPool() {
      return new ForkJoinPool(Runtime.getRuntime().availableProcessors(), new ForkJoinPool.ForkJoinWorkerThreadFactory() {
         @Override
         public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setDaemon(true);
            t.setName("shuffle-sim-" + t.getPoolIndex());
            return t;
         }
      }, null, false);
   }

   /** Supplies one request's values to SimulationCore and collects its results. */
   private final class Collector implements SimulationUser {
      private final SolveRequest request;
      private volatile UUID acceptedId = UUID.randomUUID();
      private volatile Collection<SimulationResult> results = null;

      Collector(SolveRequest request) {
         this.request = request;
      }

      void discard() {
         acceptedId = UUID.randomUUID();
      }

      Collection<SimulationResult> getResults() {
         return results;
      }

      @Override
      public void acceptResults(Collection<SimulationResult> results) {
         this.results = results;
      }

      @Override
      public UUID getAcceptedId() {
         return acceptedId;
      }

      @Override
      public RosterManager getRosterManager() {
         return factory.getRosterManager();
      }

      @Override
      public SpeciesManager getSpeciesManager() {
         return factory.getSpeciesManager();
      }

      @Override
      public BoardManager getBoardManager() {
         return boardManager;
      }

      @Override
      public EffectManager getEffectManager() {
         return factory.getEffectManager();
      }

      @Override
      public GradingModeManager getGradingModeManager() {
         return factory.getGradingModeManager();
      }

      @Override
      public boolean isMegaAllowed() {
         return request.getTeam().getMegaSlotName() != null;
      }

      @Override
      public Team getCurrentTeam() {
         return request.getTeam();
      }

      @Override
      public int getPreferredNumFeeders() {
         return request.getNumFeeders();
      }

      @Override
      public int getMegaProgress() {
         return boardManager.getBoard().getMegaProgress();
      }

      @Override
      public int getRemainingHealth() {
         return request.getRemainingHealth();
      }

      @Override
      public int getRemainingMoves() {
         return request.getRemainingMoves();
      }

      @Override
      public Collection<Effect> getDisabledEffects() {
         return request.getDisabledEffects();
      }

      @Override
      public boolean getAttackPowerUp() {
         return request.isAttackPowerUp();
      }

      @Override
      public int getEffectThreshold() {
         return request.getEffectThreshold();
      }

      @Override
      public boolean isMobileMode() {
         return factory.isMobileMode();
      }
   }
}

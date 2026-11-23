package shuffle.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import shuffle.fwk.ShuffleController;
import shuffle.fwk.config.EntryType;
import shuffle.fwk.config.manager.BoardManager;
import shuffle.fwk.config.manager.EffectManager;
import shuffle.fwk.config.manager.GradingModeManager;
import shuffle.fwk.config.manager.RosterManager;
import shuffle.fwk.config.manager.SpeciesManager;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Effect;
import shuffle.fwk.data.Species;
import shuffle.fwk.data.Stage;
import shuffle.fwk.data.Team;
import shuffle.fwk.data.TeamImpl;
import shuffle.fwk.data.simulation.SimulationCore;
import shuffle.fwk.data.simulation.SimulationResult;
import shuffle.fwk.data.simulation.SimulationUser;
import shuffle.fwk.headless.ShuffleEngine;
import shuffle.fwk.headless.SolveRequest;

/**
 * Checks the headless {@link ShuffleEngine} gives the same answers as the desktop controller for the
 * same board, stage and team.
 * <p>
 * The simulator averages over random skyfall, so two runs never match exactly. With enough feeders
 * the set of legal moves must be identical, and each engine's best move must be (near) best for the
 * other.
 */
public class EngineParityTest {

   private static final int FEEDERS = 300;
   private static final double TOLERANCE = 0.85;
   private static final String[] STAGES = { "001", "008", "014", "015", "016", "SP_084" };

   private static ShuffleController controller;
   private static ShuffleEngine engine;

   @BeforeClass
   public static void setUp() {
      controller = new ShuffleController();
      controller.getPreferencesManager().setEntry(EntryType.BOOLEAN, "AUTO_COMPUTE", false);
      controller.getPreferencesManager().setEntry(EntryType.INTEGER, "NUM_FEEDERS", FEEDERS);
      engine = new ShuffleEngine();
   }

   @AfterClass
   public static void tearDown() {
      engine.close();
   }

   /** The bundled starting boards (often sparse, with no saved team). */
   @Test
   public void defaultBoardsMatchDesktop() {
      int compared = 0;
      for (String id : STAGES) {
         Stage stage = controller.getModel().getStageManager().getStageValue(id);
         if (stage == null) {
            continue;
         }
         controller.setCurrentStage(stage);
         controller.loadDefaultGrid();
         compareCurrent(id, stage);
         compared++;
      }
      assertTrue("no stages compared", compared >= 3);
   }

   /** Full, match-free random boards with a real team, a mega slot, blockers and frozen cells. */
   @Test
   public void randomBoardsMatchDesktop() {
      Stage stage = controller.getModel().getStageManager().getStageValue("009"); // Audino
      assertNotNull(stage);
      controller.setCurrentStage(stage);
      TeamImpl team = new TeamImpl();
      String[] names = { "Audino", "Pikachu", "Squirtle", "Charmander" };
      for (int i = 0; i < names.length; i++) {
         team.addName(names[i], (char) ('a' + i));
      }
      team.setMegaSlot("Audino");
      controller.getTeamManager().setTeamForStage(team, stage);
      SpeciesManager sm = controller.getSpeciesManager();

      Random rand = new Random(20261122L);
      for (int n = 0; n < 6; n++) {
         Board board = randomBoard(rand, sm, names);
         board.setMegaProgress(n % 2 == 0 ? 0 : 20); // alternate mega inactive / active
         controller.getBoardManager().setBoard(board);
         compareCurrent("random#" + n, stage);
      }
   }

   private static Board randomBoard(Random rand, SpeciesManager sm, String[] names) {
      Board b = new Board();
      for (int row = 1; row <= Board.NUM_ROWS; row++) {
         for (int col = 1; col <= Board.NUM_COLS; col++) {
            Species s;
            do {
               int pick = rand.nextInt(names.length + 1);
               s = pick == names.length ? (rand.nextBoolean() ? Species.WOOD : Species.METAL)
                     : sm.getSpeciesByName(names[pick]);
            } while (makesMatch(b, row, col, s));
            b.setSpeciesAt(row, col, s);
            if (!Species.FIXED_SPECIES.contains(s) && rand.nextInt(10) == 0) {
               b.setFrozenAt(row, col, true);
            }
         }
      }
      return b;
   }

   /** True if placing s at (row, col) would complete three in a row above or to the left. */
   private static boolean makesMatch(Board b, int row, int col, Species s) {
      boolean left = col > 2 && s.equals(b.getSpeciesAt(row, col - 1)) && s.equals(b.getSpeciesAt(row, col - 2));
      boolean up = row > 2 && s.equals(b.getSpeciesAt(row - 1, col)) && s.equals(b.getSpeciesAt(row - 2, col));
      return left || up;
   }

   /** Solves the controller's current state both ways and compares. */
   private static void compareCurrent(String id, Stage stage) {
      Board board = new Board(controller.getBoardManager().getBoard());
      Team team = controller.getCurrentTeam();
      List<SimulationResult> desktop = runDesktop();
      List<SimulationResult> headless = engine.solve(new SolveRequest(board, stage, team).setNumFeeders(FEEDERS)
            .setRemainingMoves(controller.getRemainingMoves()).setRemainingHealth(controller.getRemainingHealth()));
      compare(id, desktop, headless);
   }

   private static void compare(String id, List<SimulationResult> desktop, List<SimulationResult> headless) {
      assertEquals(id + ": one side found no moves", desktop.isEmpty(), headless.isEmpty());
      if (desktop.isEmpty()) {
         return;
      }
      Map<List<Integer>, SimulationResult> d = byMove(desktop);
      Map<List<Integer>, SimulationResult> h = byMove(headless);
      assertEquals(id + ": different legal moves", d.keySet(), h.keySet());

      List<Integer> hBest = headless.get(0).getMove();
      List<Integer> dBest = desktop.get(0).getMove();
      double dBestScore = desktop.get(0).getNetScore().getAverage();
      double hBestScore = headless.get(0).getNetScore().getAverage();
      // Each side's pick is nearly as good as the other's pick, as scored by that other side.
      assertNear(id + ": engine's best " + hBest + " is poor on desktop", d.get(hBest).getNetScore().getAverage(),
            dBestScore);
      assertNear(id + ": desktop's best " + dBest + " is poor in engine", h.get(dBest).getNetScore().getAverage(),
            hBestScore);
      System.out.printf("%s: %d moves, desktop best %s (%.0f), engine best %s (%.0f)%n", id, d.size(), dBest,
            dBestScore, hBest, hBestScore);
   }

   private static void assertNear(String msg, double value, double best) {
      assertTrue(msg + String.format(" (%.0f vs best %.0f)", value, best), value >= best * TOLERANCE - 1);
   }

   private static Map<List<Integer>, SimulationResult> byMove(List<SimulationResult> results) {
      Map<List<Integer>, SimulationResult> map = new HashMap<List<Integer>, SimulationResult>();
      for (SimulationResult r : results) {
         map.put(new ArrayList<Integer>(r.getMove()), r);
      }
      return map;
   }

   /** Runs SimulationCore exactly as the desktop app does, using the controller for every input. */
   private static List<SimulationResult> runDesktop() {
      DesktopUser user = new DesktopUser(controller);
      new SimulationCore(user, user.getAcceptedId()).invoke();
      assertNotNull("desktop simulation failed", user.results);
      TreeSet<SimulationResult> ranked = new TreeSet<SimulationResult>(
            controller.getModel().getCurrentGradingMode().getGradingMetric());
      ranked.addAll(user.results);
      return new ArrayList<SimulationResult>(ranked);
   }

   /** Delegates every simulation input to the desktop controller; only collects the results. */
   private static final class DesktopUser implements SimulationUser {
      private final ShuffleController c;
      private final UUID id = UUID.randomUUID();
      Collection<SimulationResult> results;

      DesktopUser(ShuffleController c) {
         this.c = c;
      }

      @Override
      public void acceptResults(Collection<SimulationResult> results) {
         this.results = results;
      }

      @Override
      public UUID getAcceptedId() {
         return id;
      }

      @Override
      public RosterManager getRosterManager() {
         return c.getRosterManager();
      }

      @Override
      public SpeciesManager getSpeciesManager() {
         return c.getSpeciesManager();
      }

      @Override
      public BoardManager getBoardManager() {
         return c.getBoardManager();
      }

      @Override
      public EffectManager getEffectManager() {
         return c.getEffectManager();
      }

      @Override
      public GradingModeManager getGradingModeManager() {
         return c.getGradingModeManager();
      }

      @Override
      public boolean isMegaAllowed() {
         return c.isMegaAllowed();
      }

      @Override
      public Team getCurrentTeam() {
         return c.getCurrentTeam();
      }

      @Override
      public int getPreferredNumFeeders() {
         return c.getPreferredNumFeeders();
      }

      @Override
      public int getMegaProgress() {
         return c.getMegaProgress();
      }

      @Override
      public int getRemainingHealth() {
         return c.getRemainingHealth();
      }

      @Override
      public int getRemainingMoves() {
         return c.getRemainingMoves();
      }

      @Override
      public Collection<Effect> getDisabledEffects() {
         Set<Effect> set = new HashSet<Effect>(c.getDisabledEffects());
         return set;
      }

      @Override
      public boolean getAttackPowerUp() {
         return c.getAttackPowerUp();
      }

      @Override
      public int getEffectThreshold() {
         return c.getEffectThreshold();
      }

      @Override
      public boolean isMobileMode() {
         return c.isMobileMode();
      }
   }
}

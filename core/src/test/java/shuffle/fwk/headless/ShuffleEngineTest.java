package shuffle.fwk.headless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import shuffle.fwk.config.ConfigPaths;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Species;
import shuffle.fwk.data.Stage;
import shuffle.fwk.data.Team;
import shuffle.fwk.data.TeamImpl;
import shuffle.fwk.data.simulation.SimulationResult;

/**
 * Runs the real simulator headlessly. Nothing here may touch java.awt / javax.swing (the classpath
 * of :core does not even contain the desktop UI).
 */
public class ShuffleEngineTest {

   private static File configDir;
   private static ShuffleEngine engine;

   @BeforeClass
   public static void setUp() throws Exception {
      // Keep user config written by the managers out of the repository.
      configDir = Files.createTempDirectory("shuffle-engine-test").toFile();
      ConfigPaths.setBaseDirectory(configDir);
      engine = new ShuffleEngine();
   }

   @AfterClass
   public static void tearDown() {
      engine.close();
      ConfigPaths.setBaseDirectory(null);
   }

   /** A team made of every non-fixed species on the board (what a stage's saved team usually is). */
   static Team teamFromBoard(Board board) {
      TeamImpl team = new TeamImpl();
      char key = 'a';
      for (int row = 1; row <= Board.NUM_ROWS; row++) {
         for (int col = 1; col <= Board.NUM_COLS; col++) {
            Species s = board.getSpeciesAt(row, col);
            if (s != null && !Species.FIXED_SPECIES.contains(s) && !team.getNames().contains(s.getName())) {
               team.addName(s.getName(), key++);
            }
         }
      }
      return team;
   }

   @Test
   public void loadsBundledData() {
      assertFalse("species", engine.getSpeciesManager().getAllSpecies().isEmpty());
      assertNotNull("stage 001", engine.getStage("001"));
      Board board = engine.getDefaultBoard(engine.getStage("001"));
      assertEquals("Happiny", board.getSpeciesAt(1, 1).getName());
      assertEquals("Happiny", board.getSpeciesAt(6, 6).getName());
   }

   @Test
   public void solvesStage001() {
      Stage stage = engine.getStage("001");
      Board board = engine.getDefaultBoard(stage);
      SolveRequest request = new SolveRequest(board, stage, teamFromBoard(board)).setNumFeeders(20);
      List<SimulationResult> results = engine.solve(request);

      assertFalse("no results", results.isEmpty());
      for (SimulationResult r : results) {
         List<Integer> move = r.getMove();
         assertEquals("move is pickup row,col + drop row,col: " + move, 4, move.size());
         for (int v : move) {
            assertTrue("coordinate out of range: " + move, v >= 1 && v <= 6);
         }
      }
      // Ranked best first according to the grading mode used.
      Comparator<SimulationResult> metric = engine.getGradingModeManager().getCurrentGradingMode()
            .getGradingMetric();
      for (int i = 1; i < results.size(); i++) {
         assertTrue("results not ranked", metric.compare(results.get(i - 1), results.get(i)) <= 0);
      }
      assertTrue("best move should score", results.get(0).getNetScore().getAverage() > 0);
   }

   @Test
   public void timesOutAndRecovers() throws Exception {
      Stage stage = engine.getStage("001");
      Board board = engine.getDefaultBoard(stage);
      Team team = teamFromBoard(board);
      try {
         engine.solve(new SolveRequest(board, stage, team).setNumFeeders(100000), 1);
         fail("expected a timeout");
      } catch (TimeoutException expected) {
         // ok
      }
      List<SimulationResult> results = engine.solve(new SolveRequest(board, stage, team).setNumFeeders(5), 60000);
      assertFalse("engine unusable after a timeout", results.isEmpty());
   }

   @Test
   public void mobileModeUsesMobileStages() {
      boolean before = engine.isMobileMode();
      try {
         engine.setMobileMode(true);
         assertTrue(engine.isMobileMode());
         Stage stage = engine.getStage("001");
         assertNotNull("mobile stage 001", stage);
         Board board = engine.getDefaultBoard(stage);
         List<SimulationResult> results = engine
               .solve(new SolveRequest(board, stage, teamFromBoard(board)).setNumFeeders(5));
         assertFalse(results.isEmpty());
      } finally {
         engine.setMobileMode(before);
      }
   }
}

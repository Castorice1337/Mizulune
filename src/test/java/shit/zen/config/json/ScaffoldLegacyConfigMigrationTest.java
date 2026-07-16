package shit.zen.config.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.Scaffold;

final class ScaffoldLegacyConfigMigrationTest {
    @Test
    void newTellyTechniqueAndSouthsideProfileRoundTripWithoutLegacyModeMigration()
            throws Exception {
        Scaffold previous = Scaffold.INSTANCE;
        try {
            Scaffold source = new Scaffold();
            source.registerSettings();
            source.technique.setValue("New Telly");
            source.mode.setValue("Normal");
            source.newTellyAlwaysUpdateRotation.setValue(false);
            source.newTellyPlaceTick.setValue(4);
            source.newTellyRotationTick.setValue(5);
            source.newTellyNoUpTelly.setValue(true);
            source.newTellySafeMode.setValue(true);
            source.newTellyTestOnGround.setValue(true);
            source.newTellyFixRotation.setValue(true);
            source.newTellySlowUpTelly.setValue(true);
            source.newTellyBlockSlotMode.setValue("Most Blocks");
            source.newTellyJumpMode.setValue("Parkour");
            source.newTellyDuplicateRotPlace.setValue(false);
            source.newTellyInteractItemBeforePlace.setValue(false);

            StringWriter output = new StringWriter();
            try (BufferedWriter writer = new BufferedWriter(output)) {
                new JsonValuesConfig(() -> List.of(source)).save(writer);
            }

            Scaffold loaded = new Scaffold();
            loaded.registerSettings();
            new JsonValuesConfig(() -> List.of(loaded)).read(
                    new BufferedReader(new StringReader(output.toString())));

            assertEquals("New Telly", loaded.technique.getValue());
            assertEquals("Normal", loaded.mode.getValue());
            assertFalse(loaded.newTellyAlwaysUpdateRotation.getValue());
            assertEquals(4, loaded.newTellyPlaceTick.getValue().intValue());
            assertEquals(5, loaded.newTellyRotationTick.getValue().intValue());
            assertTrue(loaded.newTellyNoUpTelly.getValue());
            assertTrue(loaded.newTellySafeMode.getValue());
            assertTrue(loaded.newTellyTestOnGround.getValue());
            assertTrue(loaded.newTellyFixRotation.getValue());
            assertTrue(loaded.newTellySlowUpTelly.getValue());
            assertEquals("Most Blocks", loaded.newTellyBlockSlotMode.getValue());
            assertEquals("Parkour", loaded.newTellyJumpMode.getValue());
            assertFalse(loaded.newTellyDuplicateRotPlace.getValue());
            assertFalse(loaded.newTellyInteractItemBeforePlace.getValue());
        } finally {
            Scaffold.INSTANCE = previous;
        }
    }

    @Test
    void legacyTellyModeMigratesAfterAllScaffoldValuesAreLoaded() throws Exception {
        Scaffold previous = Scaffold.INSTANCE;
        try {
            TrackingScaffold scaffold = new TrackingScaffold();
            scaffold.registerSettings();
            JsonValuesConfig config = new JsonValuesConfig(() -> List.of(scaffold));

            config.read(new BufferedReader(new StringReader("""
                    {
                      "schema": 1,
                      "modules": {
                        "scaffold": {
                          "enabled": false,
                          "values": {
                            "general": {
                              "values": {
                                "mode": "Telly",
                                "technique": "Expand",
                                "telly": false
                              }
                            }
                          }
                        }
                      }
                    }
                    """)));

            assertEquals("Telly", scaffold.modeBeforeMigration);
            assertEquals("Expand", scaffold.techniqueBeforeMigration);
            assertFalse(scaffold.tellyBeforeMigration);
            assertEquals("Normal", scaffold.modeAfterMigration);
            assertEquals("Normal", scaffold.techniqueAfterMigration);
            assertTrue(scaffold.tellyAfterMigration);
            assertEquals("Normal", scaffold.mode.getValue());
            assertEquals("Normal", scaffold.technique.getValue());
            assertTrue(scaffold.telly.getValue());

            StringWriter output = new StringWriter();
            try (BufferedWriter writer = new BufferedWriter(output)) {
                config.save(writer);
            }
            JsonObject generalValues = JsonParser.parseString(output.toString())
                    .getAsJsonObject()
                    .getAsJsonObject("modules")
                    .getAsJsonObject("scaffold")
                    .getAsJsonObject("values")
                    .getAsJsonObject("values")
                    .getAsJsonObject("general")
                    .getAsJsonObject("values");
            assertEquals("Normal", generalValues.getAsJsonObject("mode").get("value").getAsString());
            assertEquals("Normal", generalValues.getAsJsonObject("technique").get("value").getAsString());
            assertTrue(generalValues.getAsJsonObject("telly").get("value").getAsBoolean());
            assertFalse(scaffold.isEnabled());
        } finally {
            Scaffold.INSTANCE = previous;
        }
    }

    private static final class TrackingScaffold extends Scaffold {
        private String modeBeforeMigration;
        private String techniqueBeforeMigration;
        private boolean tellyBeforeMigration;
        private String modeAfterMigration;
        private String techniqueAfterMigration;
        private boolean tellyAfterMigration;

        @Override
        protected void onConfigLoaded() {
            this.modeBeforeMigration = this.mode.getValue();
            this.techniqueBeforeMigration = this.technique.getValue();
            this.tellyBeforeMigration = this.telly.getValue();
            super.onConfigLoaded();
            this.modeAfterMigration = this.mode.getValue();
            this.techniqueAfterMigration = this.technique.getValue();
            this.tellyAfterMigration = this.telly.getValue();
        }
    }
}

package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract check: the fields {@link RankedApiClient} parses out of the MCSR Ranked API must still
 * be present in the vendored official OpenAPI spec (openapi/mcsrranked.yaml). If MCSR renames or
 * drops one of these, this test flags it before the client silently returns nulls. Text-based so
 * it needs no YAML dependency.
 */
class RankedApiSpecContractTest {

    private static final String[] REQUIRED_TOKENS = {
            // MatchInfo fields the client reads
            "id:", "type:", "date:", "seed:", "seedType:", "bastionType:",
            "players:", "changes:", "timelines:", "forfeited:", "result:",
            // MatchSeed fields
            "MatchSeed:", "overworld:", "nether:", "endTowers:", "variations:",
            // player / change / timeline fields
            "uuid", "nickname", "eloRate", "change", "time", "MatchInfo:",
            // the paths the client calls
            "/users/{identifier}/matches", "/matches/{match_id}",
    };

    @Test
    void officialSpecStillContainsTheFieldsWeParse() throws IOException {
        Path spec = Path.of("openapi", "mcsrranked.yaml");
        Assumptions.assumeTrue(Files.isRegularFile(spec),
                "vendored spec openapi/mcsrranked.yaml not found (run from repo root)");
        String text = Files.readString(spec);
        for (String token : REQUIRED_TOKENS) {
            assertTrue(text.contains(token),
                    "official MCSR Ranked spec no longer mentions '" + token
                            + "' — RankedApiClient may be out of sync");
        }
    }
}

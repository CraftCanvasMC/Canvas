package io.canvasmc.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class WorldUnloadResultTest {

    @ParameterizedTest
    @EnumSource(WorldUnloadResult.class)
    public void reportsSuccessAndFailureAsComplements(final WorldUnloadResult result) {
        assertEquals(result == WorldUnloadResult.SUCCESS, result.isSuccess());
        assertEquals(result != WorldUnloadResult.SUCCESS, result.isFailure());
        assertEquals(!result.isSuccess(), result.isFailure());
    }
}

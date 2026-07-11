package io.canvasmc.canvas;

import net.minecraft.server.level.PortalTickStateTest;
import net.minecraft.world.entity.PortalMomentumTransformTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({PortalTickStateTest.class, PortalMomentumTransformTest.class})
public class PortalMomentumTestSuite {
}

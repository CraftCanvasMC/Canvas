package io.canvasmc.canvas.network;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas network tests")
@IncludeTags("Normal")
@SelectPackages("io.canvasmc.canvas.network")
@ExcludeClassNamePatterns(".*TestSuite")
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class CanvasNetworkTestSuite {
}

package io.canvasmc.canvas.configuration.test;

import com.mojang.logging.LogUtils;
import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.markers.Comment;
import java.nio.file.Path;
import org.slf4j.Logger;

public class Main {
    private static final Logger LOGGER = LogUtils.getClassLogger();

    @Comment("Testing!")
    public String test = "aaa";
    @Comment("sdfsdfsdfs!")
    public boolean a = false;
    public int num = 31;
    @Comment("Tessdfsdfsdfting sdfsd sdfjhsd s fdsdfs q  d d d dd d g g g g g g g g g g g g g g g g g gdjsdl kfjskd f sdfsldfkjsd sddd!")
    public String tessssst = "aaaaa";

    public Test vbdfhd = new Test();

    static void main(String[] args) throws InterruptedException {
        ConfigurationProvider.buildSolidConfiguration(
            Path.of("run/test/yes.yml"),
            Main::new,
            80,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added {} to config", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.info("Removed {} from config", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final Main instance) {
                    LOGGER.info("{}{}", instance.test, instance.vbdfhd.aas);
                }
            },
            "example test header"
        );
        ConfigurationProvider.buildPatchableConfiguration(
            Path.of("run/test/patch.yml"),
            Path.of("run/test/yes.yml"),
            Main::new,
            80,
            // TODO - should we just not have a proper resolver for this? the patch base is the only one
            new Resolver<>() {
                @Override
                public void onFinishLoad(final Main instance) {
                    LOGGER.info("{}{}", instance.test, instance.vbdfhd.aas);
                }
            },
            "this is a header and it's really good"
        );
        Thread.sleep(100);
    }

    public static class Test {
        @Comment("Tessdfsdfsdfting sdfsd sdfjhsd s fdsdfs q  d d d dd d g gsdfss g g gdjsdl kfjskd f sdfsldfkjsd sddd!")
        public boolean ahfsldjf = false;
        @Comment("Tessdfsdfsddkjsd sddd!")
        public boolean aas = false;
        @Comment("Ta")
        public boolean dhf29329nd = false;
    }
}

package me.dueris.canvas;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;
import org.spongepowered.configurate.yaml.internal.snakeyaml.Yaml;
import space.vectrix.ignite.applaunch.IgniteBootstrap;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class CanvasConfiguration {
    private static Path configurationPath;
    private static File jarFile;
    private static Map<String, Object> yamlData;

    public CanvasConfiguration() {

    }

    public static CanvasConfiguration init(){
        final Logger logger = LogManager.getLogger("Ignite Bootstrap");
        logger.info("Loading Canvas configuration..");
        jarFile = IgniteBootstrap.getJarLocation();
        configurationPath = Paths.get(jarFile.getPath().replace(jarFile.getName(), "canvas.yml"));
        try {
            Yaml yaml = new Yaml();

            try (FileReader reader = new FileReader(String.valueOf(configurationPath))) {
                yamlData = yaml.load(reader);

                if (yamlData != null) {

                } else {
                    System.err.println("Invalid YAML format: Unable to parse the YAML data.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Canvas configuration loaded successfully!");
        return new CanvasConfiguration();
    }

    public Object get(Object O){
        return yamlData.get(O);
    }

    public String getString(Object O){
        return get(O).toString();
    }

    public Boolean getBoolean(Object O){
        return (boolean) get(O);
    }

    public Object getOrDefault(Object O, Object T){
        try{
            if(!configurationPath.toFile().exists()) return T;
            if(yamlData.containsKey(O)){
                return get(O);
            }else{
                return T;
            }
        } catch (Exception e){
            return T;
        }
    }
}

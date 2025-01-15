package io.canvasmc.canvas;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Objects;

@me.shedaniel.autoconfig.annotation.Config(name = "canvas_server")
public class Config implements ConfigData {

	private static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
	public static Config INSTANCE = new Config();

	public static Config init() {
		AutoConfig.register(Config.class, JanksonConfigSerializer::new);
		INSTANCE = AutoConfig.getConfigHolder(Config.class).getConfig();
		Config defaulted = new Config();

		if (defaulted == null || INSTANCE == null) {
			throw new NullPointerException("Defaulted config or registered config was null!");
		}

		detectModifications(defaulted, INSTANCE, Config.class);
		return INSTANCE;
	}

	private static void detectModifications(Object obj1, Object obj2, Class<?> parentClass) {
		if (obj1 == null || obj2 == null) {
			throw new NullPointerException("One of the objects to compare is null!");
		}

		Class<?> clazz = obj1.getClass();
		Field[] fields = clazz.getDeclaredFields();

		for (Field field : fields) {
			field.setAccessible(true);
			try {
				Object value1 = field.get(obj1);
				Object value2 = field.get(obj2);

				if (!Objects.equals(value1, value2) && !isInnerClassOf(value1.getClass(), parentClass)) {
					LOGGER.info("Detected modified arg, '{}', was changed to: {}", field.getName(), value2);
				}

				if (value1 != null && value2 != null && isInnerClassOf(value1.getClass(), parentClass)) {
					detectModifications(value1, value2, value1.getClass());
				}
			} catch (IllegalAccessException e) {
				LOGGER.error("Cannot access field: {}", field.getName());
			}
		}
	}

	private static boolean isInnerClassOf(@NotNull Class<?> clazz, Class<?> parentClass) {
		return clazz.getEnclosingClass() != null && clazz.getEnclosingClass().equals(parentClass);
	}
}

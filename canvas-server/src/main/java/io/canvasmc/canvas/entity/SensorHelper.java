package io.canvasmc.canvas.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class SensorHelper {

    public static void disableSensor(LivingEntity brainedEntity, SensorType<?> sensorType) {
        if (brainedEntity.level().isClientSide()) {
            return;
        }
        Brain<?> brain = brainedEntity.getBrain();
        Sensor<?> sensor = (brain).sensors.get(sensorType);
        if (sensor != null) {
            //Disable the sensor by setting the maximum last sense time, which will make it count down almost forever
            // Removing the whole sensor could be an issue, since it may be serialized and used in a future version.

            //Instead of setting to Long.MAX_VALUE, we want to be able to recover the random offset of the sensor:
            long lastSenseTime = sensor.timeToTick;
            int senseInterval = sensor.scanRate; //Usual values: 20,40,80,200

            long maxMultipleOfSenseInterval = Long.MAX_VALUE - (Long.MAX_VALUE % senseInterval);
            maxMultipleOfSenseInterval -= senseInterval;
            maxMultipleOfSenseInterval += lastSenseTime;

            sensor.timeToTick = (maxMultipleOfSenseInterval);
        }
    }

    public static <T extends LivingEntity, U extends Sensor<T>> void enableSensor(T brainedEntity, SensorType<U> sensorType) {
        enableSensor(brainedEntity, sensorType, false);
    }

    public static <T extends LivingEntity, U extends Sensor<T>> void enableSensor(T brainedEntity, SensorType<U> sensorType, boolean extraTick) {
        if (brainedEntity.level().isClientSide()) {
            return;
        }

        Brain<?> brain = brainedEntity.getBrain();
        //noinspection unchecked
        U sensor = (U) (brain).sensors.get(sensorType);
        if (sensor != null) {
            long lastSenseTime = sensor.timeToTick;
            int senseInterval = sensor.scanRate;

            //Recover the random offset of the sensor:
            if (lastSenseTime > senseInterval) {
                lastSenseTime = lastSenseTime % senseInterval;
                if (extraTick) {
                    (sensor).timeToTick = (0L);
                    sensor.tick((ServerLevel) brainedEntity.level(), brainedEntity);
                }
            }
            sensor.timeToTick = (lastSenseTime);
        }
    }
}

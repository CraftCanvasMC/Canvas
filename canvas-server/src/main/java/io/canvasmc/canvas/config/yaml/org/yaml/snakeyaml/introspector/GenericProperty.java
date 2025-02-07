//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class GenericProperty extends Property {
    private final Type genType;
    private boolean actualClassesChecked;
    private Class<?>[] actualClasses;

    public GenericProperty(String name, Class<?> aClass, Type aType) {
        super(name, aClass);
        this.genType = aType;
        this.actualClassesChecked = aType == null;
    }

    public Class<?>[] getActualTypeArguments() {
        if (!this.actualClassesChecked) {
            if (this.genType instanceof final ParameterizedType parameterizedType) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    this.actualClasses = new Class[actualTypeArguments.length];

                    for (int i = 0; i < actualTypeArguments.length; ++i) {
                        if (actualTypeArguments[i] instanceof Class) {
                            this.actualClasses[i] = (Class) actualTypeArguments[i];
                        } else if (actualTypeArguments[i] instanceof ParameterizedType) {
                            this.actualClasses[i] = (Class) ((ParameterizedType) actualTypeArguments[i]).getRawType();
                        } else {
                            if (!(actualTypeArguments[i] instanceof GenericArrayType)) {
                                this.actualClasses = null;
                                break;
                            }

                            Type componentType = ((GenericArrayType) actualTypeArguments[i]).getGenericComponentType();
                            if (!(componentType instanceof Class)) {
                                this.actualClasses = null;
                                break;
                            }

                            this.actualClasses[i] = Array.newInstance((Class) componentType, 0).getClass();
                        }
                    }
                }
            } else if (this.genType instanceof GenericArrayType) {
                Type componentType = ((GenericArrayType) this.genType).getGenericComponentType();
                if (componentType instanceof Class) {
                    this.actualClasses = new Class[]{(Class) componentType};
                }
            } else if (this.genType instanceof Class) {
                Class<?> classType = (Class) this.genType;
                if (classType.isArray()) {
                    this.actualClasses = new Class[1];
                    this.actualClasses[0] = this.getType().getComponentType();
                }
            }

            this.actualClassesChecked = true;
        }

        return this.actualClasses;
    }
}

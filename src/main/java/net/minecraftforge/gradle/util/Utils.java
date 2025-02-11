package net.minecraftforge.gradle.util;

import org.gradle.api.provider.Property;

public class Utils {
    public static<T> void setProperty(Property<T> property, T value) {
        property.convention(value);
        property.set(value);
    }
}

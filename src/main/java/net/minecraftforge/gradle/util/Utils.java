package net.minecraftforge.gradle.util;

import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.provider.Property;

import java.io.File;
import java.util.Set;

public class Utils {
    public static<T> void setProperty(Property<T> property, T value) {
        property.convention(value);
        property.set(value);
    }

    public static Set<File> getFilteredDependencyFiles(ResolvableDependencies incoming, String group, String name, String version) {
        return incoming.artifactView(viewConfiguration -> viewConfiguration.componentFilter(componentIdentifier -> {
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) componentIdentifier;
                return moduleComponentIdentifier.getGroup().equals(group)
                        && moduleComponentIdentifier.getModule().equals(name)
                        && moduleComponentIdentifier.getVersion().equals(version);
            }
            return false;
        })).getFiles().getFiles();
    }
}

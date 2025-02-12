package net.minecraftforge.gradle.util;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedFilesCollectingVisitor;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;

import java.io.File;
import java.util.Set;

public class Utils {
    public static<T> void setProperty(Property<T> property, T value) {
        property.convention(value);
        property.set(value);
    }

    public static Set<File> getFiles(LenientConfiguration lenientConfiguration, Spec<? super Dependency> dependencySpec) throws ResolveException {
        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor();
        lenientConfiguration.select(dependencySpec).visitArtifacts(visitor, false);
        this.resolutionHost.rethrowFailure("files", visitor.getFailures());
        return visitor.getFiles();
    }
}

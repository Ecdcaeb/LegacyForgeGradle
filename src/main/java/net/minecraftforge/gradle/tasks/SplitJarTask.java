/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 * Copyright (C) 2020-2023 anatawa12 and other contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.NotNull;

public class SplitJarTask extends CachedTask implements PatternFilterable
{
    @InputFile
    private Object     inJar;

    @Input
    private final PatternSet pattern = new PatternSet();

    @Cached
    @OutputFile
    private Object     outFirst;

    @Cached
    @OutputFile
    private Object     outSecond;

    @TaskAction
    public void doTask() throws IOException
    {
        // get the spec
        final Spec<FileTreeElement> spec = pattern.getAsSpec();

        File input = getInJar();

        File out1 = getOutFirst();
        File out2 = getOutSecond();

        out1.getParentFile().mkdirs();
        out2.getParentFile().mkdirs();

        // begin reading jar
        try (JarOutputStream zout1 = new JarOutputStream(Files.newOutputStream(out1.toPath()));
             JarOutputStream zout2 = new JarOutputStream(Files.newOutputStream(out2.toPath())))
        {

            getProject().zipTree(input).visit(new FileVisitor() {

                @Override
                public void visitDir(@NotNull FileVisitDetails details)
                {
                    // ignore directories
                }

                @Override
                public void visitFile(@NotNull FileVisitDetails details)
                {
                    JarEntry entry = new JarEntry(details.getPath());
                    entry.setSize(details.getSize());
                    entry.setTime(details.getLastModified());

                    try
                    {
                        if (spec.isSatisfiedBy(details))
                        {
                            zout1.putNextEntry(entry);
                            details.copyTo(zout1);
                            zout1.closeEntry();
                        }
                        else
                        {
                            zout2.putNextEntry(entry);
                            details.copyTo(zout2);
                            zout2.closeEntry();
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @InputFiles
    public Provider<FileCollection> getInputFiles() {
        return getProject().provider(() -> getProject().zipTree(getInJar()).matching(pattern));
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutFirst()
    {
        return getProject().file(outFirst);
    }

    public void setOutFirst(Object outFirst)
    {
        this.outFirst = outFirst;
    }

    public File getOutSecond()
    {
        return getProject().file(outSecond);
    }

    public void setOutSecond(Object outSecond)
    {
        this.outSecond = outSecond;
    }

    @NotNull
    @Override
    public PatternFilterable exclude(@NotNull String... arg0)
    {
        return pattern.exclude(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable exclude(@NotNull Iterable<String> arg0)
    {
        return pattern.exclude(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable exclude(@NotNull Spec<FileTreeElement> arg0)
    {
        return pattern.exclude(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable exclude(@NotNull Closure arg0)
    {
        return pattern.exclude(arg0);
    }

    @NotNull
    @Internal
    @Override
    public Set<String> getExcludes()
    {
        return pattern.getExcludes();
    }

    @NotNull
    @Internal
    @Override
    public Set<String> getIncludes()
    {
        return pattern.getIncludes();
    }

    @NotNull
    @Override
    public PatternFilterable include(@NotNull String... arg0)
    {
        return pattern.include(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable include(@NotNull Iterable<String> arg0)
    {
        return pattern.include(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable include(@NotNull Spec<FileTreeElement> arg0)
    {
        return pattern.include(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable include(@NotNull Closure arg0)
    {
        return pattern.include(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable setExcludes(@NotNull Iterable<String> arg0)
    {
        return pattern.setExcludes(arg0);
    }

    @NotNull
    @Override
    public PatternFilterable setIncludes(@NotNull Iterable<String> arg0)
    {
        return pattern.setIncludes(arg0);
    }
}

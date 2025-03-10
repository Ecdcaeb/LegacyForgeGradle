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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.io.FileWriteMode;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.ThrowableUtil;
import net.minecraftforge.gradle.util.patching.ContextualPatch;
import net.minecraftforge.gradle.util.patching.ContextualPatch.PatchStatus;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.cloudbees.diff.PatchException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;

public class PatchSourcesTask extends AbstractEditJarTask
{
    /*
     * TODO: optimization plans
     * 1) doStufBefore> read all the patch files into a map as strings stored by relative path.
     * 2) doStufBefore> Create a threaded Executor to patch the files
     * 3) AsRead > kick off the patching threads
     * 4) ????
     * 5) profit from multithreaded oatching. Its CPU bound anyways.
     */
    private int                    maxFuzz       = 0;
    private int                    patchStrip    = 3;
    private boolean                makeRejects   = true;
    private boolean                failOnError   = false;
    private Object                 patches;
    private List<Object>           injects       = Lists.newArrayList();

    // stateful pieces of this task
    private ContextProvider        context;
    private final ArrayList<PatchedFile> loadedPatches = Lists.newArrayList();

    @Override
    public void doStuffBefore() throws IOException
    {
        getLogger().info("Reading patches");

        // create context provider
        context = new ContextProvider(null, patchStrip); // add in the map later.

        // collect patchFiles and add them to the listing
        File patchThingy = getPatches(); // cached for the if statements
        final int fuzz = getMaxFuzz();

        if (patchThingy.isDirectory())
        {
            for (File f : getProject().fileTree(getPatches()))
            {
                if (!f.exists() || f.isDirectory() || !f.getName().endsWith("patch"))
                {
                    continue;
                }

                loadedPatches.add(new PatchedFile(f, context, fuzz));
            }
        }
        else if (patchThingy.getName().endsWith(".jar") || patchThingy.getName().endsWith(".zip"))
        {
            // no rejects from a jar
            makeRejects = false;

            getProject().zipTree(patchThingy).visit(new FileVisitor() {

                @Override
                public void visitDir(@NotNull FileVisitDetails arg0)
                {
                    // nope.
                }

                @Override
                public void visitFile(@NotNull FileVisitDetails details)
                {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    details.copyTo(stream);
                    String file = new String(stream.toByteArray(), Constants.CHARSET);
                    loadedPatches.add(new PatchedFile(file, context, fuzz));
                }

            });
        }
        else
        {
            throw new GradleConfigurationException("Patches (" + patchThingy.getPath() + ") is not a valid type! only zips, jars, and directories are allowed.");
        }
    }

    @Override
    public void doStuffMiddle(final Map<String, String> sourceMap, final Map<String, byte[]> resourceMap) throws Exception
    {
        // Inject injects
        getLogger().info("Injecting injects (sources and resources)");
        this.inject(getInjects(), sourceMap, resourceMap);

        // fix the context provider
        context.fileMap = sourceMap;

        // apply patches
        getLogger().info("Applying patches");
        applyPatches();
    }

    private void inject(FileCollection injects, final Map<String, String> sourceMap, final Map<String, byte[]> resourceMap) throws IOException
    {
        FileVisitor visitor = new FileVisitor() {

            @Override
            public void visitDir(@NotNull FileVisitDetails arg0)
            {
                // nope.
            }

            @Override
            public void visitFile(FileVisitDetails details)
            {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                details.copyTo(stream);
                // BAOS dont need to be clsoed.

                byte[] array = stream.toByteArray();
                String path = details.getRelativePath().getPathString().replace('\\', '/');

                if (details.getName().endsWith(".java"))
                {
                    sourceMap.put(path, new String(array, Constants.CHARSET));
                }
                else
                {
                    resourceMap.put(path, array);
                }
            }

        };

        for (File inject : injects)
        {
            if (inject.isDirectory())
            {
                getProject().fileTree(inject).visit(visitor);
            }
            else if (inject.getName().endsWith(".jar") || inject.getName().endsWith(".zip"))
            {
                getProject().zipTree(inject).visit(visitor);
            }
            else if (inject.getName().endsWith(".java"))
            {
                sourceMap.put(inject.getName(), Files.asCharSource(inject, Constants.CHARSET).read());
            }
            else
            {
                resourceMap.put(inject.getName(), Files.toByteArray(inject));
            }
        }
    }

    private void applyPatches() throws IOException, PatchException
    {
        boolean fuzzed = false;
        Throwable failure = null;

        for (PatchedFile patch : loadedPatches)
        {
            List<ContextualPatch.PatchReport> errors = patch.patch.patch(false);
            for (ContextualPatch.PatchReport report : errors)
            {
                // catch failed patches
                if (!report.getStatus().isSuccess())
                {
                    StringBuilder rejectBuilder = new StringBuilder();

                    getLogger().log(LogLevel.ERROR, "Patching failed: {} {}", context.strip(report.getTarget()), report.getFailure().getMessage());
                    // now spit the hunks
                    int failed = 0;
                    for (ContextualPatch.HunkReport hunk : report.getHunks())
                    {
                        // catch the failed hunks
                        if (!hunk.getStatus().isSuccess())
                        {
                            failed++;
                            getLogger().error("  {}: {} @ {}", hunk.getHunkID(), hunk.getFailure() != null ? hunk.getFailure().getMessage() : "", hunk.getIndex());

                            if (makeRejects)
                            {
                                rejectBuilder.append(String.format("++++ REJECTED PATCH %d\n", hunk.getHunkID()));
                                rejectBuilder.append(Joiner.on('\n').join(hunk.hunk.lines));
                                rejectBuilder.append("\n++++ END PATCH\n");
                            }
                        }
                        else if (hunk.getStatus() == PatchStatus.Fuzzed)
                        {
                            getLogger().info("[catch failed patches]  {} fuzzed {}!", hunk.getHunkID(), hunk.getFuzz());
                        }
                    }

                    getLogger().log(LogLevel.ERROR, "  {}/{} failed", failed, report.getHunks().size());

                    if (makeRejects)
                    {
                        File reject = patch.makeRejectFile();
                        if (reject.exists())
                        {
                            reject.delete();
                        }
                        Files.asCharSink(reject, Charsets.UTF_8, FileWriteMode.APPEND).write(rejectBuilder.toString());
                        getLogger().log(LogLevel.ERROR, "  Rejects written to {}", reject.getAbsolutePath());
                    }

                    if (failure == null)
                        failure = report.getFailure();
                }
                // catch fuzzed patches
                else if (report.getStatus() == ContextualPatch.PatchStatus.Fuzzed)
                {
                    getLogger().log(LogLevel.INFO, "Patching fuzzed: {}", context.strip(report.getTarget()));

                    // set the boolean for later use
                    fuzzed = true;

                    // now spit the hunks
                    for (ContextualPatch.HunkReport hunk : report.getHunks())
                    {
                        // catch the failed hunks
                        if (hunk.getStatus() == PatchStatus.Fuzzed)
                        {
                            getLogger().info("[catch fuzzed patches]  {} fuzzed {}!", hunk.getHunkID(), hunk.getFuzz());
                        }
                    }

                    if (failure == null)
                        failure = report.getFailure();
                }

                // sucesful patches
                else
                {
                    getLogger().info("Patch succeeded: {}", context.strip(report.getTarget()));
                }
            }
        }

        if (failure != null && failOnError)
        {
            ThrowableUtil.propagate(failure);
        }

        if (fuzzed)
        {
            getLogger().lifecycle("Patches Fuzzed!");
        }
    }

    // START GETTERS/SETTERS HERE

    @Input
    public int getMaxFuzz()
    {
        return maxFuzz;
    }

    public void setMaxFuzz(int maxFuzz)
    {
        this.maxFuzz = maxFuzz;
    }

    @Input
    public int getPatchStrip()
    {
        return patchStrip;
    }

    public void setPatchStrip(int patchStrip)
    {
        this.patchStrip = patchStrip;
    }

    @Input
    public boolean isMakeRejects()
    {
        return makeRejects;
    }

    public void setMakeRejects(boolean makeRejects)
    {
        this.makeRejects = makeRejects;
    }

    @Input
    public boolean isFailOnError()
    {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError)
    {
        this.failOnError = failOnError;
    }

    @Optional
    @InputDirectory
    public File getPatchesDir()
    {
        File patch = getPatches();
        if (patch.isDirectory())
            return getPatches();
        else
            return null;
    }

    @Optional
    @InputFile
    public File getPatchesZip()
    {
        File patch = getPatches();
        if (patch.isDirectory())
            return null;
        else
            return getPatches();
    }

    @Internal
    public File getPatches()
    {
        return getProject().file(patches);
    }

    public void setPatches(Object patchDir)
    {
        this.patches = patchDir;
    }

    @InputFiles
    public FileCollection getInjects()
    {
        return getProject().files(injects);
    }

    public void setInjects(List<Object> injects)
    {
        this.injects = injects;
    }

    public void addInject(Object obj)
    {
        injects.add(obj);
    }

    // OVERRIDEN GARBAGE

    //@formatter:off
    @Override protected boolean storeJarInRam() { return true; }
    @Override public String asRead(String fileName, String file) { return file; }
    @Override public void doStuffAfter() { }
    //@formatter:on

    // START INNER CLASSES

    private static class ContextProvider implements ContextualPatch.IContextProvider
    {
        public Map<String, String> fileMap;

        private final int          stripAmmount;

        public ContextProvider(Map<String, String> fileMap, int stripAmmount)
        {
            this.fileMap = fileMap;
            this.stripAmmount = stripAmmount;
        }

        public String strip(String target)
        {
            target = target.replace('\\', '/');
            int index = 0;
            for (int x = 0; x < stripAmmount; x++)
            {
                index = target.indexOf('/', index) + 1;
            }
            return target.substring(index);
        }

        @Override
        public List<String> getData(String target)
        {
            target = strip(target);

            if (fileMap.containsKey(target))
            {
                String[] lines = fileMap.get(target).split("\r\n|\r|\n");
                List<String> ret = new ArrayList<>();
                Collections.addAll(ret, lines);
                return ret;
            }

            return null;
        }

        @Override
        public void setData(String target, List<String> data)
        {
            target = strip(target);
            fileMap.put(target, Joiner.on(Constants.NEWLINE).join(data));
        }
    }

    private static class PatchedFile
    {
        public final File            fileToPatch;
        public final ContextualPatch patch;

        public PatchedFile(File file, ContextProvider provider, int maxFuzz) throws IOException
        {
            this.fileToPatch = file;
            this.patch = ContextualPatch.create(Files.asCharSource(file, Charset.defaultCharset()).read(), provider).setAccessC14N(true).setMaxFuzz(maxFuzz);
        }

        public PatchedFile(String file, ContextProvider provider, int maxFuzz)
        {
            this.fileToPatch = null;
            this.patch = ContextualPatch.create(file, provider).setAccessC14N(true).setMaxFuzz(maxFuzz);
        }

        public File makeRejectFile()
        {
            if (fileToPatch == null)
            {
                return null;
            }

            return new File(fileToPatch.getParentFile(), fileToPatch.getName() + ".rej");
        }
    }
}

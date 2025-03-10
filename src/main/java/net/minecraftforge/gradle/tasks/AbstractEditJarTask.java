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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public abstract class AbstractEditJarTask extends CachedTask
{
    private Object inJar;
    private Object outJar;

    protected File resolvedInJar;
    protected File resolvedOutJar;

    @TaskAction
    public void doTask() throws Throwable
    {
        resolvedInJar = getInJar();
        resolvedOutJar = getOutJar();

        doStuffBefore();

        if (storeJarInRam())
        {
            getLogger().debug("Reading jar: {}", resolvedInJar);

            Map<String, String> sourceMap = Maps.newHashMap();
            Map<String, byte[]> resourceMap = Maps.newHashMap();

            readAndStoreJarInRam(resolvedInJar, sourceMap, resourceMap);

            doStuffMiddle(sourceMap, resourceMap);

            saveJar(resolvedOutJar, sourceMap, resourceMap);

            getLogger().debug("Saving jar: {}", resolvedOutJar);
        }
        else
        {
            copyJar(resolvedInJar, resolvedOutJar);
        }

        doStuffAfter();
    }

    /**
     * Do Stuff before the jar is read
     * @throws Exception for convenience
     */
    public abstract void doStuffBefore() throws Exception;

    /**
     * Called as the .java files of the jar are read from the jar
     * @param name name of the current entry
     * @param file current contents of the entry
     * @return new contents of the file
     * @throws Exception as a convenience for any potential exceptions thrown in this method
     */
    public abstract String asRead(String name, String file) throws Exception;

    /**
     * Do Stuff after the jar is read, but before it is written.
     * @param sourceMap name-&gt;contents for all java files in the jar
     * @param resourceMap name-&gt;contents for everything else
     * @throws Exception for convenience
     */
    public abstract void doStuffMiddle(Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception;

    /**
     * Do Stuff after the jar is Written
     * @throws Exception for convenience
     */
    public abstract void doStuffAfter() throws Exception;

    /**
     * Called immediately after every file is written to the jar.
     * @param jarOut The jar output stream
     * @param entryName The path to the file in the jar
     * @throws IOException IOException
     */
    protected void postWriteEntry(JarOutputStream jarOut, String entryName) throws IOException {}

    /**
     * Called after all entries have been written to the jar. This can be useful for adding any additional entries
     * @param jarOut The jar output stream
     * @throws IOException IOException
     */
    protected void postWrite(JarOutputStream jarOut) throws IOException {}

    /**
     * Whether to store the contents of the jar in RAM.
     * If this returns false, then the doStuffMiddle method is not called.
     * @return store jar in ram
     */
    protected abstract boolean storeJarInRam();

    final void readAndStoreJarInRam(File jar, Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception
    {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar.toPath())))
        {
            ZipEntry entry;
            String fileStr;

            while ((entry = zin.getNextEntry()) != null)
            {
                // ignore META-INF, it shouldn't be here. If it is we remove it from the output jar.
                if (entry.getName().contains("META-INF"))
                {
                    continue;
                }

                // resources or directories.
                if (!isSourceFile(entry))
                {
                    resourceMap.put(entry.getName(), ByteStreams.toByteArray(zin));
                }
                else
                {
                    // source!
                    fileStr = new String(ByteStreams.toByteArray(zin), Constants.CHARSET);

                    fileStr = asRead(entry.getName(), fileStr);

                    sourceMap.put(entry.getName(), fileStr);
                }
            }
        }
    }

    protected void saveJar(File output, Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws IOException
    {
        output.getParentFile().mkdirs();

        try (JarOutputStream zout = new JarOutputStream(Files.newOutputStream(output.toPath())))
        {

            // write in resources
            for (Map.Entry<String, byte[]> entry : resourceMap.entrySet())
            {
                zout.putNextEntry(new JarEntry(entry.getKey()));
                zout.write(entry.getValue());
                zout.closeEntry();
                postWriteEntry(zout, entry.getKey());
            }

            // write in sources
            for (Map.Entry<String, String> entry : sourceMap.entrySet())
            {
                zout.putNextEntry(new JarEntry(entry.getKey()));
                zout.write(entry.getValue().getBytes());
                zout.closeEntry();
                postWriteEntry(zout, entry.getKey());
            }

            postWrite(zout);
        }
    }

    /**
     * Checks whether the given entry should be treated as a source file
     * <p>
     * This can be overridden to change the types your task treats as source files
     *
     * @param entry the entry to check
     * @return true if entry is a source file
     */
    protected boolean isSourceFile(ZipEntry entry) {
        if (entry.isDirectory())
            return false;
        String extension = FilenameUtils.getExtension(entry.getName());
        switch (extension) {
            case "java":
            case "scala":
            case "groovy":
            case "kt":
                return true;
        }
        return false;
    }

    private void copyJar(File input, File output) throws Exception
    {
        // begin reading jar
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(input.toPath()));
             JarOutputStream zout = new JarOutputStream(Files.newOutputStream(output.toPath())))
        {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null)
            {
                // no META or dirs. wel take care of dirs later.
                if (entry.getName().contains("META-INF"))
                {
                    continue;
                }

                // resources or directories.
                try
                {
                    if (!isSourceFile(entry))
                    {
                        zout.putNextEntry(new JarEntry(entry));
                        ByteStreams.copy(zin, zout);
                        zout.closeEntry();
                        postWriteEntry(zout, entry.getName());
                    }
                    else
                    {
                        // source
                        zout.putNextEntry(new JarEntry(entry.getName()));
                        zout.write(asRead(entry.getName(), new String(ByteStreams.toByteArray(zin), Constants.CHARSET)).getBytes());
                        zout.closeEntry();
                        postWriteEntry(zout, entry.getName());
                    }
                }
                catch (ZipException ex)
                {
                    getLogger().debug("Duplicate zip entry {} in {} writing {}", entry.getName(), input, output);
                }
            }

            postWrite(zout);
        }
    }

    @InputFile
    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    @OutputFile
    @Cached
    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }
}

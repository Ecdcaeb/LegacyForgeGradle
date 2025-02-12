/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.StandardOutputListener;

import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLogListener implements StandardOutputListener, BuildListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLogListener.class);

    private BufferedWriter writer;
    
    public FileLogListener(File file)
    {

        try
        {
            if (file.exists())
                if (!file.delete()) LOGGER.error("Fail to delete file {}", file.getAbsoluteFile());
            else
                if (!file.getParentFile().mkdirs()) LOGGER.error("Fail to create dir {}", file.getParentFile());

            if (!file.createNewFile()) LOGGER.error("Fail to create file {}", file.getAbsoluteFile());
            
            writer = Files.newWriter(file, Charset.defaultCharset());
        }
        catch (IOException e)
        {
            LOGGER.error("Error when processing file ", e);
        }
    }
    
    @Override
    public void projectsLoaded(@NotNull Gradle arg0) {}

    @Override
    public void onOutput(CharSequence arg0)
    {
        try
        {
            writer.write(arg0.toString());
        }
        catch (IOException e)
        {
            // to stop recursion....
        }
    }

    @Override
    public void buildFinished(@NotNull BuildResult arg0)
    {
        try
        {
            writer.close();
        }
        catch (IOException e)
        {
            LOGGER.error("Unable to close the File Writer", e);
        }
    }

    @Override
    public void projectsEvaluated(@NotNull Gradle arg0) {}  // nothing
    
    @Override
    public void settingsEvaluated(@NotNull Settings arg0) {} // nothing

}

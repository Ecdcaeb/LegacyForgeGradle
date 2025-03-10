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
package net.minecraftforge.gradle.util.caching;

import java.io.File;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.ThrowableUtil;

import org.gradle.api.Action;
import org.gradle.api.Task;

import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;

public class WriteCacheAction implements Action<Task>
{
    private final Annotated annot;
    private final List<Annotated> inputs;

    public WriteCacheAction(Annotated annot, List<Annotated> inputs)
    {
        this.annot = annot;
        this.inputs = inputs;
    }

    @Override
    public void execute(@NotNull Task task)
    {
        execute((ICachableTask) task);
    }

    public void execute(ICachableTask task)
    {
        if (!task.doesCache())
            return;

        try
        {
            File outFile = task.getProject().file(annot.getValue(task));
            if (outFile.exists())
            {
                File hashFile = CacheUtil.getHashFile(outFile);
                Files.asCharSink(hashFile, Constants.CHARSET).write(CacheUtil.getHashes(annot, inputs, task));
            }
        }
        // error? spit it and do the task.
        catch (Throwable e)
        {
            ThrowableUtil.propagate(e);
        }
    }

}

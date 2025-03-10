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
package net.minecraftforge.gradle.versions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import net.minecraftforge.gradle.user.tweakers.ClientTweaker;
import net.minecraftforge.gradle.user.tweakers.TweakerExtension;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ExtensionMcpMappingTest
{
    private Project          testProject;
    private TweakerExtension ext;

    @Before
    public void setupProject()
    {
        this.testProject = ProjectBuilder.builder().build();
        assertNotNull(this.testProject);
        this.testProject.apply(ImmutableMap.of("plugin", ClientTweaker.class));

        this.ext = this.testProject.getExtensions().findByType(TweakerExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
        this.ext.setSuppressVersionTest(true);
        
        this.ext.setTweakClass("some.thing.other"); // to ignore any issues regarding this.
    }
    private static final String VERSION_17 = "1.7.10";
    private static final String VERSION_18 = "1.8";
    private static final String VERSION_19 = "1.9";

    @Test
    public void testValidSnapshot17()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_20140925");
        assertEquals("snapshot", this.ext.getMappingsChannel());
        assertEquals("20140925", this.ext.getMappingsVersion());
    }

    @Test
    public void testValidStable17()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_12");
        assertEquals("stable", this.ext.getMappingsChannel());
        assertEquals("12", this.ext.getMappingsVersion());
    }

    @Test
    public void testValidSnapshot18()
    {
        this.ext.setVersion(VERSION_18);
        this.ext.setMappings("snapshot_20150218");
        assertEquals("snapshot", this.ext.getMappingsChannel());
        assertEquals("20150218", this.ext.getMappingsVersion());
    }

    @Test
    public void testValidStable18()
    {
        this.ext.setVersion(VERSION_18);
        this.ext.setMappings("stable_15");
        assertEquals("stable", this.ext.getMappingsChannel());
        assertEquals("15", this.ext.getMappingsVersion());
    }

    @Test
    public void testValidSnapshot19()
    {
        this.ext.setVersion(VERSION_19);
        this.ext.setMappings("snapshot_20160301");
        assertEquals("snapshot", this.ext.getMappingsChannel());
        assertEquals("20160301", this.ext.getMappingsVersion());
    }

    @Test
    public void testSnapshotNodoc()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_nodoc_20140925");
        assertEquals("snapshot", this.ext.getMappingsChannelNoSubtype());
        assertEquals("20140925", this.ext.getMappingsVersion());
    }

    @Test
    public void testStableNodoc()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_nodoc_12");
        assertEquals("stable", this.ext.getMappingsChannelNoSubtype());
        assertEquals("12", this.ext.getMappingsVersion());
    }

    @Test
    public void testOrdering()
    {
        this.ext.setMappings("snapshot_20140925");
        this.ext.setVersion(VERSION_17);
        assertEquals("snapshot", this.ext.getMappingsChannel());
        assertEquals("20140925", this.ext.getMappingsVersion());
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidSnapshot()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_15");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidStable()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_20140925");
    }
    
    @Test(expected = GradleConfigurationException.class)
    public void testInvalidCustom()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("abrar_blahblah");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testValidCustom()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("abrar_custom");
        assertEquals("abrar", this.ext.getMappingsChannel());
        assertEquals("custom", this.ext.getMappingsVersion());
    }
}

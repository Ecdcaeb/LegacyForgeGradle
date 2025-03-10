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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.gradle.util.mcp.JavadocAdder;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;

public class RemapSources extends AbstractEditJarTask
{
    private DelayedFile               methodsCsv;
    private DelayedFile               fieldsCsv;
    private DelayedFile               paramsCsv;
    private boolean                   addsJavadocs = true;

    private final Map<String, String> methods      = Maps.newHashMap();
    private final Map<String, String> methodDocs   = Maps.newHashMap();
    private final Map<String, String> fields       = Maps.newHashMap();
    private final Map<String, String> fieldDocs    = Maps.newHashMap();
    private final Map<String, String> params       = Maps.newHashMap();


    private static final Pattern      SRG_FINDER             = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_\\b");
    private static final Pattern      METHOD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {4})+|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern      FIELD_JAVADOC_PATTERN  = Pattern.compile("^(?<indent>(?: {4})+|\\t+)(?!return)(?:\\w+\\s+)*(?:\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");

    @Override
    public void doStuffBefore() throws Exception
    {
        // read CSV files
        CSVReader reader = Constants.getReader(getMethodsCsv());
        for (String[] s : reader.readAll())
        {
            methods.put(s[0], s[1]);
            if (!s[3].isEmpty() && addsJavadocs)
                methodDocs.put(s[0], s[3]);
        }

        reader = Constants.getReader(getFieldsCsv());
        for (String[] s : reader.readAll())
        {
            fields.put(s[0], s[1]);
            if (!s[3].isEmpty() && addsJavadocs)
                fieldDocs.put(s[0], s[3]);
        }

        reader = Constants.getReader(getParamsCsv());
        for (String[] s : reader.readAll())
        {
            params.put(s[0], s[1]);
        }
    }

    @Override
    protected boolean storeJarInRam()
    {
        return false;
    }

    @Override
    public String asRead(String name, String text)
    {
        ArrayList<String> newLines = new ArrayList<>();
        for (String line : Constants.lines(text))
        {
            // basically all this code is to find the javadocs for a field before replacing it.
            // if we aren't doing javadocs... screw dat.
            if (addsJavadocs)
            {
                injectJavadoc(newLines, line, methodDocs::get, fieldDocs::get);
            }
            newLines.add(replaceInLine(line));
        }

        return Joiner.on(Constants.NEWLINE).join(newLines);
    }

    /**
     * Injects a javadoc into the given list of lines, if the given line is a
     * method or field declaration.
     *
     * @param lines The current file content (to be modified by this method)
     * @param line The line that was just read (will not be in the list)
     * @param methodFunc A function that takes a method SRG id and returns its javadoc
     * @param fieldFunc A function that takes a field SRG id and returns its javadoc
     */
    public static void injectJavadoc(List<String> lines, String line, Function<String, String> methodFunc, Function<String, String> fieldFunc)
    {
        // methods
        Matcher matcher = METHOD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find())
        {
            String javadoc = methodFunc.apply(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
            {
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));
            }

            // worked, so return and don't try the fields.
            return;
        }

        // fields
        matcher = FIELD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find())
        {
            String javadoc = fieldFunc.apply(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
            {
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, false));
            }
        }
    }

    /** Inserts the given javadoc line into the list of lines before any annotations */
    private static void insertAboveAnnotations(List<String> list, String line)
    {
        int back = 0;
        while (list.get(list.size() - 1 - back).trim().startsWith("@"))
        {
            back++;
        }
        list.add(list.size() - back, line);
    }

    private String replaceInLine(String line)
    {
        // FAR all methods
        StringBuffer buf = new StringBuffer();
        Matcher matcher = SRG_FINDER.matcher(line);
        while (matcher.find())
        {
            String find = matcher.group();

            if (find.startsWith("p_"))
                find = params.get(find);
            else if (find.startsWith("func_"))
                find = methods.get(find);
            else if (find.startsWith("field_"))
                find = fields.get(find);

            if (find == null)
                find = matcher.group();

            matcher.appendReplacement(buf, find);
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    @InputFile
    public File getMethodsCsv()
    {
        return methodsCsv.call();
    }

    public void setMethodsCsv(DelayedFile methodsCsv)
    {
        this.methodsCsv = methodsCsv;
    }

    @InputFile
    public File getFieldsCsv()
    {
        return fieldsCsv.call();
    }

    public void setFieldsCsv(DelayedFile fieldsCsv)
    {
        this.fieldsCsv = fieldsCsv;
    }

    @InputFile
    public File getParamsCsv()
    {
        return paramsCsv.call();
    }

    public void setParamsCsv(DelayedFile paramsCsv)
    {
        this.paramsCsv = paramsCsv;
    }

    @Input
    public boolean getAddsJavadocs()
    {
        return addsJavadocs;
    }

    public boolean addsJavadocs()
    {
        return addsJavadocs;
    }

    public void setAddsJavadocs(boolean javadoc)
    {
        this.addsJavadocs = javadoc;
    }

    @Override
    public void doStuffMiddle(Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception { }

    @Override
    public void doStuffAfter() throws Exception { }

}

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
package net.minecraftforge.gradle.util.mcp;

import java.util.*;

import net.minecraftforge.gradle.common.Constants;

import com.google.common.base.Splitter;

public final class JavadocAdder
{
    private JavadocAdder() { /* no constructing */ } 
    
    /**
     * Converts a raw javadoc string into a nicely formatted, indented, and wrapped string.
     * @param indent the indent to be inserted before every line.
     * @param javadoc The javadoc string to be processed
     * @param isMethod If this javadoc is for a method or a field
     * @return A fully formatted javadoc comment string complete with comment characters and newlines.
     */
    public static String buildJavadoc(String indent, String javadoc, boolean isMethod)
    {
        StringBuilder builder = new StringBuilder();
        
        // split and wrap.
        List<String> list = new LinkedList<>();
        for (String line : Splitter.on("\\n").splitToList(javadoc))
        {
            list.addAll(wrapText(line, 120 - (indent.length() + 3)));
        }

        if (list.size() > 1 || isMethod)
        {
            builder.append(indent);
            builder.append("/**");
            builder.append(Constants.NEWLINE);

            for (String line : list)
            {
                builder.append(indent);
                builder.append(" * ");
                builder.append(line);
                builder.append(Constants.NEWLINE);
            }

            builder.append(indent);
            builder.append(" */");
            //builder.append(Constants.NEWLINE);

        }
        // one line
        else
        {
            builder.append(indent);
            builder.append("/** ");
            builder.append(javadoc);
            builder.append(" */");
            //builder.append(Constants.NEWLINE);
        }

        return builder.toString().replace(indent, indent);
    }
    
    private static List<String> wrapText(String text, int len)
    {
        // return empty array for null text
        if (text == null)
        {
            return new ArrayList<>();
        }

        // return text if len is zero or less
        if (len <= 0)
        {
            return new ArrayList<>(Collections.singletonList(text));
        }

        // return text if less than length
        if (text.length() <= len)
        {
            return new ArrayList<>(Collections.singletonList(text));
        }

        List<String> lines = new LinkedList<>();
        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();
        int tempNum;

        // each char in array
        for (char c : text.toCharArray())
        {
            // its a wordBreaking character.
            if (c == ' ' || c == ',' || c == '-')
            {
                // add the character to the word
                word.append(c);

                // its a space. set TempNum to 1, otherwise leave it as a wrappable char
                tempNum = Character.isWhitespace(c) ? 1 : 0;

                // subtract tempNum from the length of the word
                if ((line.length() + word.length() - tempNum) > len)
                {
                    lines.add(line.toString());
                    line.delete(0, line.length());
                }

                // new word, add it to the next line and clear the word
                line.append(word);
                word.delete(0, word.length());

            }
            // not a linebreak char
            else
            {
                // add it to the word and move on
                word.append(c);
            }
        }

        // handle any extra chars in current word
        if (word.length() > 0)
        {
            if ((line.length() + word.length()) > len)
            {
                lines.add(line.toString());
                line.delete(0, line.length());
            }
            line.append(word);
        }

        // handle extra line
        if (line.length() > 0)
        {
            lines.add(line.toString());
        }

        List<String> temp = new ArrayList<>(lines.size());
        for (String s : lines)
        {
            temp.add(s.trim());
        }
        return temp;
    }
}

package uk.bl.wa.util;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2015 The UK Web Archive
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

public class TestUtil {

    public static String loadUTF8(String resource) {
        byte[] bytes = loadBytes(resource);
        return bytes == null ? null : new String(bytes, Charset.forName("utf-8"));
    }

    private static byte[] loadBytes(String resource) {
        URL res = resolve(resource);
        if (res == null) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            IOUtil.copy(res.openStream(), bytes);
        } catch (IOException e) {
            throw new RuntimeException("Unable to get content from resource '" + resource + "'", e);
        }
        return bytes.toByteArray();
    }

    public static URL resolve(String resource) {
        URL classPathResource;
        if ((classPathResource = TestUtil.class.getClassLoader().getResource(resource)) != null) {
            return classPathResource;
        }

        File fileResource = new File(resource);
        if (fileResource.exists()) {
            try {
                return fileResource.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Unable to resolve resource '" + resource + "'", e);
            }
        }
        return null;
    }
}

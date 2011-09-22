/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import bmsi.util.Diff;
import bmsi.util.DiffPrint.Base;
import bmsi.util.DiffPrint.UnifiedPrint;

import com.liferay.portal.kernel.util.StringUtil;

public class UpgradeDiffer {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			throw new IllegalArgumentException("You must provide two arguments, the plugin SDk path and the source code zip file.");
		}

		UpgradeDiffer upgradeDiffer = new UpgradeDiffer(args[0], args[1]);

		upgradeDiffer.execute();
	}

	protected Pattern extPattern = Pattern.compile("(.)*ext/(\\w)+-ext/(.)*");
	protected String pluginSdkPath;
	protected String portalSourceZipPath;
	protected Map<String, String> portalImplSourceMap = new HashMap<String, String>();
	protected Map<String, String> portalServiceSourceMap = new HashMap<String, String>();
	protected Map<String, String> portalWebSourceMap = new HashMap<String, String>();
	protected Map<String, String> utilBridgesSourceMap = new HashMap<String, String>();
	protected Map<String, String> utilJavaSourceMap = new HashMap<String, String>();
	protected Map<String, String> utilTaglibSourceMap = new HashMap<String, String>();

	public UpgradeDiffer(String pluginSdkPath, String portalSourceZipPath) {
		this.pluginSdkPath = pluginSdkPath;
		this.portalSourceZipPath = portalSourceZipPath;
	}

	public void execute() throws Exception {
		ZipFile zipFile = new ZipFile(new File(portalSourceZipPath), ZipFile.OPEN_READ);

		scanPortalSource(zipFile);

		Map<String, String> mapping = getModifiedFiles(new File(pluginSdkPath));

		Set<String> keys = mapping.keySet();

		for (String key : keys) {
			String value = mapping.get(key); 
			ZipEntry zipEntry = zipFile.getEntry(key);
			
			if (zipEntry == null) {
				System.out.println("Adding new file " + key);
				continue;
			}

			String[] source = loadContent(zipFile.getInputStream(zipEntry));
			String[] target = loadContent(new FileInputStream(value));

			Diff diff = new Diff(source, target);

			Diff.change script = diff.diff_2(false);

			if (script == null) {
				System.err.println("No differences");
			} else {
				File patchFile = getPatchFile(pluginSdkPath, value.substring(pluginSdkPath.length()));

				Base printer = new UnifiedPrint(source, target);

				printer.setOutput(new FileWriter(patchFile));
				printer.print_header(key, value);
				printer.print_script(script);
			}
		}
	}

	protected Map<String, String> getModifiedFiles(File root) {
		Map<String, String> plguinSourceMapping = new HashMap<String, String>();
		
		File[] files = root.listFiles();

		for (File file : files) {
			if (file.isDirectory()) {
				plguinSourceMapping.putAll(getModifiedFiles(file));
			} else {
				String fileName = file.getAbsolutePath();
				if (extPattern.matcher(fileName).matches() && fileName.indexOf("diffs") == -1) {

					verifyModification(fileName, "ext-impl/src/", portalImplSourceMap, plguinSourceMapping);
					verifyModification(fileName, "ext-service/src/", portalServiceSourceMap, plguinSourceMapping);
					verifyModification(fileName, "ext-web/docroot/", portalWebSourceMap, plguinSourceMapping);
					verifyModification(fileName, "ext-util-bridges/src/", utilBridgesSourceMap, plguinSourceMapping);
					verifyModification(fileName, "ext-util-java/src/", utilJavaSourceMap, plguinSourceMapping);
					verifyModification(fileName, "ext-util-taglib/src/", utilTaglibSourceMap, plguinSourceMapping);

				}
			}
		}

		return plguinSourceMapping;
	}

	protected File getPatchFile(String dirPath, String fileName) {
		fileName = fileName + ".patch";
		dirPath = dirPath + System.getProperty("file.separator") + "diffs" + System.getProperty("file.separator");

		int endIndex = fileName.lastIndexOf("/");

		dirPath = dirPath + fileName.substring(0, endIndex);

		File parent = new File(dirPath);

		parent.mkdirs();

		return new File(parent, fileName.substring(endIndex + 1));
	}

	protected String[] loadContent (InputStream in) throws IOException {
		return StringUtil.split(
				StringUtil.read(in),
				System.getProperty("line.separator"));
	}

	protected void scanPortalSource(String fileName, String pattern, Map<String, String> portalSourceMapping) {
		int index = fileName.indexOf(pattern);
		if (index != -1) {
			String key = fileName.substring(index + pattern.length());
			portalSourceMapping.put(key, fileName);
		}
	}

	protected void scanPortalSource(ZipFile zipFile) throws Exception {
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipFile
				.entries();

		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = (ZipEntry) entries.nextElement();
			String fileName = zipEntry.getName();

			scanPortalSource(fileName, "portal-impl/src/", portalImplSourceMap);
			scanPortalSource(fileName, "portal-service/src/", portalServiceSourceMap);
			scanPortalSource(fileName, "portal-web/docroot/", portalWebSourceMap);
			scanPortalSource(fileName, "util-bridges/src/", utilBridgesSourceMap);
			scanPortalSource(fileName, "util-java/src/", utilJavaSourceMap);
			scanPortalSource(fileName, "util-taglib/src/", utilTaglibSourceMap);
		}
	}

	protected void verifyModification(String fileName, String pattern, Map<String, String> portalSourceMapping, Map<String, String> pluginSourceMapping) {
		int index = fileName.indexOf(pattern);

		if (index != -1) {
			String key = fileName.substring(index + pattern.length());

			String value = portalSourceMapping.get(key);
			if (value != null) {
				pluginSourceMapping.put(value, fileName);
			} else {
				pluginSourceMapping.put(fileName, fileName);
			}
		}
		
	}
}
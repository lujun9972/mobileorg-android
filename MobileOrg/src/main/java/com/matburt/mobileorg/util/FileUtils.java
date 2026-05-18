package com.matburt.mobileorg.util;

import java.io.BufferedReader;
import java.io.IOException;

public class FileUtils {

	public static final String CAPTURE_FILE = "mobileorg.org";
	public static final String CAPTURE_FILE_ALIAS = "Captures";

	public static String read(BufferedReader reader) throws IOException {
		if (reader == null) {
			return "";
		}

		StringBuilder fileContents = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			fileContents.append(line);
			fileContents.append("\n");
		}

		return fileContents.toString();
	}
}

package com.commonwealthrobotics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StudioBuildInfo {

	private static Class baseBuildInfoClass = CaDoodle.class;

	public static String getVersion() {
		String s = getTag("app.version");

		if (s == null) {
			s = "0.0.0";
		}
		return s;
	}

	public static int getProtocolVersion() {
		return getBuildInfo()[0];
	}

	public static int getSDKVersion() {
		return getBuildInfo()[1];
	}

	public static int getBuildVersion() {
		return getBuildInfo()[2];
	}

	public static int[] getBuildInfo() {
		try {
			String s = getVersion();
			String[] splits = s.split("[.]+");
			int[] rev = new int[3];
			for (int i = 0; i < 3; i++) {
				rev[i] = new Integer(splits[i]);
			}
			return rev;
		} catch (NumberFormatException e) {
			return new int[] { 0, 0, 0 };
		}

	}

	private static String getTag(String target) {
		try {
			StringBuilder s = new StringBuilder();
			InputStream is = getBuildPropertiesStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			String line;
			try {
				while (null != (line = br.readLine())) {
					s.append(line).append("\n");
				}
			} catch (IOException ignored) {
			}

			String[] splitAll = s.toString().split("[\n]+");
			for (String aSplitAll : splitAll) {
				if (aSplitAll.contains(target)) {
					String[] split = aSplitAll.split("[=]+");
					return split[1];
				}
			}
		} catch (NullPointerException e) {
			return null;
		}
		return null;
	}

	public static String getBuildDate() {
		String s = "";
		InputStream is = StudioBuildInfo.class.getResourceAsStream("/META-INF/MANIFEST.MF");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		try {
			while (null != (line = br.readLine())) {
				s += line + "\n";
			}
		} catch (IOException ignored) {
		}
		// System.out.println("Manifest:\n"+s);
		return "";
	}

	private static InputStream getBuildPropertiesStream() {
		return baseBuildInfoClass.getResourceAsStream("build.properties");
	}

	public static String getSDKVersionString() {
		return getName();
	}

	public static boolean isOS64bit() {
		return (System.getProperty("os.arch").contains("64"));
	}

	public static boolean isARM() {
		return (System.getProperty("os.arch").toLowerCase().contains("arm"));
	}

	public static boolean isLinux() {
		return (System.getProperty("os.name").toLowerCase().contains("linux"));
	}

	public static boolean isWindows() {
		return (System.getProperty("os.name").toLowerCase().contains("win"));
	}

	public static boolean isMac() {
		return (System.getProperty("os.name").toLowerCase().contains("mac"));
	}

	public static boolean isUnix() {
		return (isLinux() || isMac());
	}

	public static Class getBaseBuildInfoClass() {
		return baseBuildInfoClass;
	}

	public static void setBaseBuildInfoClass(Class c) {
		baseBuildInfoClass = c;
	}

	public static String getName() {
		return "CaDoodle " + getProtocolVersion() + "." + getSDKVersion() + "." + getBuildVersion();
	}
}

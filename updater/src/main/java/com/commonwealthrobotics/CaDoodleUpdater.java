/* (C)2023 */
package com.commonwealthrobotics;


public class CaDoodleUpdater {
	private static String project = "CommonWealthRobotics";
	private static String repo = "CaDoodle";
	private static String jvm = "cadoodle-jvm-configuration.json";

	private static String baseURL = "https://github.com/" + project + "/" + repo;

	public static void main(String[] a) {
		System.out.println("Launching Cadoodle Updater");
		System.out.println("Build info " + StudioBuildInfo.getVersion());
		System.out.println("OS " + System.getProperty("os.name"));
		System.out.println("Archetecture " + System.getProperty("os.arch"));
		System.out.println("Build Date " + StudioBuildInfo.getBuildDate());
		String[] args = new String[] {project,repo,"CaDoodle.jar",jvm,"-Dprism.forceGPU=true","-jar"};
		LatestFromGithubLaunch.Main.main(args);
	}
}

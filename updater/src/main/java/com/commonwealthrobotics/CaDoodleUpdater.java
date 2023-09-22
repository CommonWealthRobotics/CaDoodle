package com.commonwealthrobotics;

public class CaDoodleUpdater{
	private static String project = "CommonWealthRobotics";
	private static String repo = "CaDoodle";
	
	private static String baseURL = "https://github.com/" + project + "/" + repo ;
	public static void main(String [] args){
		System.out.println("Launching Cadoodle Updater");
		System.out.println("Build info "+StudioBuildInfo.getVersion());
		System.out.println("OS "+System.getProperty("os.name"));
		System.out.println("Archetecture "+System.getProperty("os.arch"));
		
	}
}

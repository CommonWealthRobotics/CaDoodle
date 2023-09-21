package com.commonwealthrobotics;

public class CaDoodle{

	public static void main(String [] args){
		System.out.println("Launching Cadoodle application");
		System.out.println("Build info "+StudioBuildInfo.getVersion());
		System.out.println("OS "+System.getProperty("os.name"));
		System.out.println("Archetecture "+System.getProperty("os.arch"));
	}
}

package com.commonwealthrobotics;

import com.neuronrobotics.bowlerstudio.BowlerStudio;

import javafx.application.Application;
import javafx.stage.Stage;

public class CaDoodle extends Application{

	public static void main(String [] args){
		System.out.println("Launching Cadoodle application");
		System.out.println("Build info "+StudioBuildInfo.getVersion());
		System.out.println("OS "+System.getProperty("os.name"));
		System.out.println("Archetecture "+System.getProperty("os.arch"));
		try {
			BowlerStudio.main(args);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void start(Stage arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}
}

package com.commonwealthrobotics;

public class HatRackMain {

	public static void main(String[] args) {
		System.out.println("Hello World!");
		for(int i=0;i<args.length;i++) {
			System.out.println("Arg "+i+" = "+args[i]);
		}
		System.out.println(System.getProperty("os.name"));
		System.out.println(System.getProperty("os.arch"));

	}

}

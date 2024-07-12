package com.commonwealthrobotics;
/**
 * Sample Skeleton for 'ui.fxml' Controller Class
 */

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class LatestFromGithubLaunchUI {
	public static String[] argsFromSystem;
	//public static String[] args;
	public static String project;
	public static Stage stage;

	public static String latestVersionString = "";
	public static String myVersionString = null;
	public static long sizeOfJar = 0;
	public static long sizeOfJson = 0;
	@FXML // ResourceBundle that was given to the FXMLLoader
	private ResourceBundle resources;

	@FXML // URL location of the FXML file that was given to the FXMLLoader
	private URL location;

	@FXML // fx:id="progress"
	private ProgressBar progress; // Value injected by FXMLLoader

	@FXML // fx:id="previousVersion"
	private Label previousVersion; // Value injected by FXMLLoader
	@FXML // fx:id="previousVersion"
	private Label binary; // Value injected by FXMLLoader
	@FXML // fx:id="currentVersion"
	private Label currentVersion; // Value injected by FXMLLoader

	@FXML // fx:id="yesButton"
	private Button yesButton; // Value injected by FXMLLoader

	@FXML // fx:id="noButton"
	private Button noButton; // Value injected by FXMLLoader

	private static HashMap<String, Object> database;

	private String bindir;

	private File bindirFile;

	private File myVersionFile;

	private String myVersionFileString;

	private static String downloadJarURL;
	private static String downloadJsonURL;
	
	public static String repoName;
	public static String jarName;

	@FXML
	void onNo(ActionEvent event) {
		System.out.println("No path");
		launchApplication();
	}

	@FXML
	void onYes(ActionEvent event) {
		System.out.println("Yes path");
		yesButton.setDisable(true);
		noButton.setDisable(true);
		new Thread(() -> {

			try {
				String downloadURL2 = downloadJarURL;
				URL url = new URL(downloadURL2);
				URLConnection connection = url.openConnection();
				InputStream is = connection.getInputStream();
				ProcessInputStream pis = new ProcessInputStream(is, (int) sizeOfJar);
				pis.addListener(new Listener() {
					@Override
					public void process(double percent) {
						Platform.runLater(() -> {
							progress.setProgress(percent);
						});
					}
				});
				File folder = new File(bindir + latestVersionString + "/");
				File exe = new File(bindir + latestVersionString + "/" + jarName);

				if (!folder.exists() || !exe.exists() || sizeOfJar != exe.length()) {
					folder.mkdirs();
					exe.createNewFile();
					byte dataBuffer[] = new byte[1024];
					int bytesRead;
					FileOutputStream fileOutputStream = new FileOutputStream(exe.getAbsoluteFile());
					while ((bytesRead = pis.read(dataBuffer, 0, 1024)) != -1) {
						fileOutputStream.write(dataBuffer, 0, bytesRead);
					}
					fileOutputStream.close();
					pis.close();

				}
				if (folder.exists() && exe.exists() && sizeOfJar == exe.length())
					myVersionString = latestVersionString;
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			launchApplication();
		}).start();
	}
	private boolean launched=false;
	public void launchApplication() {
		if(launched)
			throw new RuntimeException("Applicaion is already launched!");
		launched=true;
		Platform.runLater(() -> {
			yesButton.setDisable(true);
			noButton.setDisable(true);
			
		});
		new Thread(() -> {
			String command;
			try {
				command = JvmManager.getCommandString(project, repoName, myVersionString,downloadJsonURL,sizeOfJson,progress,bindir);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
				return;
			}
			// Run this later to show downloading the JVM
			Platform.runLater(() ->stage.close());

			try {
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	//		
	//		for (int i = 4; i < args.length; i++) {
	//			command += " " + args[i];
	//		}
			try {
				myVersionFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			BufferedWriter writer;
			try {
				writer = new BufferedWriter(new FileWriter(myVersionFileString));
				writer.write(myVersionString);
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	
			String fc =!isWin()?
						command + " " + bindir + myVersionString + "/" + jarName+"":
							command + " \"" + bindir + myVersionString + "/" + jarName+"\"";
			for(String s:argsFromSystem) {
				fc+=(" "+s);
			}
			
			String finalCommand=fc;
			System.out.println("Running:\n\n"+finalCommand+"\n\n");
			new Thread(() -> {
				try {
					Process process = Runtime.getRuntime().exec(finalCommand);
					BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					String line;
					while ((line = reader.readLine()) != null && process.isAlive()) {
						System.out.println(line);
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					reader.close();
					System.out.println("LatestFromGithubLaunch clean exit");
					System.exit(0);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}).start();
		}).start();
	}

	public static boolean isWin() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}
	public static boolean isLin() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}
	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	public static boolean isArm() {
		return System.getProperty("os.arch").toLowerCase().contains("aarch64");
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static void readCurrentVersion(String url) throws IOException {
		InputStream is = new URL(url).openStream();
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			// Create the type, this tells GSON what datatypes to instantiate when parsing
			// and saving the json
			Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
			}.getType();
			// chreat the gson object, this is the parsing factory
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			database = gson.fromJson(jsonText, TT_mapStringString);
			latestVersionString = (String) database.get("tag_name");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> assets = (List<Map<String, Object>>) database.get("assets");
			for (Map<String, Object> key : assets) {
				if (((String) key.get("name")).contentEquals(jarName)) {
					downloadJarURL = (String) key.get("browser_download_url");
					sizeOfJar = ((Double) key.get("size")).longValue();
					System.out.println(downloadJarURL + " Size " + sizeOfJar + " bytes");
				}
				if (((String) key.get("name")).contentEquals("jvm.json")) {
					downloadJsonURL = (String) key.get("browser_download_url");
					sizeOfJson = ((Double) key.get("size")).longValue();
					System.out.println(downloadJsonURL + " Size " + sizeOfJson + " bytes");
				}
				
			}
		} finally {
			is.close();
		}
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void initialize() {
		assert progress != null : "fx:id=\"progress\" was not injected: check your FXML file 'ui.fxml'.";
		assert previousVersion != null : "fx:id=\"previousVersion\" was not injected: check your FXML file 'ui.fxml'.";
		assert currentVersion != null : "fx:id=\"currentVersion\" was not injected: check your FXML file 'ui.fxml'.";
		boolean noInternet = false;
		try {
			readCurrentVersion("https://api.github.com/repos/" + project + "/" + repoName + "/releases/latest");
			binary.setText(project + "\n" + repoName + "\n" + jarName + "\n" + (sizeOfJar / 1000000) + " Mb");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			noInternet=true;
		}
		stage.setTitle("Auto-Updater for " + repoName);
		currentVersion.setText(latestVersionString);
		bindir = System.getProperty("user.home") + "/bin/" + repoName + "Install/";
		myVersionFileString = bindir + "currentversion.txt";
		myVersionFile = new File(myVersionFileString);
		bindirFile = new File(bindir);
		if (!bindirFile.exists())
			bindirFile.mkdirs();
		if (!myVersionFile.exists()) {

			onYes(null);
			return;
		} else {
			try {
				myVersionString = new String(Files.readAllBytes(Paths.get(myVersionFileString))).trim();
				previousVersion.setText(myVersionString);
				if (myVersionString.length() < 3) {
					onYes(null);
					return;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(!noInternet) {
			if(myVersionString==null) {
				launchApplication();
				return;
			}
			else
				if (myVersionString.contentEquals(latestVersionString)) {
					launchApplication();
					return;
				}
		}else {
			onNo(null);
			return;
		}

	}
}

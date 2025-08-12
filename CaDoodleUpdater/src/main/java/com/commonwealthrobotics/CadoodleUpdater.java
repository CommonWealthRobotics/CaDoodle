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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class CadoodleUpdater {
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
	@FXML // fx:id="currentVersion"
	private Label infoBar;
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
		infoBar.setText("Downloading CaDoodle JAR...");
		new Thread(() -> {

			try {
				
				String downloadURL2 = downloadJarURL;
				System.out.println("Downloading "+downloadJarURL);
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
				File exe = new File(bindir + latestVersionString + "/" + jarName+"_TMP");
				File exeFinal = new File(bindir + latestVersionString + "/" + jarName);

				if (!folder.exists() || !exeFinal.exists() || sizeOfJar != exeFinal.length()) {
					folder.mkdirs();
					if(exe.exists())
						exe.delete();
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

				if(exe.exists())
					Files.move(exe.toPath(), exeFinal.toPath(), StandardCopyOption.REPLACE_EXISTING);
				if (folder.exists() && exeFinal.exists() && sizeOfJar == exeFinal.length())
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
				command = JvmManager.getCommandString(project, repoName, myVersionString,downloadJsonURL,sizeOfJson,progress,bindir,infoBar);
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
					// Get the current environment
					Map<String, String> env = new HashMap<>(System.getenv());

					// Extract JAVA_HOME from the JVM path
					// Assuming your command starts with the full path to java executable
					String javaHome = extractJavaHomeFromCommand(command);
					if (javaHome != null) {
					    env.put("JAVA_HOME", javaHome);
					}

					// Convert environment map to array format
					String[] envArray = env.entrySet().stream()
					    .map(entry -> entry.getKey() + "=" + entry.getValue())
					    .toArray(String[]::new);
					// Execute with modified environment
					Process process = Runtime.getRuntime().exec(finalCommand, envArray);
					Thread thread = new Thread(()->{
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
						String line;
						try {
							while ((line = reader.readLine()) != null&& 
									process.isAlive()) {
								System.err.println(line);
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							reader.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					});
					thread.start();
					Thread thread2 = new Thread(()->{
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
						String line;
						try {
							while ((line = reader.readLine()) != null&& 
									process.isAlive()) {
								System.out.println(line);
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							reader.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					});
					thread2.start();
					try {
						thread2.join();
						thread.join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("CaDoodle Updater clean exit");
					System.exit(0);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}).start();
		}).start();
	}
	private static String extractJavaHomeFromCommand(String command) {
	    try {
	        // Split the command to get the java executable path
	        String[] parts = command.split(" ");
	        if (parts.length > 0) {
	            String javaPath = parts[0];
	            
	            // Remove quotes if present
	            javaPath = javaPath.replace("\"", "");
	            
	            // Get the parent directory of the bin folder
	            Path path = Paths.get(javaPath);
	            if (path.getFileName().toString().startsWith("java")) {
	                // Go up from java executable to bin, then to JAVA_HOME
	                Path binDir = path.getParent();
	                if (binDir != null && binDir.getFileName().toString().equals("bin")) {
	                    return binDir.getParent().toString();
	                }
	            }
	        }
	    } catch (Exception e) {
	        System.err.println("Could not extract JAVA_HOME from command: " + e.getMessage());
	    }
	    return null;
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
			downloadJarURL=null;
			for (Map<String, Object> key : assets) {
				String string = (String) key.get("name");

				System.out.println("Checking "+string);
				if (string.contentEquals(jarName)) {
					downloadJarURL = (String) key.get("browser_download_url");
					sizeOfJar = ((Double) key.get("size")).longValue();
					System.out.println(downloadJarURL + " Size " + sizeOfJar + " bytes");
				}else
					System.out.println(string+" is not "+jarName);
				if (string.contentEquals("jvm.json")) {
					downloadJsonURL = (String) key.get("browser_download_url");
					sizeOfJson = ((Double) key.get("size")).longValue();
					System.out.println(downloadJsonURL + " Size " + sizeOfJson + " bytes");
				}else
					System.out.println(string+" is not jvm.json");
				
			}
			if(downloadJarURL==null) {
				System.err.println("FAIL the Jar is missing in release "+latestVersionString);
				System.exit(1);
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

package com.commonwealthrobotics;

/**
 * Sample Skeleton for 'ui.fxml' Controller Class
 */

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.lang.reflect.Type;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.StandardOpenOption;

public class CadoodleUpdater {
	public static String[] argsFromSystem;
	// public static String[] args;
	public static String project;
	public static Stage stage;

	public static String latestVersionString = "";
	public static String myVersionString = null;
	public static long sizeOfJar = 0;
	public static long sizeOfJson = 0;
	public static long sizeOfZip = 0;
	@FXML // ResourceBundle that was given to the FXMLLoader
	private ResourceBundle resources;
	@FXML
	private HBox initialStartupControls;
	@FXML
	private HBox pluginFileBox;

	@FXML // URL location of the FXML file that was given to the FXMLLoader
	private URL location;

	@FXML // fx:id="progressBar"
	private ProgressBar progressBar; // Value injected by FXMLLoader
	@FXML // fx:id="progressLabel"
	private Label progressLabel; // Value injected by FXMLLoader

	@FXML // fx:id="previousVersion"
	private Label previousVersion; // Value injected by FXMLLoader
	@FXML // fx:id="previousVersion"
	private Label binary; // Value injected by FXMLLoader
	@FXML // fx:id="currentVersion"
	private Label currentVersion; // Value injected by FXMLLoader
	@FXML // fx:id="currentVersion"
	private Label infoBar;
	@FXML // fx:id="currentVersion"
	private Label pluginFileLabel;

	@FXML // fx:id="currentVersion"
	private RadioButton downloadPlugins;
	@FXML // fx:id="currentVersion"
	private RadioButton useOsPlugins;
	@FXML // fx:id="currentVersion"
	private RadioButton selectPluginFile;
	@FXML // fx:id="yesButton"
	private Button yesButton; // Value injected by FXMLLoader

	@FXML // fx:id="noButton"
	private Button noButton; // Value injected by FXMLLoader
	@FXML // fx:id="noButton"
	private Button uptodateButton;
	@FXML // fx:id="noButton"
	private Button selectPluginFileButton;
	private static HashMap<String, Object> database;

	private String bindir;

	private File bindirFile;

	private File myVersionFile;

	private String myVersionFileString;

	private static String downloadJarURL;
	private static String downloadJsonURL;
	private static String downloadZip;
	private static long timeSincePrint = System.currentTimeMillis();

	public static String repoName;
	public static String jarName;

	@FXML
	void onNo(ActionEvent event) {
		yesButton.setDisable(true); // Prevent another button click
		noButton.setDisable(true);
		System.out.println("No path, user just wants to launch the app");
		infoBar.setText("Starting application...");
		launchApplication();
	}

	@FXML
	void onYes(ActionEvent event) {
		System.out.println("Yes path, user wants/needs to update the app");
		yesButton.setDisable(true); // Prevent another button click
		noButton.setDisable(true);
		progressBar.setDisable(false);
		infoBar.setText("Downloading CaDoodle Application, please wait...");
		progressLabel.setText("Downloading 0.0%");
		initialStartupControls.setVisible(false);
		pluginFileBox.setVisible(false);
		new Thread(() -> {

			boolean downloadFailed = false;
			try {
				String downloadURL2 = downloadJarURL;
				System.out.println("Downloading " + downloadJarURL);
				URL url = new URL(downloadURL2);
				URLConnection connection = url.openConnection();
				InputStream is = connection.getInputStream();

				ProcessInputStream pis = new ProcessInputStream(is, (int) sizeOfJar);
				pis.addListener(new Listener() {
					@Override
					public void process(double percent) {
						if ((System.currentTimeMillis() - timeSincePrint) > 1000) {
							timeSincePrint = System.currentTimeMillis();
							System.out.println(String.format("Downloading %s %.1f%%", downloadURL2, (percent * 100)));
							Platform.runLater(() -> {
								if (percent <= 1.0) {
									progressBar.setProgress(percent);
									progressLabel.setText(String.format("Downloading %.1f%%", (percent * 100)));
								}
							});
						}
					}
				});

				File folder = new File(bindir + latestVersionString + "/");
				File exe = new File(bindir + latestVersionString + "/" + jarName + "_TMP");
				File exeFinal = new File(bindir + latestVersionString + "/" + jarName);

				if (!folder.exists() || !exeFinal.exists() || sizeOfJar != exeFinal.length()) {
					folder.mkdirs();

					if (exe.exists())
						exe.delete();

					exe.createNewFile();
					int bufferSize = 16 * 1024;
					byte dataBuffer[] = new byte[bufferSize];
					int bytesRead;
					FileOutputStream fileOutputStream = new FileOutputStream(exe.getAbsoluteFile());
					while ((bytesRead = pis.read(dataBuffer, 0, bufferSize)) != -1) {
						fileOutputStream.write(dataBuffer, 0, bytesRead);
					}
					fileOutputStream.close();
					pis.close();
				}

				if (exe.exists())
					Files.move(exe.toPath(), exeFinal.toPath(), StandardCopyOption.REPLACE_EXISTING);

				if (folder.exists() && exeFinal.exists() && sizeOfJar == exeFinal.length())
					myVersionString = latestVersionString;

			} catch (Exception e1) {
				// TODO Auto-generated catch block
				System.out.println("Download failed!");
				infoBar.setText("Download failed! " + e1.getMessage());
				downloadFailed = true;
				e1.printStackTrace();
			}

			if (!downloadFailed)
				launchApplication();
		}).start();
	}

	private boolean launched = false;
	private Path goloblaPinFile = null;
	private Path pluginsZip;

	public void launchApplication() {
		if (launched) {
			infoBar.setText("Application is already running!");
			throw new RuntimeException("Application is already running!");
		}

		launched = true;
		Platform.runLater(() -> {
			infoBar.setText("Starting application...");
			yesButton.setDisable(true);
			noButton.setDisable(true);
		});
		// Run this later to show downloading the JVM

		new Thread(() -> {
			String command;
			try {
				command = JvmManager.getCommandString(project, repoName, myVersionString, downloadJsonURL, downloadZip,
						sizeOfZip, sizeOfJson, progressBar, progressLabel, bindir, infoBar);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
				return;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			//
			// for (int i = 4; i < args.length; i++) {
			// command += " " + args[i];
			// }
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
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Use quoted (Windows) or unquoted path (non-Windows)
			String fc = (isWin() ? (command + " \"" + bindir + myVersionString + "/" + jarName + "\"")
					: (command + " " + bindir + myVersionString + "/" + jarName + ""));

			for (String s : argsFromSystem)
				fc += (" " + s);

			String finalCommand = fc;
			System.out.println("Running:\n\n" + finalCommand + "\n\n");
			System.out.println("JAVA_HOME: " + extractJavaHomeFromCommand(command) + "\n\n");

			new Thread(() -> {
				try {
					// Get the current environment
					Map<String, String> env = new HashMap<>(System.getenv());

					// Extract JAVA_HOME from the JVM path
					// Assuming your command starts with the full path to java executable
					String javaHome = extractJavaHomeFromCommand(command);
					if (javaHome != null)
						env.put("JAVA_HOME", javaHome);

					// Convert environment map to array format
					String[] envArray = env.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
							.toArray(String[]::new);
					// Execute with modified environment
					Process process = Runtime.getRuntime().exec(finalCommand, envArray);
					Thread thread = new Thread(() -> {
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
						String line;
						try {
							while ((line = reader.readLine()) != null && process.isAlive()) {
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
					Platform.runLater(() -> stage.close());
					Thread thread2 = new Thread(() -> {
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
						String line;
						try {
							while (((line = reader.readLine()) != null) && process.isAlive()) {
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

	public static void readCurrentVersion(String url) throws IOException, URISyntaxException {
		System.out.println("Read current version from " + url);
		InputStream is = new URI(url).toURL().openStream();
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			System.out.println("Got file contents " + jsonText);
			// Create the type, this tells GSON what datatypes to instantiate when parsing
			// and saving the json
			Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
			}.getType();
			// create the gson object, this is the parsing factory
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			database = gson.fromJson(jsonText, TT_mapStringString);
			System.out.println("Database:\n" + database);
			latestVersionString = (String) database.get("tag_name");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> assets = (List<Map<String, Object>>) database.get("assets");
			downloadJarURL = null;
			System.out.println("Assets:\n" + assets);
			for (Map<String, Object> key : assets) {
				String string = (String) key.get("name");

				System.out.println("Checking " + string);
				if (string.equals(jarName)) {
					downloadJarURL = (String) key.get("browser_download_url");
					sizeOfJar = ((Double) key.get("size")).longValue();
					System.out.println(downloadJarURL + " Size " + sizeOfJar + " bytes");
				} else
					System.out.println(string + " is not " + jarName);
				if (string.equals("jvm.json")) {
					downloadJsonURL = (String) key.get("browser_download_url");
					sizeOfJson = ((Double) key.get("size")).longValue();
					System.out.println(downloadJsonURL + " Size " + sizeOfJson + " bytes");
				}

				if (string.equals("gitcache.zip")) {
					downloadZip = (String) key.get("browser_download_url");
					sizeOfZip = ((Double) key.get("size")).longValue();
					System.out.println(downloadZip + " Size " + sizeOfZip + " bytes");
				}

			}
			if (downloadJarURL == null) {
				System.err.println("FAIL the Jar is missing in release " + latestVersionString);
				System.exit(1);
			}
		} finally {
			is.close();
		}
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void onExtractLTS(ActionEvent ev) {
		runPluginProcess();
		initialStartupControls.setVisible(false);
		pluginFileBox.setVisible(false);
		new Thread(() -> {
			String pinFileName = bindir + "pinVersion";
			File pinFile = new File(pinFileName);
			if (!pinFile.exists()) {
				try {
					pinFile.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			Platform.runLater(() -> onNo(null));
		}).start();
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void onFirstYes(ActionEvent ev) {
		runPluginProcess();
		onYes(ev);
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void onselectFile(ActionEvent e) {

		FileChooser fileChooser = new FileChooser();

		fileChooser.setInitialDirectory(pluginsZip.getParent().toFile());
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Files", "*.zip"));

		fileChooser.setTitle("Plugin Archive Selection");

		pluginsZip = fileChooser.showOpenDialog(stage).toPath();

		pluginFileLabel.setText(pluginsZip.toAbsolutePath().toString());
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void pluginOptionChange(ActionEvent e) {
		selectPluginFileButton.setDisable(!selectPluginFile.isSelected());

	}

	private void setupDefaultVersion() {
		bindir = System.getProperty("user.home") + "/bin/" + repoName + "Install/";
		myVersionFileString = bindir + "currentversion.txt";
		try {
			Path jarDir = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
			System.out.println("Jar located in " + jarDir);
			Path bundledZip = jarDir.resolve("CaDoodle-ApplicationInstall.zip");
			goloblaPinFile = jarDir.resolve("pinVersionSystem");
			pluginsZip = jarDir.resolve("BowlerStudioInstall.zip");
			if (pluginsZip.toFile().exists()) {
				selectPluginFile.setSelected(true);
				pluginFileLabel.setText(pluginsZip.toAbsolutePath().toString());
			} else {
				selectPluginFileButton.setDisable(true);
			}
			Path jvmArchive = null;
			String[] files = jarDir.toFile().list();
			if (files != null) {
				for (String s : files) {
					if (s.startsWith("zulu") && s.contains("jre")) {
						jvmArchive = jarDir.resolve(s);
						break;
					}
				}
			}
			if (jvmArchive == null)
				throw new IllegalStateException("No bundled JVM found in " + jarDir);
			System.out.println("Found zip " + bundledZip);
			System.out.println("Found JVM " + jvmArchive);
			// 1. Extract CaDoodle-ApplicationInstall.zip into $HOME/bin/
			Path homebin = Path.of(System.getProperty("user.home"), "bin");
			Files.createDirectories(homebin);

			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(bundledZip))) {
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					Path target = homebin.resolve(entry.getName()).normalize();
					if (!target.startsWith(homebin))
						throw new SecurityException("Zip slip detected: " + entry.getName());
					if (entry.isDirectory()) {
						Files.createDirectories(target);
					} else {
						Files.createDirectories(target.getParent());
						Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
					}
					zis.closeEntry();
				}
			}

			// 2. Copy the JVM archive into $HOME/bin/CaDoodle-ApplicationInstall/
			Path installDir = homebin.resolve("CaDoodle-ApplicationInstall");
			Files.createDirectories(installDir);
			Files.copy(jvmArchive, installDir.resolve(jvmArchive.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			myVersionString = new String(Files.readAllBytes(Paths.get(myVersionFileString))).trim();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// onYes(null);
			return;
		}
	}

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void initialize() {
		assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'ui.fxml'.";
		assert previousVersion != null : "fx:id=\"previousVersion\" was not injected: check your FXML file 'ui.fxml'.";
		assert currentVersion != null : "fx:id=\"currentVersion\" was not injected: check your FXML file 'ui.fxml'.";
		assert pluginFileBox != null : "fx:id=\"currentVersion\" was not injected: check your FXML file 'ui.fxml'.";

		stage.setTitle("Auto-Updater for " + repoName);
		yesButton.setDisable(true);
		noButton.setDisable(true);
		bindir = System.getProperty("user.home") + "/bin/" + repoName + "Install/";
		myVersionFileString = bindir + "currentversion.txt";
		String pinFileName = bindir + "pinVersion";
		File pinFile = new File(pinFileName);
		boolean noInternet = false;

		if (pinFile.exists()) {
			noInternet = true;
		}

		if (!noInternet) {
			try {
				readCurrentVersion("https://api.github.com/repos/" + project + "/" + repoName + "/releases/latest");
				binary.setText(project + "\n" + repoName + "\n" + jarName + "\n" + (sizeOfJar / 1000000) + " MB");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				noInternet = true;
			}
		}

		currentVersion.setText(latestVersionString);
		myVersionFile = new File(myVersionFileString);
		bindirFile = new File(bindir);
		if (!bindirFile.exists())
			bindirFile.mkdirs();

		if (!myVersionFile.exists()) {
			boolean MyNoInternet = noInternet;
			initialStartupControls.setVisible(false);
			pluginFileBox.setVisible(false);
			new Thread(() -> {
				setupDefaultVersion();
				boolean globalpin = false;
				if (goloblaPinFile != null) {
					File gpinfile = goloblaPinFile.toFile();
					if (gpinfile.exists()) {
						globalpin = true;
					}
				}
				boolean runDef = MyNoInternet || globalpin;

				Platform.runLater(() -> {
					initialStartupControls.setVisible(true);
					pluginFileBox.setVisible(true);
					// uptodateButton.setDisable(noInternet);
					yesButton.setVisible(false);
					noButton.setVisible(false);
					if (runDef)
						onExtractLTS(null);
				});
			}).start();
			return;
		} else {
			initialStartupControls.setVisible(false);
			pluginFileBox.setVisible(false);
			yesButton.setVisible(true);
			noButton.setVisible(true);
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

		if (!noInternet) {
			if (myVersionString == null) {
				launchApplication();
				return;
			} else {
				if (myVersionString.equals(latestVersionString)) {
					launchApplication();
					return;
				}
				// Internet access available and an update is available
				// Allow user to download the update or just start the application
				infoBar.setText("An update is available.\nWould you like to download it now?");
				yesButton.setDisable(false);
				noButton.setDisable(false);
			}
		} else {
			onNo(null);
			return;
		}
	}

	private void runPluginProcess() {
		Stage progressStage = new Stage();
		Path homebin = Path.of(System.getProperty("user.home"), "bin");
		Path pluginDir = homebin.resolve("BowlerStudioInstall");
		if (pluginDir.toFile().exists())
			return;

		new Thread(() -> {
			if (downloadPlugins.isSelected()) {
				ProgressBar progressBar = new ProgressBar(0);
				progressBar.setPrefWidth(300);
				Label label = new Label("Starting download...");
				try {
					pluginsZip = Files.createTempFile("BowlerStudioInstall", ".zip");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String string = "https://github.com/CommonWealthRobotics/bowler-script-kernel/releases/download/3.1.8/BowlerStudioInstall-";
				String string2 = "";
				if (isMac()) {
					string2 = "macos-arm";
				}
				if (isLin())
					string2 = "linux";
				if (isWin())
					string2 = "windows";
				URI uri = URI.create(string + string2 + ".zip");

				Platform.runLater(() -> {
					VBox root = new VBox(10, label, progressBar);
					root.setPadding(new Insets(20));
					String css = getClass().getResource("/com/commonwealthrobotics/stylesheet.css").toExternalForm();
					root.getStylesheets().add(css);
					// progressStage.initOwner(stage);
					progressStage.initModality(Modality.APPLICATION_MODAL);
					progressStage.setTitle("Downloading Plugins...");
					progressStage.setScene(new Scene(root));
					progressStage.setResizable(false);
					progressStage.show();
				});

				HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

				HttpRequest request = HttpRequest.newBuilder().uri(uri).build();

				try {
					HttpResponse<InputStream> response = client.send(request,
							HttpResponse.BodyHandlers.ofInputStream());

					long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);

					try (InputStream in = response.body();
							OutputStream out = Files.newOutputStream(pluginsZip, StandardOpenOption.WRITE)) {

						byte[] buffer = new byte[81920];
						long totalRead = 0;
						int read;

						long lastUpdateTime = System.nanoTime();
						long lastUpdateBytes = 0;
						final long UPDATE_INTERVAL_NS = 100_000_000L; // update UI ~10x/sec

						while ((read = in.read(buffer)) != -1 && progressStage.isShowing()) {
							out.write(buffer, 0, read);
							totalRead += read;

							long now = System.nanoTime();
							if (now - lastUpdateTime >= UPDATE_INTERVAL_NS) {
								long finalTotalRead = totalRead;
								double elapsedSec = (now - lastUpdateTime) / 1_000_000_000.0;
								double intervalBytes = finalTotalRead - lastUpdateBytes;
								// bits per second / 1_000_000 = Mbps
								double speedMbps = elapsedSec > 0 ? (intervalBytes * 8.0 / 1_000_000.0) / elapsedSec
										: 0.0;

								lastUpdateTime = now;
								lastUpdateBytes = finalTotalRead;

								Platform.runLater(() -> {
									if (contentLength > 0) {
										double fraction = (double) finalTotalRead / contentLength;
										progressBar.setProgress(fraction);
										label.setText(String.format("%.3f / %.3f GB (%.1f%%) — %.2f Mbps",
												finalTotalRead / 1024.0 / 1024.0 / 1024.0,
												contentLength / 1024.0 / 1024.0 / 1024.0, fraction * 100, speedMbps));
									} else {
										progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
										label.setText(String.format("%,d KB downloaded — %.2f Mbps",
												finalTotalRead / 1024, speedMbps));
									}
								});
							}
						}

						// final update after loop ends, so the label doesn't stall short of 100%
						long finalTotalRead = totalRead;
						Platform.runLater(() -> {
							if (contentLength > 0) {
								double fraction = (double) finalTotalRead / contentLength;
								progressBar.setProgress(fraction);
								label.setText(String.format("%.3f / %.3f GB (100.0%%)",
										finalTotalRead / 1024.0 / 1024.0 / 1024.0,
										contentLength / 1024.0 / 1024.0 / 1024.0));
							}
						});
					}
				} catch (IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					Platform.runLater(progressStage::close);
				}
				pluginsZip.toFile().deleteOnExit();
			}
			if (pluginsZip.toFile().exists()) {
				try {
					Files.createDirectories(pluginDir);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					unzip(pluginsZip.toFile(), pluginDir.toString());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}).start();
	}

	public static void unzip(File path, String dir) throws Exception {
		Path destFolderPath = new File(dir).toPath();

		try (ZipFile zipFile = ZipFile.builder().setFile(path).get()) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry entry = entries.nextElement();
				Path entryPath = destFolderPath.resolve(entry.getName());
				if (entryPath.normalize().startsWith(destFolderPath.normalize())) {
					if (entry.isDirectory()) {
						Files.createDirectories(entryPath);
					} else {
						Files.createDirectories(entryPath.getParent());

						// Check timestamps before extracting
						File file = entryPath.toFile();
						file.getParentFile().mkdirs();
						File targetFile = file;
						boolean shouldExtract = false;

						if (!targetFile.exists()) {
							// File doesn't exist, extract it
							shouldExtract = true;
						} else {
							// File exists, compare timestamps
							long zipTime = entry.getTime();
							long diskTime = targetFile.lastModified();

							if (zipTime > diskTime) {
								// Zip file is newer, extract it
								shouldExtract = true;
								// Log.debug("Updating file (zip is newer): " + entryPath);
							} else {
								// Disk file is newer or same, skip extraction
								// Log.debug("Skipping file (disk is newer or same): " + entryPath);
							}
						}

						if (shouldExtract) {
							try (InputStream in = zipFile.getInputStream(entry)) {
								try {
									// ar.setExternalAttributes(entry.extraAttributes);
									if (entry.isUnixSymlink()) {
										String text = new BufferedReader(
												new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
												.collect(Collectors.joining("\n"));
										Path target = Paths.get(".", text);
										Files.createSymbolicLink(entryPath, target);
										continue;
									}
								} catch (Exception ex) {
									ex.printStackTrace();
								}
								try (OutputStream out = new FileOutputStream(file)) {
									IOUtils.copy(in, out);
									// com.neuronrobotics.sdk.common.Log.debug("Inflating " + entryPath);
								} catch (Exception ex) {
									// Log.error(ex);
								}
								if (isExecutable(entry)) {
									file.setExecutable(true);
								}
							}
						}
					}
				}
			}
		}
	}

	public static boolean isExecutable(ZipArchiveEntry entry) {
		int unixMode = entry.getUnixMode();
		// Check if any of the executable bits are set for user, group, or others.
		// User executable: 0100 (0x40), Group executable: 0010 (0x10), Others
		// executable: 0001 (0x01)
		return (unixMode & 0x49) != 0;
	}

	private static boolean isPosixCompliantSystem() {
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	private static Set<PosixFilePermission> getPosixPermissions(int mode) {
		StringBuilder permissions = new StringBuilder("rwxrwxrwx");
		for (int i = 0; i < 9; i++) {
			if ((mode & (1 << (8 - i))) == 0) {
				permissions.setCharAt(i, '-');
			}
		}
		return java.nio.file.attribute.PosixFilePermissions.fromString(permissions.toString());
	}
}

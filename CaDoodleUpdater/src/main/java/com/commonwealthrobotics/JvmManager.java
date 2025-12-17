package com.commonwealthrobotics;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.examples.Archiver;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class JvmManager {
	private static long timeSincePrint = System.currentTimeMillis();
	private static Label infoBar;

	public static String getCommandString(String project, String repo, String version, String downloadJsonURL,
			long sizeOfJson, ProgressBar progress, String bindir, Label info) throws Exception {
		if (version == null)
			version = "0.0.6";
		infoBar=info;
		File exe;
		File jvmArchive;
		File dest;
		try {
			exe = download(version, downloadJsonURL, sizeOfJson, progress, bindir, "jvm.json");
			Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
			}.getType();
			// chreat the gson object, this is the parsing factory
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			String jsonText = Files.readString(exe.toPath());

			HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
			String key = "UNKNOWN";
			key = discoverKey(key);
			Object rawKey = database.get(key);          // raw lookup
			if (!(rawKey instanceof Map))
				throw new IllegalStateException("Entry for key '" + key + "' is not a Map");
			@SuppressWarnings("unchecked")
			Map<String, Object> vm = (Map<String, Object>) rawKey;

			String baseURL = vm.get("url").toString();
			String type = vm.get("type").toString();
			String name = vm.get("name").toString();
			List<String> jvmargs = null;
			Object o = vm.get("jvmargs");
			if (o instanceof List) {
				@SuppressWarnings("unchecked")
				List<String> tmp = (List<String>) o;
				jvmargs = tmp;
			} else
				jvmargs = new ArrayList<String>();

			String jvmURL = baseURL + name + "." + type;
			jvmArchive = download("", jvmURL, 300000000, progress, bindir, name + "." + type);
			dest = new File(bindir + name);
			if (!dest.exists()) {
				if (type.toLowerCase().contains("zip")) {
					try {
						unzip(jvmArchive, bindir);
					} catch (java.util.zip.ZipException ex) {
						System.out.println("Failed the extract, erasing and re-downloading");
						jvmArchive.delete();
						ex.printStackTrace();
						return getCommandString(project, repo, version, downloadJsonURL, sizeOfJson, progress, bindir,info);
					}
				}
				if (type.toLowerCase().contains("tar.gz")) {
					untar(jvmArchive, bindir);
				}
			} else {
				System.out.println("Not extraction, VM exists " + dest.getAbsolutePath());
			}
			String cmd = bindir + name + "/bin/java" + (CadoodleUpdater.isWin() ? ".exe" : "") + " ";
			for (String s : jvmargs) {
				cmd += s + " ";
			}
			return cmd + " -jar ";
		} catch (java.io.EOFException ex) {
			ex.printStackTrace();
			System.err.println("JVM is currupted");
			System.exit(1);
		}
		return null;
	}

	private static String discoverKey(String key) {
		if (CadoodleUpdater.isLin()) {
			if (CadoodleUpdater.isArm()) {
				key = "Linux-aarch64";
			} else {
				key = "Linux-x64";
			}
		}

		if (CadoodleUpdater.isMac()) {
			if (CadoodleUpdater.isArm()) {
				key = "Mac-aarch64";
			} else {
				key = "Mac-x64";
			}
		}
		if (CadoodleUpdater.isWin()) {
			if (CadoodleUpdater.isArm()) {
				key = "UNKNOWN";
			} else {
				key = "Windows-x64";
			}
		}
		return key;
	}

	public static boolean isExecutable(ZipArchiveEntry entry) {
		int unixMode = entry.getUnixMode();
		// Check if any of the executable bits are set for user, group, or others.
		// User executable: 0100 (0x40), Group executable: 0010 (0x10), Others
		// executable: 0001 (0x01)
		return (unixMode & 0x49) != 0;
	}

	private static void unzip(File path, String dir) throws Exception {
		String fileBaseName = FilenameUtils.getBaseName(path.getName().toString());
		Path destFolderPath = new File(dir).toPath();
		Platform.runLater(()->infoBar.setText("Inflating Java Runtime..."));

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
						try (InputStream in = zipFile.getInputStream(entry)) {
							try {
								// ar.setExternalAttributes(entry.extraAttributes);
								if (entry.isUnixSymlink()) {
									String text = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
											.lines().collect(Collectors.joining("\n"));
									Path target = Paths.get(".", text);
									System.out.println("Creating symlink " + entryPath + " with " + target);

									Files.createSymbolicLink(entryPath, target);
									continue;
								}
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
								in.transferTo(out);
							}
							if (isExecutable(entry)) {
								entryPath.toFile().setExecutable(true);
							}
						}
					}
				}
			}
		}
	}

	public static void untar(File tarFile, String dir) throws Exception {
		System.out.println("Untaring " + tarFile.getName() + " into " + dir);
		Platform.runLater(()->infoBar.setText("Inflating Java Runtime..."));

		File dest = new File(dir);
		dest.mkdir();
		TarArchiveInputStream tarIn = null;
		try {
			tarIn = new TarArchiveInputStream(
					new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarFile))));
		} catch (java.io.IOException ex) {
			tarFile.delete();
			return;
		}
		TarArchiveEntry tarEntry = tarIn.getNextEntry();
		// tarIn is a TarArchiveInputStream
		while (tarEntry != null) {// create a file with the same name as the tarEntry
			File destPath = new File(dest.toString() + System.getProperty("file.separator") + tarEntry.getName());
			System.out.println("Inflating: " + destPath.getCanonicalPath());
			if (tarEntry.isDirectory()) {
				destPath.mkdirs();
			} else {
				destPath.createNewFile();
				FileOutputStream fout = new FileOutputStream(destPath);
				byte[] b = new byte[(int) tarEntry.getSize()];
				tarIn.read(b);
				fout.write(b);
				fout.close();
				int mode = tarEntry.getMode();
				b = new byte[5];
				TarUtils.formatUnsignedOctalString(mode, b, 0, 4);
				if (bits(b[1]).endsWith("1")) {
					destPath.setExecutable(true);
				}
			}
			tarEntry = tarIn.getNextEntry();
		}
		tarIn.close();
	}

	private static String bits(byte b) {
		return String.format("%6s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
	}

	private static File download(String version, String downloadJsonURL, long sizeOfJson, ProgressBar progress,
			String bindir, String filename) throws MalformedURLException, IOException, FileNotFoundException {
		File folder = new File(bindir + version + "/");
		File exe = new File(bindir + version + "/" + filename+"_TMP");
		File exeFinal = new File(bindir + version + "/" + filename);

		try {
			URL url = new URL(downloadJsonURL);
			URLConnection connection = url.openConnection();
			InputStream is = connection.getInputStream();
			ProcessInputStream pis = new ProcessInputStream(is, (int) sizeOfJson);
			pis.addListener(new Listener() {
				@Override
				public void process(double percent) {
					if(System.currentTimeMillis()-timeSincePrint>1000) {
						timeSincePrint=System.currentTimeMillis();
						 System.out.println("Download " + filename + " percent " + percent);

					}
					Platform.runLater(() -> {
						if(percent<100)
							progress.setProgress(percent);
					});
				}
			});

			if (!folder.exists() || !exeFinal.exists()) {
				if(exe.exists())
					exe.delete();
				System.out.println("Start Downloading " + filename);
				Platform.runLater(()->infoBar.setText("Downloading Java Runtime..."));
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
				System.out.println("Finished downloading " + filename);
			} else {
				System.out.println("Not downloadeing, it existst " + filename);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		System.out.println("Using JVM " + exeFinal.getAbsolutePath());
		if(exe.exists())
			Files.move(exe.toPath(), exeFinal.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return exeFinal;
	}
	
}

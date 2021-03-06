package com.github.beijingstrongbow.update;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.JOptionPane;

import com.github.beijingstrongbow.Main;
import com.github.beijingstrongbow.Main.ProgramState;
import com.github.beijingstrongbow.userinterface.LoadingProgressWindow;
import com.github.beijingstrongbow.userinterface.LoadingProgressWindow.ProgressMode;

public class UpdateHandler {
	
	private final String updateURL = "https://api.github.com/repos/BeijingStrongbow/CourseScheduleScraper/releases/latest";
			
	private final String updateScriptURL = "https://raw.githubusercontent.com/BeijingStrongbow/CourseScheduleScraper/master/Universal/target/";
	
	private final String versionFileName = "version.txt";
	
	private final String tempLocation = "tmp.jar";
	
	private final LoadingProgressWindow downloadProgress;
	
	/**
	 * Creates the version file if one doesn't already exist
	 */
	public UpdateHandler(LoadingProgressWindow downloadProgress) {
		this.downloadProgress = downloadProgress;
		File file = new File(getDefaultSavePath());
		boolean needVersionFile = true;
		boolean needUpdateScript = true;
		
		if(!file.exists()) {
			file.mkdirs();
		}
		
		String[] files = file.list();
		for(int i = 0; i < files.length; i++) {
			if(files[i].equals(versionFileName)) {
				needVersionFile = false;
			}
			if(files[i].equals(getUpdateScriptName())) {
				needUpdateScript = false;
			}
		}
		
		if(needVersionFile) {
			try {
				String json = getReleaseJSON();
				
				String version = getVersionFromJSON(json);
				
				File versionFile = new File(getDefaultSavePath() + versionFileName);
				PrintWriter output = new PrintWriter(versionFile);
				output.print(version);
				
				output.flush();
				output.close();
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}
		
		if(needUpdateScript) {
			try {
				downloadAndSaveFile(new URL(updateScriptURL + getUpdateScriptName()), new File(getDefaultSavePath() + getUpdateScriptName()), 0, false);
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * Check if an update is available
	 * 
	 * @return Whether there is an update
	 */
	public void update() {
				
		try {
			String json = getReleaseJSON();

			String download = getDownloadURLFromJSON(json);
			
			String version = getVersionFromJSON(json);
			
			String currentVersion = getVersion();
			
			String oldSave = getCurrentJARLocation();
			
			int downloadSize = getDownloadSizeFromJSON(json);
			if(!currentVersion.equals(version)) {
				int option = JOptionPane.showOptionDialog(null, "An update is available. Do you want to download it?", "Update", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
				if(option == 0) {
					downloadProgress.showWindow();
					downloadAndSaveFile(new URL(download), new File(getDefaultSavePath() + tempLocation), downloadSize, true);
					saveVersion(version);

					if(System.getProperty("os.name").toUpperCase().contains("WIN")) {
						Runtime.getRuntime().exec("\"" + getDefaultSavePath() + getUpdateScriptName() + "\" \"" + oldSave + "\" \"" + getDefaultSavePath() + tempLocation + "\"");
					}
					else if(System.getProperty("os.name").toUpperCase().contains("MAC") || System.getProperty("os.name").toUpperCase().contains("NUX")) {
						System.out.println("sh \"" + getDefaultSavePath() + getUpdateScriptName() + "\" \"/" + oldSave + "\" \"" + getDefaultSavePath() + tempLocation + "\"");
						Process cmd = Runtime.getRuntime().exec(new String[] {"sh", getDefaultSavePath() + getUpdateScriptName(), "/" + oldSave, getDefaultSavePath() + tempLocation});
						BufferedReader error = new BufferedReader(new InputStreamReader(cmd.getErrorStream()));
						
						String line;
						while((line = error.readLine()) != null) {
							System.out.println(line);
						}
					}
					
					System.exit(0);
				}
			}
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Get the JSON describing the latest release
	 * 
	 * @return The JSON describing the latest release
	 */
	private String getReleaseJSON() throws IOException {
		URL url = new URL(updateURL);
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		
		Scanner input = new Scanner(connection.getInputStream());
		
		String json = "";
		String line;

		while(input.hasNextLine()) {
			json += input.nextLine();
		}
		
		return json;
	}
	
	/**
	 * Download and save the updated JAR file or the update script
	 * 
	 * @param downloadURL The URL to download from
	 * @param saveFile The location to save the JAR file to
	 * @param downloadSize The size of the file that is being downloaded
	 * @param showProgress Whether to update the progress on the progress window
	 * @throws IOException
	 */
	private void downloadAndSaveFile(URL downloadURL, File saveFile, int downloadSize, boolean showProgress) throws IOException{
		
		HttpsURLConnection downloadConnection = (HttpsURLConnection) downloadURL.openConnection();
		downloadConnection.setRequestMethod("GET");

		BufferedInputStream input = new BufferedInputStream(downloadConnection.getInputStream());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		FileOutputStream fileOut = new FileOutputStream(saveFile);
		
		/*Thread fileWriter = new Thread(new Runnable() {

			@Override
			public void run() {
				long lastOutput = System.currentTimeMillis();
				
				while(System.currentTimeMillis() - lastOutput < 3000) {
					if(output.size() > 0) {
						try {
							fileOut.write(output.toByteArray());
							lastOutput = System.currentTimeMillis();
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
				}
			}
		});
		
		fileWriter.start();*/
		int byteRead;
		
		if(showProgress) {
			int byteCount = 0;
			double progressIncrement = (1.0 / downloadSize) * 100;
			while((byteRead = input.read()) != -1) {
				output.write(byteRead);
				byteCount++;
				downloadProgress.addProgress(progressIncrement);
			}
		}
		else {
			while((byteRead = input.read()) != -1) {
				output.write(byteRead);
			}
		}
		
		output.writeTo(fileOut);
		output.reset();
		
		fileOut.flush();
		fileOut.close();
		input.close();
	}
	
	/**
	 * Write the version that is currently installed locally
	 * 
	 * @param versionName The name of the file that is currently installed
	 */
	private void saveVersion(String versionName) throws IOException {
		File file = new File(getDefaultSavePath() + versionFileName);
		
		PrintWriter pw = new PrintWriter(file);
		
		pw.write(versionName);
		
		pw.flush();
		pw.close();
		
	}
	
	/**
	 * Get the current installed version of the program
	 * 
	 * @return The version that is currently installed
	 * @throws IOException
	 */
	private String getVersion() throws IOException {
		File file = new File(getDefaultSavePath() + versionFileName);
		
		Scanner input = new Scanner(file);
		
		String version = input.nextLine();
		
		return version;
	}
	
	/**
	 * Get the URL to download the JAR file from
	 * 
	 * @param json The json string received from GitHub
	 * @return The download URL
	 */
	private String getDownloadURLFromJSON(String json) {
		int fromIndex = json.indexOf("\"browser_download_url\"");
		int httpsIndex = json.indexOf("https", fromIndex);
		String download = json.substring(httpsIndex, json.indexOf('"', httpsIndex));
		
		return download;
	}
	
	/**
	 * Get the size of the JAR file that will be downloaded
	 * 
	 * @param json The json string recieved from GitHub
	 * @return The size of the JAR file
	 */
	private int getDownloadSizeFromJSON(String json) {
		int startIndex = json.indexOf("\"size\"") + 7;
		int endIndex = json.indexOf(',', startIndex);
		
		return Integer.parseInt(json.substring(startIndex, endIndex));
	}
	
	/**
	 * Get the current version out of the json received from GitHub
	 * 
	 * @param json The json string received from GitHub
	 * @return The current version, in the format ScheduleGenerator.vX.Y, where X and Y are integers
	 */
	private String getVersionFromJSON(String json) {
					
		String download = getDownloadURLFromJSON(json);
		
		StringBuilder fileName = new StringBuilder();
		
		char[] charArray = download.toCharArray();
		for(int i = download.length() - 5; i >= 0; i--) {
			if(charArray[i] != '/') {
				fileName.insert(0, charArray[i]);
			}
			else break;
		}
		
		return fileName.toString();
	}
	
	/**
	 * Get the save path for the version file on Windows, Mac, and Linux
	 * 
	 * @return The path for the version file
	 */
	private String getDefaultSavePath() {
		String operatingSystem = System.getProperty("os.name").toUpperCase();
		String path = "";
		if(operatingSystem.contains("WIN")) {
			path = System.getenv("APPDATA") + "\\ScheduleGenerator\\";
		}
		else if(operatingSystem.contains("MAC")) {
			path = System.getProperty("user.home") + "/Library/Application Support/ScheduleGenerator/";
		}
		else if(operatingSystem.contains("NUX")) {
			path = System.getProperty("user.home") + "/.config/ScheduleGenerator/";
		}
		return path;
	}
	
	/**
	 * Get the name of the update script on Windows, Mac, and Linux
	 * 
	 * @return The name of the update script
	 */
	private String getUpdateScriptName() {
		String operatingSystem = System.getProperty("os.name").toUpperCase();
		String name = "";
		if(operatingSystem.contains("WIN")) {
			name = "UpdateScriptWin.bat";
		}
		else if(operatingSystem.contains("MAC") || operatingSystem.contains("NUX")) {
			name = "UpdateScriptMac.sh";
		}
		return name;
	}
	
	/**
	 * Get the current path to the JAR file
	 * 
	 * @return The path to the JAR file
	 */
	private String getCurrentJARLocation() {
		try {
			return UpdateHandler.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath().substring(1);
		}
		catch(URISyntaxException ex) {
			ex.printStackTrace();
			return "";
		}
	}
}

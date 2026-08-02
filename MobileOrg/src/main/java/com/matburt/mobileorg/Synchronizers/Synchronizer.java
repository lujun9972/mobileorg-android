package com.matburt.mobileorg.Synchronizers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.matburt.mobileorg.Gui.FileDecryptionActivity;
import com.matburt.mobileorg.Gui.SynchronizerNotificationCompat;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileParser;
import com.matburt.mobileorg.OrgData.OrgIndexParser;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.FileUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.matburt.mobileorg.util.PreferenceUtils;
import com.matburt.mobileorg.Services.SyncService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.net.ssl.SSLHandshakeException;

/**
 * This class implements many of the operations that need to be done on
 * synching. Instead of using it directly, create a {@link SyncManager}.
 *
 * When implementing a new synchronizer, the methods {@link #isConfigured()},
 * {@link #putRemoteFile(String, String)} and {@link #getRemoteFile(String)} are
 * needed.
 */
public class Synchronizer {
	public static final String SYNC_UPDATE = "com.matburt.mobileorg.Synchronizer.action.SYNC_UPDATE";
	public static final String SYNC_DONE = "sync_done";
	public static final String SYNC_START = "sync_start";
	public static final String SYNC_PROGRESS_UPDATE = "progress_update";
	public static final String SYNC_SHOW_TOAST = "showToast";

	public static final String INDEX_FILE = "index.org";

	private Context context;
	private ContentResolver resolver;
	private SynchronizerInterface syncher;
	private SynchronizerNotificationCompat notify;
	private String syncDiag = "";

	public Synchronizer(Context context, SynchronizerInterface syncher, SynchronizerNotificationCompat notify) {
		this.context = context;
		this.resolver = context.getContentResolver();
		this.syncher = syncher;
		this.notify = notify;
	}

 	public boolean isEnabled() {
		return true;
	}

 	/**
 	 * @return List of files that where changed.
 	 */
	public ArrayList<String> runSynchronizer(OrgFileParser parser) {
		if (!syncher.isConfigured()) {
			notify.errorNotification("Sync not configured");
			return new ArrayList<String>();
		}

		if (!syncher.isConnectable()) {
			notify.errorNotification("No network connection available");
			return new ArrayList<String>();
		}

		syncDiag = "";
		try {
			announceStartSync();
			ArrayList<String> changedFiles = pull(parser);
			pushCaptures();
			announceSyncDone();
			String okMsg = "Sync OK: " + changedFiles.size() + " files updated\n" + syncDiag;
			showToast(okMsg);
			writeDebugLog(okMsg);
			return changedFiles;
		} catch (Exception e) {
			syncDiag += "\nERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
			showErrorNotification(e);
			Log.e("Synchronizer", "Error synchronizing", e);
			Log.d("MobileOrg", "[Sync] sync FAILED, sending SYNC_DONE broadcast from catch block");
			announceSyncDone(context);
			showToast("Sync FAILED\n" + syncDiag);
				writeDebugLog("Sync FAILED\n" + syncDiag);
			return new ArrayList<String>();
		}
	}

	private void showToast(final String msg) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
			}
		});
	}

	private void writeDebugLog(String msg) {
		MobileOrgApplication.log("SYNC: " + msg);
	}

	/**
	 * This method will fetch the local and the remote version of the capture
	 * file combine their content. This combined version is transfered to the
	 * remote.
	 */
	public void pushCaptures() throws IOException,
			CertificateException, SSLHandshakeException {
		final String filename = FileUtils.CAPTURE_FILE;

		notify.updateNotification("Uploading captures");

		String localContents = "";

		try {
			OrgFile file = new OrgFileRepository(resolver).getByFilename(filename);
			localContents += new OrgFileRepository(resolver).nodesToString(file);
		} catch (OrgFileNotFoundException e) {}

		localContents += OrgEdit.editsToString(resolver);

		if (localContents.equals(""))
			return;
		String remoteContent = FileUtils.read(syncher.getRemoteFile(filename));

		if (remoteContent.indexOf("{\"error\":") == -1)
			localContents = remoteContent + "\n" + localContents;

		syncher.putRemoteFile(filename, localContents);

		try {
			OrgFileRepository fileRepo = new OrgFileRepository(resolver);
			fileRepo.removeFile(fileRepo.getByFilename(filename));
		} catch (OrgFileNotFoundException e) {}

		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(Files.buildFilenameUri(filename), null, null);
	}

	/**
	 * This method will download index.org and checksums.dat from the remote
	 * host. Using those files, it determines the other files that need updating
	 * and downloads them.
	 * @return
	 */
	public ArrayList<String> pull(OrgFileParser parser) throws SSLHandshakeException, CertificateException, IOException {
		HashMap<String,String> remoteChecksums = getAndParseChecksumFile();
		syncDiag += "checksums: " + remoteChecksums.size() + " entries\n";
		Log.i("MobileOrg", "Sync: remoteChecksums count=" + remoteChecksums.size()
				+ ", entries=" + remoteChecksums.keySet());

		HashMap<String, String> localChecksums = new OrgFileRepository(resolver).getFileChecksums();
			ArrayList<String> changedFiles = getFilesThatChangedRemotely(remoteChecksums, localChecksums);
		syncDiag += "changed: " + changedFiles.size() + " files" +
				(changedFiles.isEmpty() ? "" : " " + changedFiles) + "\n";
		Log.i("MobileOrg", "Sync: changedFiles=" + changedFiles.size()
				+ ", files=" + changedFiles);

		announceProgressDownload(INDEX_FILE, 0, changedFiles.size() + 2);
		HashMap<String,String> filenameMap = getAndParseIndexFile();
		syncDiag += "index.org: " + filenameMap.size() + " files listed\n";
		Log.i("MobileOrg", "Sync: index parsed, filenameMap=" + filenameMap.size()
				+ ", entries=" + filenameMap.keySet());

		// Remove local files that no longer exist on remote
		removeRemoteDeletedFiles(filenameMap, localChecksums);

		// Only download files that are listed in index.org
		// (checksums.dat may reference files removed from index)
		changedFiles.retainAll(filenameMap.keySet());

		if(changedFiles.size() == 0) {
			Log.i("MobileOrg", "Sync: no changed files to download");
			return changedFiles;
		}

		changedFiles.remove(INDEX_FILE);

		Collections.sort(changedFiles, String::compareToIgnoreCase);

		pull(parser, changedFiles, filenameMap, remoteChecksums);
		announceProgressDownload("", changedFiles.size() + 1, changedFiles.size() + 2);

		return changedFiles;
	}

	private void pull(OrgFileParser parser, ArrayList<String> filesToGet,
			HashMap<String, String> filenameMap,
			HashMap<String, String> remoteChecksums)
			throws SSLHandshakeException, CertificateException, IOException {
		final int totalNumberOfFiles = filesToGet.size() + 2;
		int fileIndex = 1;
		for (String filename : filesToGet) {
			announceProgressDownload(filename, fileIndex++, totalNumberOfFiles);
			Log.d("MobileOrg", context.getString(R.string.downloading) +
					" " + filename + "/" + filenameMap.get(filename));

			OrgFile orgFile = new OrgFile(filename, filenameMap.get(filename),
					remoteChecksums.get(filename));
			getAndParseFile(orgFile, parser);
		}
	}

	private HashMap<String, String> getAndParseIndexFile() throws SSLHandshakeException, CertificateException, IOException {
		String remoteIndexContents = FileUtils.read(syncher.getRemoteFile(INDEX_FILE));
		syncDiag += "index.org: " + remoteIndexContents.length() + " chars\n";
		Log.i("MobileOrg", "Sync: index.org length=" + remoteIndexContents.length()
				+ ", preview=" + remoteIndexContents.substring(0, Math.min(200, remoteIndexContents.length())));
		new OrgFileRepository(resolver).setTodos(
				OrgIndexParser.getTodosFromIndex(remoteIndexContents));
		new OrgFileRepository(resolver).setPriorities(
				OrgIndexParser.getPrioritiesFromIndex(remoteIndexContents));
		new OrgFileRepository(resolver).setTags(
				OrgIndexParser.getTagsFromIndex(remoteIndexContents));
		HashMap<String, String> filenameMap = OrgIndexParser
				.getFilesFromIndex(remoteIndexContents);
		return filenameMap;
	}

	private HashMap<String, String> getAndParseChecksumFile() throws SSLHandshakeException, CertificateException, IOException {
		String remoteChecksumContents = FileUtils.read(syncher.getRemoteFile("checksums.dat"));
		syncDiag += "checksums.dat: " + remoteChecksumContents.length() + " chars\n";
		Log.i("MobileOrg", "Sync: checksums.dat length=" + remoteChecksumContents.length()
				+ ", content=" + remoteChecksumContents.substring(0, Math.min(500, remoteChecksumContents.length())));

		HashMap<String, String> remoteChecksums = OrgIndexParser
				.getChecksums(remoteChecksumContents);
		return remoteChecksums;
	}

	private ArrayList<String> getFilesThatChangedRemotely(HashMap<String, String> remoteChecksums, HashMap<String, String> localChecksums) {
		syncDiag += "local: " + localChecksums.size() + " files in DB\n";

		ArrayList<String> filesToGet = new ArrayList<String>();

		for (String key : remoteChecksums.keySet()) {
			if (localChecksums.containsKey(key)
					&& localChecksums.get(key).equals(remoteChecksums.get(key)))
				continue;
			filesToGet.add(key);
		}

		filesToGet.remove(FileUtils.CAPTURE_FILE);

		return filesToGet;
	}

	/**
	 * Remove local files that no longer exist on the remote server.
	 * Compares local DB files against the remote index.org file list
	 * and removes any local-only files (except capture and agenda files).
	 */
	private void removeRemoteDeletedFiles(HashMap<String, String> remoteFileMap, HashMap<String, String> localChecksums) {

		for (String localFile : localChecksums.keySet()) {
			if (localFile.equals(FileUtils.CAPTURE_FILE) || localFile.equals(OrgFile.AGENDA_FILE))
				continue;

			if (!remoteFileMap.containsKey(localFile)) {
				Log.i("MobileOrg", "Sync: removing locally deleted remote file: " + localFile);
				syncDiag += "removed: " + localFile + "\n";
				try {
					OrgFileRepository fileRepo = new OrgFileRepository(resolver);
					fileRepo.removeFile(fileRepo.getByFilename(localFile));
				} catch (OrgFileNotFoundException e) {
					// already gone
				}
			}
		}
	}

	private void getAndParseFile(OrgFile orgFile, OrgFileParser parser)
			throws CertificateException, IOException {
		Log.v("getter","parsing : "+orgFile);
		BufferedReader breader = syncher.getRemoteFile(orgFile.filename);

		// TODO Generate checksum of file and compare to remoteChecksum

		try {
			OrgFileRepository fileRepo = new OrgFileRepository(resolver);
			fileRepo.removeFile(fileRepo.getByFilename(orgFile.filename));
		} catch (OrgFileNotFoundException e) { /* file did not exist */ }

		if (orgFile.isEncrypted())
        	decryptAndParseFile(orgFile, breader);
        else {
        	parser.parse(orgFile, breader, this.context);
        }
	}

	private void decryptAndParseFile(OrgFile orgFile, BufferedReader reader) {
		try {
			Intent intent = new Intent(context, FileDecryptionActivity.class);
			intent.putExtra("data", FileUtils.read(reader).getBytes());
			intent.putExtra("filename", orgFile.filename);
			intent.putExtra("filenameAlias", orgFile.name);
			intent.putExtra("checksum", orgFile.checksum);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent);
		} catch(IOException e) {}
	}


	private void announceStartSync() {
		Log.d("MobileOrg", "[Sync] announceStartSync: sending SYNC_START broadcast");
		notify.setupNotification();
		announceSyncStart(context);
	}

	private void announceProgressUpdate(int progress, String message) {
		if(message != null && !TextUtils.isEmpty(message))
			notify.updateNotification(progress, message);
		else
			notify.updateNotification(progress);
		announceSyncUpdateProgress(progress, context);
	}

	private void announceProgressDownload(String filename, int fileIndex, int totalFiles) {
		int progress = 0;
		if (totalFiles > 0)
			progress = (100 / totalFiles) * fileIndex;
		String message = context.getString(R.string.downloading) + " " + filename;
		announceProgressUpdate(progress, message);
	}

	private void showErrorNotification(Exception exception) {
		notify.finalizeNotification();

		String errorMessage = "";
        if (CertificateException.class.isInstance(exception)) {
			errorMessage = "Certificate Error occured during sync: "
					+ exception.getLocalizedMessage();
		} else {
			errorMessage = "Error: " + exception.getLocalizedMessage();
		}

		notify.errorNotification(errorMessage);
	}

	private void announceSyncDone() {
		announceProgressUpdate(100, "Done synchronizing");
		notify.finalizeNotification();
		Log.d("MobileOrg", "[Sync] announceSyncDone: sending SYNC_DONE broadcast");
		announceSyncDone(context);
	}

	public void close() {
		syncher.postSynchronize();
	}

	// =====================================================================
	// Sync state broadcast (moved from OrgUtils)
	// =====================================================================

	public static void announceSyncDone(Context context) {
		// Clear flag BEFORE broadcasting so that onPrepareOptionsMenu,
		// which reads isSyncRunning, does not restore the animation
		// after stopSyncAnimation has already cleared it.
		SyncService.isSyncRunning = false;
		Intent intent = new Intent(SYNC_UPDATE);
		intent.putExtra(SYNC_DONE, true);
		intent.setPackage(context.getPackageName());
		context.sendBroadcast(intent);
	}

	public static void announceSyncStart(Context context) {
		Intent intent = new Intent(SYNC_UPDATE);
		intent.putExtra(SYNC_START, true);
		intent.setPackage(context.getPackageName());
		context.sendBroadcast(intent);
	}

	public static void announceSyncUpdateProgress(int progress, Context context) {
		Intent intent = new Intent(SYNC_UPDATE);
		intent.putExtra(SYNC_PROGRESS_UPDATE, progress);
		intent.setPackage(context.getPackageName());
		context.sendBroadcast(intent);
	}

	// =====================================================================
	// Network connectivity (moved from OrgUtils)
	// =====================================================================

	public static boolean isWifiOnline(Context context) {
		ConnectivityManager conMan = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo.State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
				.getState();

		return wifi == NetworkInfo.State.CONNECTED;
	}

	public static boolean isMobileOnline(Context context) {
		ConnectivityManager conMan = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo.State mobile = conMan.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
				.getState();

		return mobile == NetworkInfo.State.CONNECTED;
	}

	public static boolean isNetworkOnline(Context context) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		boolean wifiOnly = prefs.getBoolean(
				context.getResources().getString(R.string.key_syncWifiOnly),
				false);

		if (wifiOnly)
			return isWifiOnline(context);
		else
			return isWifiOnline(context) || isMobileOnline(context);
	}
}

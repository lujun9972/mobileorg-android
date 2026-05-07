# Org-mobile Protocol Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add org-mobile protocol support via SSH/SFTP so MobileOrg Android works with Emacs's `org-mobile-push`/`org-mobile-pull` workflow, alongside existing Git sync mode.

**Architecture:** New `SftpWrapper` class wraps JSch `ChannelSftp` for file transfer. `OrgMobileSync` implements the org-mobile protocol (pull: index.org + checksums flow; push: generate mobileorg.org with F(edit:what) entries). `OrgMobileEditCache` caches node original values in SharedPreferences for edit tracking. `SSHSynchronizer` branches on sync mode stored in SharedPreferences key `ssh_sync_mode`.

**Tech Stack:** JSch 0.1.50 (already a dependency), SharedPreferences, existing OrgFileParser/OrgNode/OrgProviderUtils.

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `synchronizers/SftpWrapper.java` | Create | SFTP download/upload via JSch ChannelSftp |
| `synchronizers/OrgMobileSync.java` | Create | Org-mobile protocol pull/push orchestration |
| `synchronizers/OrgMobileEditCache.java` | Create | Cache node original values, generate edit entries |
| `synchronizers/SSHSynchronizer.java` | Modify | Branch on sync mode (git vs orgmobile) |
| `gui/wizard/wizards/SSHWizard.java` | Modify | Add mode selector, skip JGit clone in orgmobile mode |
| `res/layout/wizard_ssh.xml` | Modify | Add mode RadioGroup |
| `res/values/strings.xml` | Modify | Add new string resources |

---

### Task 1: Create SftpWrapper

**Files:**
- Create: `MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/SftpWrapper.java`

This class wraps JSch ChannelSftp for file transfer operations. It reuses the existing SSH config from AuthData.

- [ ] **Step 1: Create SftpWrapper.java**

```java
package com.matburt.mobileorg.synchronizers;

import android.content.Context;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * SFTP wrapper using JSch ChannelSftp.
 * Reuses AuthData for SSH connection config.
 */
public class SftpWrapper {
    private ChannelSftp channel;
    private Session session;

    public SftpWrapper(Context context) {
        AuthData authData = AuthData.getInstance(context);

        JSch jsch = new JSch();
        try {
            if (!authData.usePassword()) {
                jsch.addIdentity(AuthData.getPrivateKeyPath(context));
            }

            session = jsch.getSession(
                    authData.getUser(),
                    authData.getHost(),
                    authData.getPort());

            if (authData.usePassword()) {
                session.setPassword(authData.getPassword());
            }

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            Channel ch = session.openChannel("sftp");
            ch.connect(10000);
            channel = (ChannelSftp) ch;
        } catch (JSchException e) {
            disconnect();
            throw new RuntimeException("SFTP connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Download remote file to local file.
     */
    public void download(String remotePath, String localPath) {
        try {
            OutputStream os = new FileOutputStream(localPath);
            channel.get(remotePath, os);
            os.close();
        } catch (Exception e) {
            throw new RuntimeException("SFTP download failed: " + remotePath, e);
        }
    }

    /**
     * Upload local file to remote path.
     */
    public void upload(String localPath, String remotePath) {
        try {
            InputStream is = new FileInputStream(localPath);
            channel.put(is, remotePath);
            is.close();
        } catch (Exception e) {
            throw new RuntimeException("SFTP upload failed: " + remotePath, e);
        }
    }

    /**
     * Download remote file and return content as String.
     */
    public String downloadToString(String remotePath) {
        try {
            InputStream is = channel.get(remotePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SFTP download to string failed: " + remotePath, e);
        }
    }

    /**
     * Upload string content as a remote file.
     */
    public void uploadFromString(String content, String remotePath) {
        try {
            InputStream is = new java.io.ByteArrayInputStream(content.getBytes("UTF-8"));
            channel.put(is, remotePath);
            is.close();
        } catch (Exception e) {
            throw new RuntimeException("SFTP upload from string failed: " + remotePath, e);
        }
    }

    /**
     * Check if remote file exists.
     */
    public boolean exists(String remotePath) {
        try {
            channel.stat(remotePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void disconnect() {
        if (channel != null) {
            channel.disconnect();
            channel = null;
        }
        if (session != null) {
            session.disconnect();
            session = null;
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (SftpWrapper is not yet referenced, so no compile errors)

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/SftpWrapper.java
git commit -m "feat: add SftpWrapper for SFTP file transfer via JSch"
```

---

### Task 2: Create OrgMobileEditCache

**Files:**
- Create: `MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/OrgMobileEditCache.java`

This class caches node original values after pull and generates F(edit:what) entries for push. Uses SharedPreferences with JSON serialization.

- [ ] **Step 1: Create OrgMobileEditCache.java**

```java
package com.matburt.mobileorg.synchronizers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.matburt.mobileorg.orgdata.OrgContract.OrgData;
import com.matburt.mobileorg.orgdata.OrgFile;
import com.matburt.mobileorg.orgdata.OrgNode;
import com.matburt.mobileorg.orgdata.OrgProviderUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Caches original node values after pull and generates F(edit:what) entries
 * for mobileorg.org push. Uses SharedPreferences with JSON serialization.
 */
public class OrgMobileEditCache {
    private static final String PREF_KEY_EDIT_CACHE = "orgmobile_edit_cache";
    private Context context;
    private ContentResolver resolver;

    public OrgMobileEditCache(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    /**
     * Save current state of all nodes as baseline for future edit detection.
     * Called after pull completes and files are parsed into DB.
     */
    public void cacheAllNodes() {
        JSONObject cache = new JSONObject();
        List<OrgNode> topNodes = OrgProviderUtils.getFileNodes(context);

        for (OrgNode fileNode : topNodes) {
            cacheNodeTree(fileNode.id, cache);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_KEY_EDIT_CACHE, cache.toString()).apply();
    }

    private void cacheNodeTree(long nodeId, JSONObject cache) {
        try {
            OrgNode node = new OrgNode(nodeId, resolver);
            String nodeIdentifier = node.getNodeId(resolver);
            if (TextUtils.isEmpty(nodeIdentifier)) return;

            JSONObject values = new JSONObject();
            values.put("todo", node.todo != null ? node.todo : "");
            values.put("tags", node.tags != null ? node.tags : "");
            values.put("priority", node.priority != null ? node.priority : "");
            values.put("heading", node.name != null ? node.name : "");
            values.put("body", node.getPayload());
            cache.put(nodeIdentifier, values);

            // Recurse into children
            for (OrgNode child : node.getChildren(resolver)) {
                cacheNodeTree(child.id, cache);
            }
        } catch (OrgNodeNotFoundException e) {
            // Skip missing nodes
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compare current DB state with cached values and generate mobileorg.org content.
     * Returns the complete content for mobileorg.org, or empty string if no changes.
     */
    public String generateEdits() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String cacheStr = prefs.getString(PREF_KEY_EDIT_CACHE, "");
        if (TextUtils.isEmpty(cacheStr)) return "";

        JSONObject cache;
        try {
            cache = new JSONObject(cacheStr);
        } catch (JSONException e) {
            return "";
        }

        StringBuilder edits = new StringBuilder();
        List<OrgNode> topNodes = OrgProviderUtils.getFileNodes(context);

        for (OrgNode fileNode : topNodes) {
            generateEditsForTree(fileNode.id, cache, edits);
        }

        // Detect deleted nodes: nodes in cache but no longer in DB
        detectDeletedNodes(cache, edits);

        return edits.toString();
    }

    private void generateEditsForTree(long nodeId, JSONObject cache, StringBuilder edits) {
        try {
            OrgNode node = new OrgNode(nodeId, resolver);
            String nodeIdentifier = node.getNodeId(resolver);
            if (TextUtils.isEmpty(nodeIdentifier)) {
                // New node without ID — treat as capture
                edits.append(formatCapture(node));
                return;
            }

            if (cache.has(nodeIdentifier)) {
                // Existing node — check for edits
                JSONObject cached = cache.getJSONObject(nodeIdentifier);
                generateEditEntries(node, nodeIdentifier, cached, edits);
            } else {
                // New node with ID — treat as capture
                edits.append(formatCapture(node));
            }

            // Recurse into children
            for (OrgNode child : node.getChildren(resolver)) {
                generateEditsForTree(child.id, cache, edits);
            }
        } catch (OrgNodeNotFoundException e) {
            // Skip
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void generateEditEntries(OrgNode node, String nodeIdentifier,
                                     JSONObject cached, StringBuilder edits) throws JSONException {
        String cachedTodo = cached.optString("todo", "");
        String cachedTags = cached.optString("tags", "");
        String cachedPriority = cached.optString("priority", "");
        String cachedHeading = cached.optString("heading", "");
        String cachedBody = cached.optString("body", "");

        String currentTodo = node.todo != null ? node.todo : "";
        String currentTags = node.tags != null ? node.tags : "";
        String currentPriority = node.priority != null ? node.priority : "";
        String currentHeading = node.name != null ? node.name : "";
        String currentBody = node.getPayload();

        String title = node.name != null ? node.name : "";

        if (!currentTodo.equals(cachedTodo)) {
            edits.append(formatEdit("todo", nodeIdentifier, title, cachedTodo, currentTodo));
        }
        if (!currentTags.equals(cachedTags)) {
            edits.append(formatEdit("tags", nodeIdentifier, title, cachedTags, currentTags));
        }
        if (!currentPriority.equals(cachedPriority)) {
            edits.append(formatEdit("priority", nodeIdentifier, title, cachedPriority, currentPriority));
        }
        if (!currentHeading.equals(cachedHeading)) {
            edits.append(formatEdit("heading", nodeIdentifier, title, cachedHeading, currentHeading));
        }
        if (!currentBody.equals(cachedBody)) {
            edits.append(formatEdit("body", nodeIdentifier, title, cachedBody, currentBody));
        }
    }

    private void detectDeletedNodes(JSONObject cache, StringBuilder edits) {
        // Build set of current node identifiers
        List<OrgNode> topNodes = OrgProviderUtils.getFileNodes(context);
        java.util.HashSet<String> currentIds = new java.util.HashSet<>();
        for (OrgNode fileNode : topNodes) {
            collectNodeIds(fileNode.id, currentIds);
        }

        // Check cached nodes that no longer exist
        Iterator<String> keys = cache.keys();
        while (keys.hasNext()) {
            String nodeId = keys.next();
            if (!currentIds.contains(nodeId)) {
                try {
                    JSONObject cached = cache.getJSONObject(nodeId);
                    String title = cached.optString("heading", "");
                    edits.append(formatEdit("delete", nodeId, title, "", ""));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void collectNodeIds(long nodeId, java.util.HashSet<String> ids) {
        try {
            OrgNode node = new OrgNode(nodeId, resolver);
            String nodeIdentifier = node.getNodeId(resolver);
            if (!TextUtils.isEmpty(nodeIdentifier)) {
                ids.add(nodeIdentifier);
            }
            for (OrgNode child : node.getChildren(resolver)) {
                collectNodeIds(child.id, ids);
            }
        } catch (OrgNodeNotFoundException e) {
            // Skip
        }
    }

    /**
     * Format a new capture entry:
     * * Title
     * ** content
     */
    private String formatCapture(OrgNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("* ").append(node.name != null ? node.name : "").append("\n");
        String body = node.getPayload().trim();
        if (!TextUtils.isEmpty(body)) {
            sb.append("** ").append(body).append("\n");
        }
        return sb.toString();
    }

    /**
     * Format an edit entry:
     * * F(edit:what) [[id:uuid][title]]
     * ** Old value
     * old value
     * ** New value
     * new value
     */
    private String formatEdit(String editType, String nodeIdentifier,
                              String title, String oldVal, String newVal) {
        StringBuilder sb = new StringBuilder();
        sb.append("* F(edit:").append(editType).append(") [[")
          .append(nodeIdentifier).append("][").append(title).append("]]\n");
        sb.append("** Old value\n");
        sb.append(oldVal).append("\n");
        sb.append("** New value\n");
        sb.append(newVal).append("\n");
        return sb.toString();
    }

    /**
     * Clear the edit cache.
     */
    public void clear() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(PREF_KEY_EDIT_CACHE).apply();
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/OrgMobileEditCache.java
git commit -m "feat: add OrgMobileEditCache for edit tracking in org-mobile protocol"
```

---

### Task 3: Create OrgMobileSync

**Files:**
- Create: `MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/OrgMobileSync.java`

Implements the org-mobile protocol: pull (download index.org + checksums + changed files) and push (generate mobileorg.org with edits). Returns `SyncResult` that integrates with the base `Synchronizer.runSynchronizer()` flow.

- [ ] **Step 1: Create OrgMobileSync.java**

```java
package com.matburt.mobileorg.synchronizers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.matburt.mobileorg.orgdata.OrgFileParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Implements org-mobile protocol via SFTP.
 * Pull: download index.org, checksums, changed files.
 * Push: generate and upload mobileorg.org with edits.
 */
public class OrgMobileSync {
    private static final String PREF_KEY_CHECKSUMS = "orgmobile_checksums";
    public static final String ORGMOBILE_DIR = "orgmobile_dir";

    private Context context;
    private AuthData authData;
    private OrgMobileEditCache editCache;

    public OrgMobileSync(Context context) {
        this.context = context;
        this.authData = AuthData.getInstance(context);
        this.editCache = new OrgMobileEditCache(context);
    }

    /**
     * Push local edits to remote as mobileorg.org.
     * Must be called BEFORE pull, while current DB state is still valid.
     */
    public void push(SftpWrapper sftp) {
        String remotePath = getRemotePath();

        // Generate mobileorg.org content from edits
        String content = editCache.generateEdits();
        if (TextUtils.isEmpty(content)) return;

        // Upload to remote
        String mobileorgPath = remotePath;
        if (!mobileorgPath.endsWith("/")) mobileorgPath += "/";
        mobileorgPath += "mobileorg.org";

        sftp.uploadFromString(content, mobileorgPath);
    }

    /**
     * Pull remote changes: download index.org, checksums, and changed files.
     * Returns SyncResult for base class to parse into DB.
     */
    public SyncResult pull(SftpWrapper sftp) {
        SyncResult result = new SyncResult();
        String remotePath = getRemotePath();
        if (!remotePath.endsWith("/")) remotePath += "/";
        String localDir = getLocalDir();

        // Ensure local directory exists
        new File(localDir).mkdirs();

        // 1. Download index.org
        String indexContent;
        try {
            indexContent = sftp.downloadToString(remotePath + "index.org");
        } catch (Exception e) {
            result.setState(SyncResult.State.kFailed);
            return result;
        }

        // 2. Parse file list from index
        HashMap<String, String> remoteFiles = OrgFileParser.getFilesFromIndex(indexContent);
        if (remoteFiles.isEmpty()) {
            result.setState(SyncResult.State.kFailed);
            return result;
        }

        // 3. Download checksums.dat
        HashMap<String, String> remoteChecksums = new HashMap<>();
        try {
            String checksumsContent = sftp.downloadToString(remotePath + "checksums.dat");
            remoteChecksums = OrgFileParser.getChecksums(checksumsContent);
        } catch (Exception e) {
            // No checksums file — treat all files as new
        }

        // 4. Load cached checksums
        HashMap<String, String> cachedChecksums = loadCachedChecksums();

        // 5. Determine changed/new/deleted files
        HashSet<String> currentRemoteFileNames = new HashSet<>(remoteFiles.keySet());
        HashSet<String> cachedFileNames = new HashSet<>(cachedChecksums.keySet());

        for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
            String filename = entry.getKey();
            String remoteChecksum = remoteChecksums.get(filename);

            if (!cachedFileNames.contains(filename)) {
                // New file
                result.newFiles.add(filename);
            } else if (remoteChecksum != null && !remoteChecksum.equals(cachedChecksums.get(filename))) {
                // Changed file
                result.changedFiles.add(filename);
            }
            // else: unchanged, skip
        }

        // Deleted files: in cache but not in remote index
        for (String cachedFile : cachedFileNames) {
            if (!currentRemoteFileNames.contains(cachedFile)) {
                result.deletedFiles.add(cachedFile);
            }
        }

        // 6. Download changed/new files
        for (String filename : result.newFiles) {
            try {
                sftp.download(remotePath + filename, localDir + "/" + filename);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (String filename : result.changedFiles) {
            try {
                sftp.download(remotePath + filename, localDir + "/" + filename);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 7. Save new checksums
        saveCachedChecksums(remoteChecksums);

        result.setState(SyncResult.State.kSuccess);
        return result;
    }

    /**
     * Cache current node values after pull completes.
     * Call after base class has parsed files into DB.
     */
    public void cacheCurrentState() {
        editCache.cacheAllNodes();
    }

    private String getRemotePath() {
        return authData.getPath();
    }

    public String getLocalDir() {
        return context.getFilesDir() + "/" + ORGMOBILE_DIR;
    }

    // --- Checksum cache ---

    private HashMap<String, String> loadCachedChecksums() {
        HashMap<String, String> checksums = new HashMap<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(PREF_KEY_CHECKSUMS, "");
        if (TextUtils.isEmpty(json)) return checksums;

        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                checksums.put(key, obj.getString(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return checksums;
    }

    private void saveCachedChecksums(HashMap<String, String> checksums) {
        JSONObject obj = new JSONObject(checksums);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_KEY_CHECKSUMS, obj.toString()).apply();
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/OrgMobileSync.java
git commit -m "feat: add OrgMobileSync for org-mobile protocol pull/push"
```

---

### Task 4: Modify SSHSynchronizer — Add Mode Branching

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/SSHSynchronizer.java`

Branch `synchronize()` and `getRelativeFilesDir()` based on `ssh_sync_mode` preference. In org-mobile mode, use `OrgMobileSync` instead of JGit.

- [ ] **Step 1: Modify SSHSynchronizer.java**

Replace the full contents with:

```java
package com.matburt.mobileorg.synchronizers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.matburt.mobileorg.util.FileUtils;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

public class SSHSynchronizer extends Synchronizer {
    private final String LT = "MobileOrg";
    AuthData authData;
    private Session session;

    public SSHSynchronizer(Context context) {
        super(context);
        this.context = context;
        authData = AuthData.getInstance(context);
    }

    @Override
    public String getRelativeFilesDir() {
        if (isOrgMobileMode()) {
            return OrgMobileSync.ORGMOBILE_DIR;
        }
        return JGitWrapper.GIT_DIR;
    }

    @Override
    public boolean isConfigured() {
        return !(authData.getPath().equals("")
                || authData.getUser().equals("")
                || authData.getHost().equals("")
                || authData.getPassword().equals("")
                && AuthData.getPublicKey(context).equals(""));
    }

    public void connect() {
        try {
            SshSessionFactory sshSessionFactory = new SshSessionFactory(context);
            JSch jSch = sshSessionFactory.createDefaultJSch(FS.detect());

            session = jSch.getSession(
                    authData.getUser(),
                    authData.getHost(),
                    authData.getPort());

            session.setPassword(AuthData.getInstance(context).getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            session.disconnect();
        } catch (JSchException e) {
            e.printStackTrace();
        }
    }

    public SyncResult synchronize(){
        if (isCredentialsRequired()) return new SyncResult();

        if (isOrgMobileMode()) {
            return synchronizeOrgMobile();
        }

        return synchronizeGit();
    }

    private SyncResult synchronizeGit() {
        SyncResult pullResult = JGitWrapper.pull(context);
        new JGitWrapper.PushTask(context).execute();
        return pullResult;
    }

    private SyncResult synchronizeOrgMobile() {
        OrgMobileSync orgMobile = new OrgMobileSync(context);
        SftpWrapper sftp = null;
        try {
            sftp = new SftpWrapper(context);

            // Push first: upload our edits before pulling new data
            orgMobile.push(sftp);

            // Pull: download changed files
            SyncResult result = orgMobile.pull(sftp);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new SyncResult();
        } finally {
            if (sftp != null) sftp.disconnect();
        }
    }

    /**
     * After pull + parse completes, cache current node state for edit tracking.
     * Called from runSynchronizer() flow via postSynchronize().
     */
    @Override
    public void postSynchronize() {
        if (isOrgMobileMode()) {
            OrgMobileSync orgMobile = new OrgMobileSync(context);
            orgMobile.cacheCurrentState();
        }

        if (this.session != null)
            this.session.disconnect();
    }

    public boolean isCredentialsRequired() {
        return false;
    }

    @Override
    public void addFile(String filename) {
        if (isOrgMobileMode()) {
            // In org-mobile mode, files are managed by Emacs
            return;
        }
        JGitWrapper.add(filename, context);
    }

    @Override
    public boolean isConnectable() throws Exception {
        return com.matburt.mobileorg.util.OrgUtils.isNetworkOnline(context);
    }

    @Override
    public void clearRepository(Context context) {
        File dir = new File(getAbsoluteFilesDir(context));
        for (File file : dir.listFiles()) {
            if (file.getName().equals(".git")) continue;
            file.delete();
        }
    }

    private boolean isOrgMobileMode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return "orgmobile".equals(prefs.getString("ssh_sync_mode", "git"));
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/synchronizers/SSHSynchronizer.java
git commit -m "feat: SSHSynchronizer branches on sync mode (git vs orgmobile)"
```

---

### Task 5: Add String Resources

**Files:**
- Modify: `MobileOrg/src/main/res/values/strings.xml`

Add strings for sync mode selector in SSH wizard.

- [ ] **Step 1: Add new strings to strings.xml**

Insert before the closing `</resources>` tag:

```xml
    <!-- org-mobile sync mode strings -->
    <string name="sync_mode_label">Sync mode:</string>
    <string name="sync_mode_git">Git sync</string>
    <string name="sync_mode_orgmobile">Org-mobile protocol</string>
    <string name="path_hint_orgmobile">org-mobile directory path</string>
    <string name="path_hint_git">Git repository URL</string>
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/res/values/strings.xml
git commit -m "feat: add string resources for org-mobile sync mode"
```

---

### Task 6: Modify SSH Wizard Layout — Add Mode Selector

**Files:**
- Modify: `MobileOrg/src/main/res/layout/wizard_ssh.xml`

Add a RadioGroup for sync mode selection (Git vs org-mobile) at the top of the form, after the title.

- [ ] **Step 1: Add RadioGroup to wizard_ssh.xml**

Insert after the title TextView (the one with `android:text="@string/log_in_to_ssh"`) and before the Username TextView:

```xml
      <TextView
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:text="@string/sync_mode_label"
          android:textSize="20dp"/>
      <RadioGroup
          android:id="@+id/sync_mode_group"
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:orientation="horizontal"
          android:paddingBottom="10dp">
          <RadioButton
              android:id="@+id/sync_mode_git"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="@string/sync_mode_git"
              android:textSize="16dp"
              android:checked="true"/>
          <RadioButton
              android:id="@+id/sync_mode_orgmobile"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="@string/sync_mode_orgmobile"
              android:textSize="16dp"/>
      </RadioGroup>
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/res/layout/wizard_ssh.xml
git commit -m "feat: add sync mode selector to SSH wizard layout"
```

---

### Task 7: Modify SSHWizard Activity — Wire Mode Selector

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/gui/wizard/wizards/SSHWizard.java`

Wire the mode RadioGroup: save selected mode to SharedPreferences, change path hint based on mode, and skip JGit clone in org-mobile mode.

- [ ] **Step 1: Add imports and fields**

Add these imports at the top:

```java
import android.widget.RadioGroup;
```

Add these fields to the class:

```java
private RadioGroup syncModeGroup;
private EditText sshPath;
```

Note: `sshPath` is already declared. Only `syncModeGroup` is new.

- [ ] **Step 2: Initialize RadioGroup in onCreate**

After the line `sshPort = (EditText) findViewById(R.id.wizard_ssh_port);`, add:

```java
        syncModeGroup = (RadioGroup) findViewById(R.id.sync_mode_group);
        syncModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.sync_mode_orgmobile) {
                    sshPath.setHint(R.string.path_hint_orgmobile);
                } else {
                    sshPath.setHint(R.string.path_hint_git);
                }
            }
        });
```

- [ ] **Step 3: Load sync mode in loadSettings**

After the line `sshPubFileActual.setText(appSettings.getString("scpPubFile", ""));`, add:

```java
        String syncMode = appSettings.getString("ssh_sync_mode", "git");
        if ("orgmobile".equals(syncMode)) {
            syncModeGroup.check(R.id.sync_mode_orgmobile);
        } else {
            syncModeGroup.check(R.id.sync_mode_git);
        }
```

- [ ] **Step 4: Save sync mode and conditionally skip JGit clone in saveSettings**

In `saveSettings()`, add this line after `editor.putString("scpPass", sshPass.getText().toString());`:

```java
        String syncMode = (syncModeGroup.getCheckedRadioButtonId() == R.id.sync_mode_orgmobile)
                ? "orgmobile" : "git";
        editor.putString("ssh_sync_mode", syncMode);
```

And replace the JGit clone call at the end of `saveSettings()`:

```java
        if ("git".equals(syncMode)) {
            JGitWrapper.CloneGitRepoTask task = new JGitWrapper.CloneGitRepoTask(this);
            task.execute(pathActual, sshPass.getText().toString(), userActual, hostActual, portActual);
        } else {
            // org-mobile mode: no JGit clone needed
            finish();
        }
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/gui/wizard/wizards/SSHWizard.java
git commit -m "feat: SSHWizard supports sync mode selection (git vs orgmobile)"
```

---

### Task 8: Integration Build Verification

**Files:**
- All modified/created files

Final verification that the complete feature builds successfully.

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no runtime issues in key flow**

Check that:
- SSHSynchronizer.synchronize() branches correctly on `ssh_sync_mode`
- OrgMobileSync.pull() returns SyncResult compatible with Synchronizer.runSynchronizer()
- OrgMobileSync.push() generates valid mobileorg.org format
- OrgMobileEditCache caches and compares node values correctly
- SSHWizard saves and loads mode correctly
- SftpWrapper uses AuthData correctly

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: integration fixes for org-mobile protocol support"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|---|---|
| Sync mode selection UI | Task 5, 6, 7 |
| Mode saved in SharedPreferences | Task 7 |
| SSHSynchronizer branches on mode | Task 4 |
| Org-mobile pull: download index.org | Task 3 |
| Org-mobile pull: parse file list | Task 3 (reuses OrgFileParser.getFilesFromIndex) |
| Org-mobile pull: download checksums | Task 3 |
| Org-mobile pull: compare checksums | Task 3 |
| Org-mobile pull: download changed files | Task 3 |
| Org-mobile pull: remove deleted files | Task 3 (SyncResult.deletedFiles) |
| Org-mobile push: generate mobileorg.org | Task 2 |
| Org-mobile push: F(edit:what) format | Task 2 |
| Org-mobile push: new captures | Task 2 |
| Org-mobile push: upload via SFTP | Task 3 |
| Edit tracking: cache original values | Task 2 |
| Edit tracking: compare on push | Task 2 |
| Edit types: todo, tags, priority, heading, body, delete | Task 2 |
| SftpWrapper using JSch ChannelSftp | Task 1 |
| Reuses existing AuthData for SSH config | Task 1 |
| Reuses OrgFileParser parsers | Task 3 |
| Reuses OrgNode.getNodeId() | Task 2 |
| Path field hint changes in org-mobile mode | Task 6, 7 |

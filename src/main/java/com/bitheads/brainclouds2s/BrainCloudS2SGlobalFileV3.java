package com.bitheads.brainclouds2s;

//----------------------------------------------------
// brainCloud client source code
// Copyright 2026 bitHeads, inc.
//----------------------------------------------------

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * S2S service for brainCloud Global File V3 operations.
 *
 * Access via BrainCloudS2S.getGlobalFileV3() after calling init().
 *
 * File upload is a two-step process:
 *   1. SYS_PREPARE_UPLOAD is sent via the S2S dispatcher and returns an uploadId + uploadUrl.
 *   2. The file bytes are POSTed as multipart/form-data to the upload endpoint.
 *      All metadata (gameId, uploadId) travels as URL query parameters; only the file
 *      bytes are in the multipart body under the "file" field.
 */
public class BrainCloudS2SGlobalFileV3 {

    private static final String DEFAULT_UPLOAD_PATH = "/s2suploader/globalfile/upload";

    private BrainCloudS2S _s2s;
    private String _uploadUrl;

    private final List<UploadRequest> _uploadQueue = new ArrayList<>();
    private final Object _uploadLock = new Object();

    private static class UploadRequest {
        IS2SCallback callback;
        volatile String response = null;
        volatile boolean completed = false;
        volatile boolean failed = false;
    }

    public BrainCloudS2SGlobalFileV3(BrainCloudS2S s2s) {
        _s2s = s2s;
    }

    /**
     * Derives the upload endpoint URL from the S2S dispatcher URL.
     * Called automatically by BrainCloudS2S.init().
     */
    public void init(String serverUrl) {
        if (serverUrl != null && serverUrl.contains("s2sdispatcher")) {
            _uploadUrl = serverUrl.replace("s2sdispatcher", "s2suploader/globalfile/upload");
        } else {
            _uploadUrl = "https://api.braincloudservers.com" + DEFAULT_UPLOAD_PATH;
        }
    }

    // -----------------------------------------------------------------------
    // File Info / Query
    // -----------------------------------------------------------------------

    /** Returns metadata for a global file identified by its fileId. */
    public void sysGetFileInfo(String fileId, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fileId", fileId);
        _s2s.request(buildRequest("SYS_GET_FILE_INFO", data), callback);
    }

    /** Returns metadata for a global file identified by folder path and filename. */
    public void sysGetFileInfoSimple(String folderPath, String filename, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("folderPath", folderPath);
        data.put("filename", filename);
        _s2s.request(buildRequest("SYS_GET_FILE_INFO_SIMPLE", data), callback);
    }

    /** Returns true if a file with the given name exists in the specified folder. */
    public void sysCheckFilenameExists(String folderPath, String filename, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("folderPath", folderPath);
        data.put("filename", filename);
        _s2s.request(buildRequest("SYS_CHECK_FILENAME_EXISTS", data), callback);
    }

    /** Returns true if a file exists at the given full path (e.g. "/folder/sub/file.txt"). */
    public void sysCheckFullpathFilenameExists(String fullpathFilename, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fullPathFilename", fullpathFilename);
        _s2s.request(buildRequest("SYS_CHECK_FULLPATH_FILENAME_EXISTS", data), callback);
    }

    /** Returns the CDN URL for the global file identified by fileId. */
    public void sysGetGlobalCDNUrl(String fileId, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fileId", fileId);
        _s2s.request(buildRequest("SYS_GET_GLOBAL_CDN_URL", data), callback);
    }

    /**
     * Lists all global files under the given folder path.
     * Pass folderPath="" and recurse=true to list the entire tree.
     */
    public void sysGetGlobalFileList(String folderPath, boolean recurse, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("folderPath", folderPath);
        data.put("recurse", recurse);
        _s2s.request(buildRequest("SYS_GET_GLOBAL_FILE_LIST", data), callback);
    }

    // -----------------------------------------------------------------------
    // File Management
    // -----------------------------------------------------------------------

    /** Moves a file from a user's personal cloud storage into the global file system. */
    public void sysMoveToGlobalFile(String userProfileId, String userCloudPath,
            String userCloudFilename, String globalTreeId, String globalFilename,
            boolean overwriteIfPresent, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("userProfileId", userProfileId);
        data.put("userCloudPath", userCloudPath);
        data.put("userCloudFilename", userCloudFilename);
        data.put("globalTreeId", globalTreeId);
        data.put("globalFilename", globalFilename);
        data.put("overwriteIfPresent", overwriteIfPresent);
        _s2s.request(buildRequest("SYS_MOVE_TO_GLOBAL_FILE", data), callback);
    }

    /**
     * Copies a global file to another folder, optionally with a new name.
     * Pass version=-1 to copy the latest version.
     * Pass treeVersion=-1 to skip the destination tree version check.
     */
    public void sysCopyGlobalFile(String fileId, int version, String newTreeId, int treeVersion,
            String newFilename, boolean overwriteIfPresent, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fileId", fileId);
        data.put("version", version);
        data.put("newTreeId", newTreeId);
        data.put("treeVersion", treeVersion);
        data.put("newFilename", newFilename);
        data.put("overwriteIfPresent", overwriteIfPresent);
        _s2s.request(buildRequest("SYS_COPY_GLOBAL_FILE", data), callback);
    }

    /**
     * Moves a global file to another folder, optionally with a new name.
     * Pass version=-1 to move the latest version.
     * Pass treeVersion=-1 to skip the destination tree version check.
     */
    public void sysMoveGlobalFile(String fileId, int version, String newTreeId, int treeVersion,
            String newFilename, boolean overwriteIfPresent, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fileId", fileId);
        data.put("version", version);
        data.put("newTreeId", newTreeId);
        data.put("treeVersion", treeVersion);
        data.put("newFilename", newFilename);
        data.put("overwriteIfPresent", overwriteIfPresent);
        _s2s.request(buildRequest("SYS_MOVE_GLOBAL_FILE", data), callback);
    }

    /** Deletes a single global file. Pass version=-1 to delete without a version check. */
    public void sysDeleteGlobalFile(String fileId, int version, String filename,
            IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fileId", fileId);
        data.put("version", version);
        data.put("filename", filename);
        _s2s.request(buildRequest("SYS_DELETE_GLOBAL_FILE", data), callback);
    }

    /**
     * Deletes all global files in the specified folder.
     * Pass treeVersion=-1 to skip the version check.
     * Set recurse=true to also delete files in sub-folders.
     */
    public void sysDeleteGlobalFiles(String treeId, String folderPath, int treeVersion,
            boolean recurse, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("treeId", treeId);
        data.put("folderPath", folderPath);
        data.put("treeVersion", treeVersion);
        data.put("recurse", recurse);
        _s2s.request(buildRequest("SYS_DELETE_GLOBAL_FILES", data), callback);
    }

    // -----------------------------------------------------------------------
    // Folder Management
    // -----------------------------------------------------------------------

    /**
     * Creates a new folder at the given path.
     * Pass treeVersion=-1 to skip the version check.
     * Set createInterimDirectories=true to auto-create any missing parent folders.
     */
    public void sysCreateFolder(String folderPath, int treeVersion, String name, String desc,
            boolean createInterimDirectories, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("folderPath", folderPath);
        data.put("treeVersion", treeVersion);
        data.put("name", name);
        data.put("desc", desc);
        data.put("createInterimDirectories", createInterimDirectories);
        _s2s.request(buildRequest("SYS_CREATE_FOLDER", data), callback);
    }

    /**
     * Moves a folder to a new path, optionally renaming it.
     * Pass treeVersion=-1 to skip the version check.
     */
    public void sysMoveFolder(String treeId, int treeVersion, String newFolderPath,
            String updatedName, boolean createInterimDirectories, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("treeId", treeId);
        data.put("treeVersion", treeVersion);
        data.put("newFolderPath", newFolderPath);
        data.put("updatedName", updatedName);
        data.put("createInterimDirectories", createInterimDirectories);
        _s2s.request(buildRequest("SYS_MOVE_FOLDER", data), callback);
    }

    /**
     * Renames a folder in place.
     * Pass treeVersion=-1 to skip the version check.
     */
    public void sysRenameFolder(String treeId, int treeVersion, String updatedName,
            IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("treeId", treeId);
        data.put("treeVersion", treeVersion);
        data.put("updatedName", updatedName);
        _s2s.request(buildRequest("SYS_RENAME_FOLDER", data), callback);
    }

    /** Resolves the treeId for a folder given its full path (e.g. "/folder/sub/"). */
    public void sysLookupFolder(String fullFolderPath, IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("fullFolderPath", fullFolderPath);
        _s2s.request(buildRequest("SYS_LOOKUP_FOLDER", data), callback);
    }

    /**
     * Deletes a folder. Set force=true to also delete any files and sub-folders inside it.
     * Pass treeVersion=-1 to skip the version check.
     */
    public void sysDeleteFolder(String treeId, String folderPath, int treeVersion, boolean force,
            IS2SCallback callback) {
        JSONObject data = new JSONObject();
        data.put("treeId", treeId);
        data.put("folderPath", folderPath);
        data.put("treeVersion", treeVersion);
        data.put("force", force);
        _s2s.request(buildRequest("SYS_DELETE_FOLDER", data), callback);
    }

    // -----------------------------------------------------------------------
    // Upload
    // -----------------------------------------------------------------------

    /**
     * Uploads a file to the brainCloud Global File V3 system via S2S.
     *
     * Step 1: SYS_PREPARE_UPLOAD via the S2S dispatcher returns an uploadId.
     * Step 2: File bytes are POSTed as multipart/form-data to the upload endpoint.
     *
     * The callback is dispatched on the next call to BrainCloudS2S.runCallbacks()
     * after the upload HTTP request completes.
     *
     * @param treeId              Folder tree ID (use "_root_" for root; call sysLookupFolder for sub-folders)
     * @param filename            Name of the file as it will appear in brainCloud
     * @param overwriteIfPresent  Replaces any existing file with the same name when true
     * @param fileData            File content as a byte array
     * @param callback            Invoked with the upload result JSONObject
     */
    public void uploadGlobalFile(String treeId, String filename, boolean overwriteIfPresent,
            byte[] fileData, IS2SCallback callback) {
        log("[GlobalFileV3] Preparing upload: " + filename +
                " (" + fileData.length + " bytes) treeId=" + treeId);

        JSONObject data = new JSONObject();
        data.put("treeId", treeId);
        data.put("filename", filename);
        data.put("overwriteIfPresent", overwriteIfPresent);
        data.put("fileSize", fileData.length);

        _s2s.request(buildRequest("SYS_PREPARE_UPLOAD", data), (context, prepareResult) -> {
            if (prepareResult == null || prepareResult.getInt("status") != 200) {
                log("[GlobalFileV3] SYS_PREPARE_UPLOAD failed: " +
                        (prepareResult != null ? prepareResult.toString() : "null"));
                if (callback != null) callback.s2sCallback(context, prepareResult);
                return;
            }

            JSONObject fileDetails = null;
            try {
                fileDetails = prepareResult.getJSONObject("data").getJSONObject("fileDetails");
            } catch (Exception e) {
                log("[GlobalFileV3] SYS_PREPARE_UPLOAD missing fileDetails: " + prepareResult);
                if (callback != null) callback.s2sCallback(context, prepareResult);
                return;
            }

            if (!fileDetails.has("uploadId")) {
                log("[GlobalFileV3] SYS_PREPARE_UPLOAD missing uploadId: " + prepareResult);
                if (callback != null) callback.s2sCallback(context, prepareResult);
                return;
            }

            String uploadId = fileDetails.getString("uploadId");
            String resolvedUrl = buildUploadUrl(fileDetails, uploadId);

            log("[GlobalFileV3] Uploading to: " + resolvedUrl);
            sendFileUpload(resolvedUrl, filename, fileData, callback);
        });
    }

    /**
     * Polls in-flight uploads and fires their callbacks when complete.
     * Called automatically by BrainCloudS2S.runCallbacks().
     */
    public void runCallbacks() {
        // Collect completed items outside the lock before dispatching callbacks
        List<UploadRequest> completed = new ArrayList<>();
        synchronized (_uploadLock) {
            for (int i = _uploadQueue.size() - 1; i >= 0; i--) {
                UploadRequest req = _uploadQueue.get(i);
                if (req.completed || req.failed) {
                    completed.add(req);
                    _uploadQueue.remove(i);
                }
            }
        }

        for (UploadRequest req : completed) {
            JSONObject responseJson = null;
            if (req.response != null) {
                try {
                    responseJson = new JSONObject(req.response);
                } catch (Exception e) {
                    log("[GlobalFileV3] Failed to parse upload response: " + req.response);
                }
            }
            if (req.callback != null) {
                req.callback.s2sCallback(_s2s, responseJson);
            }
        }
    }

    /**
     * Cancels and clears any pending uploads.
     * Called automatically by BrainCloudS2S.disconnect().
     */
    public void disconnect() {
        synchronized (_uploadLock) {
            _uploadQueue.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private JSONObject buildRequest(String operation, JSONObject data) {
        JSONObject request = new JSONObject();
        request.put("service", "globalFileV3");
        request.put("operation", operation);
        request.put("data", data);
        return request;
    }

    private String buildUploadUrl(JSONObject fileDetails, String uploadId) {
        if (fileDetails.has("uploadUrl")) {
            String relativeUrl = fileDetails.getString("uploadUrl");
            if (relativeUrl.startsWith("http")) {
                return relativeUrl;
            }
            // Relative path from server — prefix with scheme + host from our computed upload URL
            try {
                URL base = new URL(_uploadUrl);
                return base.getProtocol() + "://" + base.getHost() + relativeUrl;
            } catch (Exception e) {
                // fall through to fallback
            }
        }
        // Fallback: construct URL from our derived upload URL + query params
        return _uploadUrl + "?gameId=" + _s2s.getAppId() + "&uploadId=" + uploadId;
    }

    private void sendFileUpload(String uploadUrl, String filename, byte[] fileData,
            IS2SCallback callback) {
        UploadRequest req = new UploadRequest();
        req.callback = callback;
        synchronized (_uploadLock) {
            _uploadQueue.add(req);
        }

        new Thread(() -> {
            String boundary = "----BrainCloudS2SBoundary" + System.currentTimeMillis();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);
                connection.connect();

                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    String headerPart = "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"file\"; filename=\""
                            + filename + "\"\r\n"
                            + "Content-Type: application/octet-stream\r\n\r\n";
                    wr.writeBytes(headerPart);
                    wr.write(fileData);
                    wr.writeBytes("\r\n--" + boundary + "--\r\n");
                }

                int responseCode = connection.getResponseCode();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode == HttpURLConnection.HTTP_OK
                                ? connection.getInputStream()
                                : connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                log("[GlobalFileV3] Upload complete (" + responseCode + "): " + sb);
                synchronized (_uploadLock) {
                    req.response = sb.toString();
                    req.completed = true;
                }

            } catch (IOException e) {
                log("[GlobalFileV3] Upload error: " + e.getMessage());
                synchronized (_uploadLock) {
                    req.response = "{\"status\":900,\"status_message\":\"File upload failed: "
                            + e.getMessage().replace("\"", "\\\"") + "\"}";
                    req.failed = true;
                }
            }
        }).start();
    }

    private void log(String message) {
        if (_s2s.getLogEnabled()) {
            System.out.println("#BCC " + message);
        }
    }
}

package com.bitheads.brainclouds2s;

//----------------------------------------------------
// brainCloud client source code
// Copyright 2026 bitHeads, inc.
//----------------------------------------------------

import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Integration tests for BrainCloudS2SGlobalFileV3.
 *
 * Tests run in ascending name order (test01_ ... test14_) to maintain the required
 * sequence: create folder → upload → query → mutate → cleanup.
 *
 * Shared state (treeId, fileId, fileVersion) is held in static fields because
 * JUnit creates a new class instance per test method while @Before/@After re-auth
 * each time.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GlobalFileV3Test extends TestFixtureBase {

    // Static so they survive JUnit's per-test class instantiation
    private static String gfv3FolderTreeId = "";
    private static String gfv3FileId = "";
    private static int gfv3FileVersion = 1;

    // -----------------------------------------------------------------------
    // Test 01 (parity: dotnet #8): SysGetGlobalFileList
    // -----------------------------------------------------------------------
    @Test
    public void test01_sysGetGlobalFileList() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysGetGlobalFileList("", true, tr);
        assertSuccess("SysGetGlobalFileList", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 02 (parity: dotnet #9): SysLookupFolder
    // -----------------------------------------------------------------------
    @Test
    public void test02_sysLookupFolder() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysLookupFolder("s2s_test_folder", tr);
        assertSuccess("SysLookupFolder", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 03 (parity: dotnet #10): SysCreateFolder — captures treeId
    // -----------------------------------------------------------------------
    @Test
    public void test03_sysCreateFolder() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysCreateFolder(
                "s2s_test_folder", -1, "s2s_test_folder_2",
                "S2S integration test folder", false, tr);
        JSONObject result = tr.getResult(_s2sClient);
        assertSuccess("SysCreateFolder", result);
        try {
            gfv3FolderTreeId = result.getJSONObject("data").getString("createdTreeId");
        } catch (Exception e) {
            fail("SysCreateFolder did not return createdTreeId: " +
                    (result != null ? result.toString() : "null"));
        }
    }

    // -----------------------------------------------------------------------
    // Test 04 (parity: dotnet #11): UploadGlobalFile — captures fileId / version
    // -----------------------------------------------------------------------
    @Test
    public void test04_uploadGlobalFile() {
        byte[] fileData = "Hello from brainCloud S2S Java file upload test!".getBytes();
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().uploadGlobalFile(
                gfv3FolderTreeId, "s2s_test_file.txt", true, fileData, tr);
        JSONObject result = tr.getResult(_s2sClient);
        assertSuccess("UploadGlobalFile", result);
        try {
            JSONObject fd = result.getJSONObject("data")
                                  .getJSONObject("fileDetails")
                                  .getJSONObject("fileDetails");
            gfv3FileId = fd.getString("fileId");
            gfv3FileVersion = fd.getInt("version");
        } catch (Exception e) {
            fail("UploadGlobalFile did not return fileId/version: " +
                    (result != null ? result.toString() : "null"));
        }
    }

    // -----------------------------------------------------------------------
    // Test 05 (parity: dotnet #12): SysGetFileInfo
    // -----------------------------------------------------------------------
    @Test
    public void test05_sysGetFileInfo() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysGetFileInfo(gfv3FileId, tr);
        assertSuccess("SysGetFileInfo", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 06 (parity: dotnet #13): SysGetFileInfoSimple
    // -----------------------------------------------------------------------
    @Test
    public void test06_sysGetFileInfoSimple() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysGetFileInfoSimple(
                "s2s_test_folder/s2s_test_folder_2", "s2s_test_file.txt", tr);
        assertSuccess("SysGetFileInfoSimple", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 07 (parity: dotnet #14): SysCheckFilenameExists
    // -----------------------------------------------------------------------
    @Test
    public void test07_sysCheckFilenameExists() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysCheckFilenameExists(
                "s2s_test_folder/s2s_test_folder_2", "s2s_test_file.txt", tr);
        assertSuccess("SysCheckFilenameExists", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 08 (parity: dotnet #15): SysCheckFullpathFilenameExists
    // -----------------------------------------------------------------------
    @Test
    public void test08_sysCheckFullpathFilenameExists() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysCheckFullpathFilenameExists(
                "s2s_test_folder/s2s_test_folder_2/s2s_test_file.txt", tr);
        assertSuccess("SysCheckFullpathFilenameExists", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 09 (parity: dotnet #16): SysGetGlobalCDNUrl
    // -----------------------------------------------------------------------
    @Test
    public void test09_sysGetGlobalCDNUrl() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysGetGlobalCDNUrl(gfv3FileId, tr);
        assertSuccess("SysGetGlobalCDNUrl", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 10 (parity: dotnet #17): SysCopyGlobalFile
    // -----------------------------------------------------------------------
    @Test
    public void test10_sysCopyGlobalFile() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysCopyGlobalFile(
                gfv3FileId, gfv3FileVersion, gfv3FolderTreeId, -1,
                "s2s_file_copy.txt", true, tr);
        assertSuccess("SysCopyGlobalFile", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 11 (parity: dotnet #18): SysMoveGlobalFile
    // -----------------------------------------------------------------------
    @Test
    public void test11_sysMoveGlobalFile() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysMoveGlobalFile(
                gfv3FileId, gfv3FileVersion, gfv3FolderTreeId, -1,
                "s2s_file_moved.txt", true, tr);
        assertSuccess("SysMoveGlobalFile", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 12 (parity: dotnet #19): SysRenameFolder
    // -----------------------------------------------------------------------
    @Test
    public void test12_sysRenameFolder() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysRenameFolder(
                gfv3FolderTreeId, -1, "s2s_test_folder_renamed", tr);
        assertSuccess("SysRenameFolder", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 13 (parity: dotnet #20): SysDeleteGlobalFiles — cleanup files
    // -----------------------------------------------------------------------
    @Test
    public void test13_sysDeleteGlobalFiles() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysDeleteGlobalFiles(
                gfv3FolderTreeId, "s2s_test_folder/s2s_test_folder_renamed", -1, true, tr);
        assertSuccess("SysDeleteGlobalFiles", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Test 14 (parity: dotnet #21): SysDeleteFolder — cleanup folder
    // -----------------------------------------------------------------------
    @Test
    public void test14_sysDeleteFolder() {
        TestResult tr = new TestResult();
        _s2sClient.getGlobalFileV3().sysDeleteFolder(
                gfv3FolderTreeId, "s2s_test_folder/s2s_test_folder_renamed", -1, true, tr);
        assertSuccess("SysDeleteFolder", tr.getResult(_s2sClient));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private void assertSuccess(String testName, JSONObject result) {
        if (result == null || result.getInt("status") != 200) {
            fail(testName + " failed: " + (result != null ? result.toString() : "null"));
        }
    }
}

package com.bitheads.brainclouds2s;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.bitheads.brainclouds2s.BrainCloudS2S;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestFixtureBase {

    static private String m_serverUrl = "";
    static private String m_appId = "";
    static private String m_serverSecret = "";
    static private String m_serverName = "";

    protected boolean m_autoAuth = false;
    protected String m_serverSecretOverride = null;
    protected boolean m_expectAuthFail = false;

    public TestFixtureBase() {
    }

    protected static BrainCloudS2S _s2sClient;

    /// <summary>
    /// Routine loads up brainCloud configuration info from "${PROJECT_ROOT}/ids.txt"
    /// (hopefully)
    /// in a platform agnostic way.
    /// </summary>
    static private void loadIds() {
        if (m_serverUrl.length() > 0) {
            return;
        }

        File idsFile = new File("ids.txt");
        try {
            System.out.println("Looking for ids.txt file in " + idsFile.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (idsFile.exists()) {
            System.out.println("Found ids.txt file");
        }

        List<String> lines = new ArrayList<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(idsFile));
            String text;
            while ((text = reader.readLine()) != null) {
                lines.add(text);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (String line : lines) {
            String[] split = line.split("=");
            switch (split[0]) {
                case "s2sUrl":
                    m_serverUrl = split[1];
                    break;
                case "appId":
                    m_appId = split[1];
                    break;
                case "serverName":
                    m_serverName = split[1];
                    break;
                case "serverSecret":
                    m_serverSecret = split[1];
                    break;
            }
        }
    }

    @BeforeClass
    public static void setUpClass() {
        loadIds();
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {

        System.out.println("setUp()");

        _s2sClient = new BrainCloudS2S();
        _s2sClient.init(m_appId, m_serverName, 
                        m_serverSecretOverride != null ? m_serverSecretOverride : m_serverSecret, 
                        m_serverUrl, m_autoAuth);
        _s2sClient.setLogEnabled(true);

        if (!_s2sClient.isIsInitialized()) {
            fail("Initialization Failed");
            _s2sClient = null;
            return;
        }

        if (!m_autoAuth) {
            TestResult tr = new TestResult();

            _s2sClient.authenticate(tr);
            JSONObject jsonData = tr.getResult(_s2sClient);

            int statusCode = jsonData.getInt("status");

            if (statusCode != 200 && !m_expectAuthFail) {
                fail("Auth Failed");
                _s2sClient = null;
                return;
            }
            if (statusCode == 200 && m_expectAuthFail) {
                fail("Auth expected to fail, but passed");
                _s2sClient = null;
                return;
            }
        }
    }

    @After
    public void tearDown() {

        System.out.println("tearDown()");

        if (_s2sClient != null) {
            _s2sClient.disconnect();
            _s2sClient = null;
        }
    }

    public void runSimple(String requestString) {
        
        JSONObject json = null;
        try {
            json = new JSONObject(requestString);
        } catch (Exception e) {
            fail("Bad json");
        }

        TestResult tr = new TestResult();
        _s2sClient.request(json, tr);
        JSONObject result = tr.getResult(_s2sClient);

        if (result == null || result.getInt("status") != 200) {
            fail("Error returned");
        }
    }

    public void runSimpleExpectFail(String requestString) {
        
        JSONObject json = null;
        try {
            json = new JSONObject(requestString);
        } catch (Exception e) {
            return; // That's good
        }

        TestResult tr = new TestResult();
        _s2sClient.request(json, tr);
        JSONObject result = tr.getResult(_s2sClient);

        if (result != null && result.getInt("status") == 200) {
            fail("Expected fail, got 200");
        }
    }
}

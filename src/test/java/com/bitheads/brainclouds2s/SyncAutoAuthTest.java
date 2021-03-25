package com.bitheads.brainclouds2s;

import static org.junit.Assert.fail;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

public class SyncAutoAuthTest extends TestFixtureBase {
    public SyncAutoAuthTest() {
        m_autoAuth = true;
    }

    @Test
    public void simpleRequest() {
        JSONObject request = new JSONObject("{\"service\":\"time\",\"operation\":\"READ\",\"data\":{}}");

        JSONObject result = _s2sClient.requestSync(request);

        if (result == null) fail("request = null");
        if (result.getInt("status") != 200) fail("status != 200");
    }
}

package com.bitheads.brainclouds2s;

import static org.junit.Assert.fail;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

public class RunWithTimeoutAutoAuthTest extends TestFixtureBase {

    int processedCount = 0;
    int successCount = 0;
    
    public RunWithTimeoutAutoAuthTest() {
        m_autoAuth = true;
    }

    @Test
    public void runScript() {
        JSONObject request = new JSONObject("{\"service\":\"time\",\"operation\":\"READ\",\"data\":{}}");

        processedCount = 0;
        successCount = 0;
        IS2SCallback callback = (BrainCloudS2S context, JSONObject jsonData) -> {
            if (jsonData != null && jsonData.getInt("status") == 200)
            {
                successCount++;
            }
            processedCount++;
        };
        
        _s2sClient.request(request, callback);

        _s2sClient.runCallbacks(20 * 1000); // Auth
        _s2sClient.runCallbacks(20 * 1000); // The request

        if (processedCount != 1) fail("processedCount != 1");
        if (successCount != 1) fail("successCount != 1");
    }
}

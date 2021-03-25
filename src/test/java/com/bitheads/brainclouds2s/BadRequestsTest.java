package com.bitheads.brainclouds2s;

import static org.junit.Assert.fail;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

public class BadRequestsTest extends TestFixtureBase {
    public BadRequestsTest() {
    }

    int processedCount = 0;
    int successCount = 0;

    @Test
    public void singleBadRequest() {
        runSimpleExpectFail("{\"service\":\"timey\",\"operation\":\"READ_MUH_TIME\",\"data\":{}}");
    }

    @Test
    public void badSuccesssiveCalls() {
        JSONObject badRequest = new JSONObject("{\"service\":\"timey\",\"operation\":\"READ_MUH_TIME\",\"data\":{}}");
        JSONObject goodRequest = new JSONObject("{\"service\":\"time\",\"operation\":\"READ\",\"data\":{}}");

        processedCount = 0;
        successCount = 0;
        IS2SCallback callback = (BrainCloudS2S context, JSONObject jsonData) -> {
            if (jsonData != null && jsonData.getInt("status") == 200)
            {
                successCount++;
            }
            processedCount++;
        };

        // Queue many at once
        _s2sClient.request(goodRequest, callback);
        _s2sClient.request(goodRequest, callback);
        _s2sClient.request(badRequest, callback);
        _s2sClient.request(goodRequest, callback);
        _s2sClient.request(goodRequest, callback);
        
        Date startTime = new Date();
        while (processedCount < 5) {
            _s2sClient.runCallbacks();

            // Check if we don't timeout (~20 seconds)
            Date now = new Date();

            if (now.getTime() - startTime.getTime() > 20 * 1000) {
                fail("Timeout");
                break;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        if (successCount != 4) fail("successCount != 4");
    }
}

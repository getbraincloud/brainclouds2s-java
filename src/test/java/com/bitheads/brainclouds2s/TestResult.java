package com.bitheads.brainclouds2s;

import java.util.Date;

import org.json.JSONObject;

public class TestResult implements IS2SCallback {

    private boolean _completed = false;
    private JSONObject _result = null;

    public JSONObject getResult(BrainCloudS2S s2sClient) {

        _completed = false;
        _result = null;

        Date startTime = new Date();

        while (!_completed) {
            s2sClient.runCallbacks();

            // Check if we don't timeout (~20 seconds)
            Date now = new Date();

            if (now.getTime() - startTime.getTime() > 20 * 1000) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        _completed = true;

        return _result;
    }

    @Override
    public void s2sCallback(BrainCloudS2S context, JSONObject jsonData) {

        _result = jsonData;
        _completed = true;
    }
}

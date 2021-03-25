package com.bitheads.brainclouds2s;

import org.json.JSONObject;

public class Request {
    private JSONObject _json;
    private IS2SCallback _callback;

    public Request(JSONObject json, IS2SCallback callback) {
        _json = json;
        _callback = callback;
    }

    public JSONObject getJson() { return _json; }
    public IS2SCallback getCallback() { return _callback; }
}

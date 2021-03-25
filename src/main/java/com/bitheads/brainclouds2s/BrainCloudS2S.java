package com.bitheads.brainclouds2s;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BrainCloudS2S implements Runnable {

    public static final String DEFAULT_S2S_URL = "https://sharedprod.braincloudservers.com/s2sdispatcher";

    private static final long NO_PACKET_EXPECTED = -1;

    private static final int CLIENT_NETWORK_ERROR_TIMEOUT = 90001;
    private static final int MESSAGE_CONTENT_INVALID_JSON = 40614;
    private static final int SERVER_SESSION_EXPIRED = 40365;
    private static final int INVALID_REQUEST = 40001;
    private static final int BAD_REQUEST = 400;
    private static final int CLIENT_NETWORK_ERROR = 900;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_AUTHENTICATING = 1;
    private static final int STATE_CONNECTED = 2;

    private boolean _isInitialized = false;
    private int _state = STATE_DISCONNECTED;
    private boolean _autoAuth = false;
    private int _retryCount = 0;

    private String _serverUrl;
    private String _appId;
    private String _serverSecret;
    private String _serverName;
    private String _sessionId = null;
    private long _packetId;

    private ScheduledFuture _heartbeatTimer = null;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean _loggingEnabled = false;
    private long _heartbeatSeconds = 1800;  // Default to 30 mins
    private List<Request> _requestQueue = new ArrayList<Request>();

    // Active request
    private JSONObject _jsonResponse = null;
    private Request _currentRequest = null;
    private long _responseCode = 0;
    private Thread _requestThread = null;
    private Object _lock = new Object();

    /**
     * Initialize BrainCloudS2S context
     *
     * @param appId Application ID
     * @param serverName Server name
     * @param serverSecret Server secret key
     * @param url The server url to send the request to. You can use
     *            BrainCloudS2S.DEFAULT_S2S_URL for the default brainCloud servers.
     * @param autoAuth If sets to true, the context will authenticate on the
     *                 first request if it's not already. Otherwise,
     *                 authenticate() or authenticateSync() must be called
     *                 successfully first before doing requests. WARNING: This
     *                 used to be implied true.
     * 
     *                 It is recommended to put this to false, manually
     *                 call authenticate, and wait for a successful response
     *                 before proceeding with other requests.
     */
    public void init(String appId, String serverName, String serverSecret, String url, boolean autoAuth) {
        _packetId = 0;
        _serverUrl = url;
        _appId = appId;
        _serverSecret = serverSecret;
        _serverName = serverName;
        _sessionId = null;
        _isInitialized = true;
        _autoAuth = autoAuth;
        _retryCount = 0;
        _requestQueue.clear();
    }

    /**
     * Send an S2S request.
     *
     * @param json S2S operation to be sent as JSON object
     * @param callback Callback function
     */
    public void request(JSONObject json, IS2SCallback callback) {

        if (_state == STATE_DISCONNECTED && _autoAuth) {
            authenticate((BrainCloudS2S context, JSONObject response) -> {
                if (_state == STATE_CONNECTED && response != null && response.getInt("status") == 200) {
                    if (_requestQueue.size() > 0) {
                        Request nextRequest = _requestQueue.get(0);
                        sendRequest(nextRequest.getJson(), nextRequest.getCallback());
                    }
                }
            });
        }
        queueRequest(json, callback);
    }

    /**
     * Send an S2S request.
     *
     * @param json S2S operation to be sent as JSON formatted string.
     * @param callback Callback function
     */
    public void request(String json, IS2SCallBackString callback) {
        JSONObject jsonObject = new JSONObject(json);
        
        request(jsonObject, ((context, jsonData) -> {
            callback.s2sCallback(context, jsonData.toString());
        }));
    }

    private boolean _syncCompleted = false;
    private JSONObject _syncResult = null;

    /*
     * Send an S2S request, and wait for result. This call is blocking.
     * @param json Content to be sent
     * @return Result
     */
    public JSONObject requestSync(JSONObject json) {

        _syncCompleted = false;
        _syncResult = null;
        
        request(json, ((context, jsonData) -> {
            _syncCompleted = true;
            _syncResult = jsonData;
        }));

        Date startTime = new Date();

        while (!_syncCompleted) {
            runCallbacks();

            // Check if we don't timeout (~60 seconds)
            Date now = new Date();

            if (now.getTime() - startTime.getTime() > 20 * 1000) {
                _syncResult = new JSONObject("{\"status\":900,\"message\":\"Request timeout\"}");
                break;
            }

            try {
                Thread.sleep(10); // We run this faster to get better response time
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        _syncCompleted = true;

        return _syncResult;
    }
    
    private void startHeartbeat() {
        stopHeartbeat();
        _heartbeatTimer = scheduler.schedule(this, _heartbeatSeconds, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (_heartbeatTimer != null) {
            _heartbeatTimer.cancel(true);
        }
    }

    /**
     * Current state of the logger.
     * @return 
     */
    public boolean getLogEnabled() {
        return _loggingEnabled;
    }

    public boolean isIsInitialized() {
        return _isInitialized;
    }

    /*
     * Set wether S2S messages and errors are logged to the console
     * @param isEnabled Will log if true. Default false
     */
    public void setLogEnabled(boolean isEnabled) {
        _loggingEnabled = isEnabled;
    }

    private JSONObject createPacket(JSONObject json) {
        JSONObject allMessages;
        // If already a wrapped in a packet then just update it.
        if (json.has("sessionId") && json.has("packetId")) {
            allMessages = json;
        } else {
            JSONArray messages = new JSONArray();
            messages.put(json);

            allMessages = new JSONObject();
            allMessages.put("messages", messages);
        }
        allMessages.put("sessionId", _sessionId);
        allMessages.put("packetId", _packetId);
        return allMessages;
    }

    private JSONObject extractResponseAt(int index, JSONObject json) {

        JSONObject response = null;
        try {
            JSONArray messageResponses = json.getJSONArray("messageResponses");
            if (messageResponses.length() > 0) {
                response = messageResponses.getJSONObject(0);
            }
        } catch (Exception e) {
            LogString("Error decoding response " + e);
            response = json;
        }
        return response;
    }

    private JSONObject generateError(int statusCode, int reasonCode, String statusMessage) {
        JSONObject jsonError = new JSONObject();
        try {
            jsonError.put("status", statusCode);
            jsonError.put("reason_code", reasonCode);
            jsonError.put("severity", "ERROR");
            jsonError.put("status_message", statusMessage);
        } catch (JSONException je) {
            LogString("Error encoding error " + je.getMessage());
        }
        return jsonError;
    }

    private void failAllRequests(JSONObject message) {
        List<Request> queue = new ArrayList<Request>(_requestQueue); // Create a copy
        _requestQueue.clear();
        for (int i = 0; i < queue.size(); i++) {
            Request request = queue.get(i);
            if (request.getCallback() != null) {
                request.getCallback().s2sCallback(this, message);
            }
        }
    }

    /*
     * Authenticate with brainCloud. If autoAuth is set to false, which is
     * the default, this must be called successfully before doing other
     * requests. See BrainCloudS2S.init
     * @param callback Callback function
     */
    public void authenticate(IS2SCallback callback) {

        _state = STATE_AUTHENTICATING;

        JSONObject authenticateData = new JSONObject();
        authenticateData.put("appId", _appId);
        authenticateData.put("serverName", _serverName);
        authenticateData.put("serverSecret", _serverSecret);

        JSONObject authenticateMsg = new JSONObject();
        authenticateMsg.put("service", "authenticationV2");
        authenticateMsg.put("operation", "AUTHENTICATE");
        authenticateMsg.put("data", authenticateData);

        JSONArray messages = new JSONArray();
        messages.put(authenticateMsg);

        JSONObject allMessages = new JSONObject();
        allMessages.put("messages", messages);
        _packetId = 0;
        allMessages.put("packetId", _packetId);

        sendRawRequest(allMessages, (BrainCloudS2S context, JSONObject rawResponse) -> {
            try {
                if (rawResponse != null && rawResponse.has("messageResponses") && rawResponse.getJSONArray("messageResponses").length() > 0) {

                    JSONObject message = rawResponse.getJSONArray("messageResponses").getJSONObject(0);

                    if (message.getInt("status") == 200) {
                        
                        JSONObject data = message.getJSONObject("data");

                        _state = STATE_CONNECTED;
                        _packetId = rawResponse.getLong("packetId") + 1;
                        _sessionId = data.getString("sessionId");

                        // Start heatbeat
                        if (data.has("heartbeatSeconds")) {
                            _heartbeatSeconds = data.getLong("heartbeatSeconds");
                        }
                        startHeartbeat();
                    } else {
                        failAllRequests(message);
                        _state = STATE_DISCONNECTED;
                    }

                    if (callback != null) {
                        callback.s2sCallback(context, message);
                    }
                } else {
                    failAllRequests(null);
                    disconnect();

                    if (callback != null) {
                        callback.s2sCallback(context, null);
                    }
                }
            } catch (JSONException e) {
                failAllRequests(null);
                disconnect();

                if (callback != null) {
                    callback.s2sCallback(context, null);
                }
            }
        });
    }

    private void queueRequest(JSONObject jsonRequest, IS2SCallback callback) {
        _requestQueue.add(new Request(jsonRequest, callback));

        // If only 1 in the queue, then send the request immediately
        // Also make sure we're not in the process of authenticating
        if (_requestQueue.size() == 1 && _state != STATE_AUTHENTICATING) {
            sendRequest(jsonRequest, callback);
        }
    }

    private void reAuth() {
        authenticate((BrainCloudS2S context, JSONObject response) -> {
            if (response != null) {
                if (_requestQueue.size() > 0) {
                    Request nextRequest = _requestQueue.get(0);
                    sendRequest(nextRequest.getJson(), nextRequest.getCallback());
                }
            }
        });
    }

    // Raw http request, we don't extract the messageReponses from this, and we
    // don't wrap in the session id and packet id. This is just raw send/recv
    private void sendRawRequest(JSONObject jsonRequest, IS2SCallback callback) {

        synchronized(_lock) {
            _currentRequest = new Request(jsonRequest, callback);
            _jsonResponse = null;
        }

        stopHeartbeat(); // Will restart after request is done

        _requestThread = new Thread(() -> {

            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) new URL(_serverUrl).openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
    
                String body = jsonRequest.toString() + "\r\n\r\n";
    
                connection.setRequestProperty("charset", "utf-8");
                byte[] postData;
                postData = body.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
    
                if (_loggingEnabled) {
                    LogString("OUTGOING: " + jsonRequest.toString(2) + ", t: " + new Date().toString());
                }
    
                connection.connect();

                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                }

                // Get server response
                String responseBody = readResponseBody(connection);
                synchronized(_lock) {
                    _responseCode = connection.getResponseCode();

                    _jsonResponse = null;
                    // if (_responseCode == HttpURLConnection.HTTP_OK) {
                        try {
                            _jsonResponse = new JSONObject(responseBody);
                        } catch (JSONException e) {
                            _jsonResponse = null;
                        }
                    // }
                }
            } catch (java.net.SocketTimeoutException e) {
                LogString("TIMEOUT t: " + new Date().toString());
                if (callback != null) {
                    _jsonResponse = generateError(CLIENT_NETWORK_ERROR, CLIENT_NETWORK_ERROR_TIMEOUT, "Network error");
                }
                return;
            } catch (JSONException e) {
                LogString("JSON ERROR " + e.getMessage() + " t: " + new Date().toString());
                if (callback != null) {
                    _jsonResponse = generateError(CLIENT_NETWORK_ERROR, INVALID_REQUEST, e.getMessage());
                }
                return;
            } catch (IOException e) {
                try {
                    int status_code = (connection != null) ? connection.getResponseCode() : CLIENT_NETWORK_ERROR;
                    _jsonResponse = generateError(status_code, INVALID_REQUEST, e.getMessage());
                } catch (IOException e2) {
                    _jsonResponse = generateError(CLIENT_NETWORK_ERROR, INVALID_REQUEST, e2.getMessage());
                }
            }
        });

        _requestThread.start();
    }

    private void sendRequest(JSONObject jsonRequest, IS2SCallback callback) {

        sendRawRequest(createPacket(jsonRequest), (BrainCloudS2S context, JSONObject response) -> {
            _packetId++;
            
            try {
                JSONObject jsonResponse = null;
                if (response != null) {
                    jsonResponse = extractResponseAt(0, response);
                }

                // check for expired session
                if (jsonResponse != null && jsonResponse.getInt("status") != 200 && 
                    jsonResponse.getInt("reason_code") == SERVER_SESSION_EXPIRED && _retryCount < 3) {

                    _retryCount++;
                    _packetId = 0;
                    _sessionId = null;
                    reAuth();
                    return;
                }

                if (_requestQueue.size() > 0) {
                    _requestQueue.remove(0); // Remove this request
                }
                _retryCount = 0;

                if (callback != null) {
                    callback.s2sCallback(this, jsonResponse);
                }

                // Do next request in queue
                if (/*_state == STATE_CONNECTED && */_requestQueue.size() > 0) {
                    Request nextRequest = _requestQueue.get(0);
                    sendRequest(nextRequest.getJson(), nextRequest.getCallback());
                }
            } catch (JSONException e) {
                LogString("JSON ERROR " + e.getMessage() + " t: " + new Date().toString());
                if (callback != null) {
                    callback.s2sCallback(this, generateError(CLIENT_NETWORK_ERROR, INVALID_REQUEST, e.getMessage()));
                }
            }
        });
    }

    private String readResponseBody(HttpURLConnection connection) {
        String responseBody = "";
        try {
            // Get server response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            responseBody = builder.toString();
        } catch (IOException e) {
            // In case of IOException we need to read body from ErrorSream
            try {
                if (connection.getContentLengthLong() > 0) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    String line;
                    StringBuilder builder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    responseBody = builder.toString();
                }
            } catch (IOException e2) {
                responseBody = "{\"status\":500,\"severity\":\"ERROR\" ,\"reason_code\":0,\"status_message\":\""+e2.getMessage()+"\"}";
            }
        } catch (Exception e) {
            responseBody = "{\"status\":500,\"severity\":\"ERROR\" ,\"reason_code\":0,\"status_message\":\""+e.getMessage()+"\"}";
            
        }
        return responseBody;
    }

    private void LogString(String s) {
        if (_loggingEnabled) {
            // for now use System.out as unit tests do not support android.util.log class
            System.out.println("#BCC " + s);
        }
    }

    @Override
    public void run() {
        if (_sessionId != null) {
            JSONObject heartbeatMsg = new JSONObject();
            heartbeatMsg.put("service", "heartbeat");
            heartbeatMsg.put("operation", "HEARTBEAT");
            request(heartbeatMsg, null);
        }
    }

    /**
     * Terminate current session from server.
     * (New Session will automatically be created on next request)
     */
    public void disconnect() {
        stopHeartbeat();

        _state = STATE_DISCONNECTED;
        _retryCount = 0;
        _packetId = 0;
        _sessionId = null;
        _requestQueue.clear();
    }

    /*
     * Update requests and perform callbacks on the calling thread.
     */
    public void runCallbacks() {
        runCallbacks(0);
    }

    /*
     * Update requests and perform callbacks on the calling thread.
     * @param timeoutMS Time to block on the call in milliseconds. 
     *                  Pass 0 to return immediately.
     */
    public void runCallbacks(long timeoutMS) {

        long startTime = java.lang.System.currentTimeMillis();

        while (true) {

            JSONObject response = null;
            Request request = null;
            long responseCode = 0;

            synchronized(_lock) {
                if (_jsonResponse != null && _currentRequest != null) { // There's been a packet response

                    response = _jsonResponse;
                    responseCode = _responseCode;
                    request = _currentRequest;

                    _requestThread = null;
                    _jsonResponse = null;
                    _currentRequest = null;
                }
            }

            if (response != null && request != null) {

                // to avoid taking the json parsing hit even when logging is disabled
                if (_loggingEnabled) {
                    try {
                        LogString("INCOMING (" + responseCode + "): " + response.toString(2) + ", t: " + new Date().toString());
                    } catch (JSONException e) { }
                }

                // Start heartbeat 
                startHeartbeat();

                if (request.getCallback() != null) {
                    request.getCallback().s2sCallback(this, response);
                }

                break; // We got a response, leave.
            }

            if (java.lang.System.currentTimeMillis() - startTime > timeoutMS)
                break;

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}

package com.bitheads.brainclouds2s;

//----------------------------------------------------
// brainCloud client source code
// Copyright 2026 bitHeads, inc.
//----------------------------------------------------

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

/**
 * Handles the Pre-Ready Launch (PRL) flow for custom servers launched by brainCloud.
 *
 * When the PRE_READY_LAUNCH environment variable is "true", the server must wait
 * for the assigned lobby to reach the "starting" state before proceeding with launch.
 *
 * Usage:
 *   BrainCloudS2SPrl prl = new BrainCloudS2SPrl();
 *   if (prl.isPreReadyLaunch()) {
 *       prl.start(s2sClient, lobbyId, proceed -> {
 *           if (proceed) { ... } else { System.exit(0); }
 *       });
 *       while (!prl.isComplete()) {
 *           s2sClient.runCallbacks();
 *           Thread.sleep(10);
 *       }
 *   }
 */
public class BrainCloudS2SPrl {

    public interface PRLCompleteCallback {
        void onComplete(boolean proceedWithLaunch);
    }

    private enum PrlState {
        Idle,
        ConnectingRTT,
        SubscribingChannel,
        NotifyingSessionStarted,
        QueryingLobbyState,
        WaitingForLobbyReady,
        Complete
    }

    private BrainCloudS2S _s2s;
    private String _lobbyId;
    private PRLCompleteCallback _callback;
    private PrlState _state = PrlState.Idle;
    private final AtomicBoolean _complete = new AtomicBoolean(false);
    private ScheduledExecutorService _scheduler;
    private ScheduledFuture<?> _timeoutFuture;

    /**
     * Returns true if the PRE_READY_LAUNCH environment variable is set to "true".
     */
    public boolean isPreReadyLaunch() {
        String val = System.getenv("PRE_READY_LAUNCH");
        return val != null && val.equalsIgnoreCase("true");
    }

    /**
     * Returns the timeout in seconds from PRL_TIMEOUT_SECS or
     * PRE_READY_LAUNCH_TIMEOUT_SECS environment variables. Defaults to 60.
     */
    public int getTimeoutSecs() {
        String val = System.getenv("PRL_TIMEOUT_SECS");
        if (val != null) {
            try { return Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {}
        }
        val = System.getenv("PRE_READY_LAUNCH_TIMEOUT_SECS");
        if (val != null) {
            try { return Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {}
        }
        return 60;
    }

    /**
     * Parses the SERVER_CONTEXT environment variable into a JSONObject.
     * Handles single-quoted and backslash-escaped JSON strings.
     */
    public JSONObject parseServerContext() {
        try {
            String val = System.getenv("SERVER_CONTEXT");
            if (val == null || val.isEmpty()) return new JSONObject();
            val = val.trim();
            if (val.startsWith("'") && val.endsWith("'")) {
                val = val.substring(1, val.length() - 1);
            }
            val = val.replace("\\\"", "\"");
            return new JSONObject(val);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Returns true when the PRL flow has completed
     * (either proceed-with-launch or exit decision made).
     */
    public boolean isComplete() {
        return _complete.get();
    }

    /**
     * Sends SYS_ROOM_SESSION_ENDED to notify brainCloud the server session has concluded.
     * Should be called before the server process exits.
     *
     * @param s2s      Authenticated S2S context
     * @param callback Optional callback; may be null
     */
    public void sendSessionEnded(BrainCloudS2S s2s, IS2SCallback callback) {
        log(s2s, "[PRL] Sending SYS_ROOM_SESSION_ENDED.");
        JSONObject data = new JSONObject();
        data.put("serverId", System.getenv("SERVER_ID"));
        data.put("serverContext", parseServerContext());
        JSONObject request = new JSONObject();
        request.put("service", "roomServer");
        request.put("operation", "SYS_ROOM_SESSION_ENDED");
        request.put("data", data);
        s2s.request(request, callback);
    }

    /**
     * Starts the PRL flow. Assumes the S2S context is already authenticated.
     *
     * Flow:
     *   1. Enable RTT
     *   2. Subscribe to the lobby status channel (chat/CHANNEL_CONNECT)
     *   3. Notify brainCloud the room session has started (roomServer/SYS_ROOM_SESSION_STARTED)
     *   4. Query the lobby state (lobby/GET_LOBBY_DATA)
     *   5. If state is "starting" → proceed. If "disbanded" or not found → exit.
     *      Otherwise wait for an RTT push with the state transition.
     *
     * Poll isComplete() alongside s2sClient.runCallbacks() until the flow finishes.
     *
     * @param s2s      Authenticated S2S context
     * @param lobbyId  The lobby ID assigned to this server
     * @param callback Called with true to proceed with launch, false to exit
     */
    public void start(BrainCloudS2S s2s, String lobbyId, PRLCompleteCallback callback) {
        _s2s = s2s;
        _lobbyId = lobbyId;
        _callback = callback;
        _state = PrlState.ConnectingRTT;
        _complete.set(false);

        int timeoutSecs = getTimeoutSecs();
        log(s2s, "[PRL] Starting PRL flow. lobbyId=" + lobbyId + ", timeoutSecs=" + timeoutSecs);

        // Register RTT callback for lobby state push notifications
        s2s.registerRTTRawCallback(eventJSON -> {
            if (_complete.get() || _state != PrlState.WaitingForLobbyReady) return;
            String lobbyState = parseLobbyStateFromRTT(eventJSON);
            if (lobbyState != null) {
                log(s2s, "[PRL] RTT lobby state update: " + lobbyState);
                handleLobbyState(lobbyState);
            }
        });

        // Start timeout timer
        if (timeoutSecs > 0) {
            _scheduler = Executors.newSingleThreadScheduledExecutor();
            _timeoutFuture = _scheduler.schedule(() -> {
                log(s2s, "[PRL] Timeout elapsed — exiting.");
                complete(false);
            }, timeoutSecs, TimeUnit.SECONDS);
        }

        // Step 1: Enable RTT
        s2s.enableRTT(new IRTTConnectCallback() {

            @Override
            public void rttConnectSuccess() {
                String channelId = buildChannelId();
                log(s2s, "[PRL] RTT connected. Subscribing to channel: " + channelId);
                _state = PrlState.SubscribingChannel;

                // Step 2: Subscribe to the lobby status channel
                JSONObject channelData = new JSONObject();
                channelData.put("channelId", channelId);
                channelData.put("maxReturn", 50);
                JSONObject channelRequest = new JSONObject();
                channelRequest.put("service", "chat");
                channelRequest.put("operation", "CHANNEL_CONNECT");
                channelRequest.put("data", channelData);

                s2s.request(channelRequest, (context, channelResult) -> {
                    if (channelResult == null || channelResult.getInt("status") != 200) {
                        log(s2s, "[PRL] Failed to subscribe to channel: " +
                                (channelResult != null ? channelResult.toString() : "null"));
                        complete(false);
                        return;
                    }

                    log(s2s, "[PRL] Channel subscribed. Notifying session started.");
                    _state = PrlState.NotifyingSessionStarted;

                    // Step 3: Notify brainCloud the room session has started
                    JSONObject sessionData = new JSONObject();
                    sessionData.put("serverId", System.getenv("SERVER_ID"));
                    sessionData.put("serverContext", parseServerContext());
                    JSONObject sessionRequest = new JSONObject();
                    sessionRequest.put("service", "roomServer");
                    sessionRequest.put("operation", "SYS_ROOM_SESSION_STARTED");
                    sessionRequest.put("data", sessionData);

                    s2s.request(sessionRequest, (ctx2, sessionResult) -> {
                        if (sessionResult == null || sessionResult.getInt("status") != 200) {
                            log(s2s, "[PRL] SYS_ROOM_SESSION_STARTED failed: " +
                                    (sessionResult != null ? sessionResult.toString() : "null"));
                            complete(false);
                            return;
                        }

                        log(s2s, "[PRL] Session started. Querying lobby state.");
                        _state = PrlState.QueryingLobbyState;

                        // Step 4: Query the current lobby state
                        JSONObject lobbyData = new JSONObject();
                        lobbyData.put("lobbyId", _lobbyId);
                        JSONObject lobbyRequest = new JSONObject();
                        lobbyRequest.put("service", "lobby");
                        lobbyRequest.put("operation", "GET_LOBBY_DATA");
                        lobbyRequest.put("data", lobbyData);

                        s2s.request(lobbyRequest, (ctx3, lobbyResult) -> {
                            String lobbyState = parseLobbyState(lobbyResult);
                            log(s2s, "[PRL] Initial lobby state: " +
                                    (lobbyState != null ? lobbyState : "null"));
                            handleLobbyState(lobbyState);
                        });
                    });
                });
            }

            @Override
            public void rttConnectFailure(String errorMessage) {
                log(s2s, "[PRL] RTT connection failed: " + errorMessage);
                complete(false);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void complete(boolean proceed) {
        if (_complete.getAndSet(true)) return; // already completed
        _state = PrlState.Complete;

        if (_timeoutFuture != null) {
            _timeoutFuture.cancel(false);
        }
        if (_scheduler != null) {
            _scheduler.shutdownNow();
        }
        if (_s2s != null) {
            _s2s.deregisterRTTRawCallback();
        }
        if (_callback != null) {
            _callback.onComplete(proceed);
        }
    }

    private String buildChannelId() {
        // Lobby ID format: <appId>:<instanceId>
        // Channel format:  <appId>:sy:_lobby_<instanceId>
        String instanceId = _lobbyId;
        int colonPos = _lobbyId.indexOf(':');
        if (colonPos >= 0) instanceId = _lobbyId.substring(colonPos + 1);
        return _s2s.getAppId() + ":sy:_lobby_" + instanceId;
    }

    // Parses GET_LOBBY_DATA response: { "status": 200, "data": { "state": "..." } }
    private String parseLobbyState(JSONObject result) {
        try {
            if (result == null || result.getInt("status") != 200) return null;
            return result.getJSONObject("data").getString("state");
        } catch (Exception e) {
            return null;
        }
    }

    // Parses RTT push: { "service": "chat", "operation": "INCOMING",
    //                    "data": { "content": { "data": { "lobby": { "state": "..." } } } } }
    private String parseLobbyStateFromRTT(JSONObject msg) {
        try {
            if (!msg.getString("service").equals("chat")) return null;
            if (!msg.getString("operation").equals("INCOMING")) return null;
            return msg.getJSONObject("data")
                      .getJSONObject("content")
                      .getJSONObject("data")
                      .getJSONObject("lobby")
                      .getString("state");
        } catch (Exception e) {
            return null;
        }
    }

    private void handleLobbyState(String lobbyState) {
        if (lobbyState == null) {
            log(_s2s, "[PRL] Lobby not found — exiting.");
            complete(false);
        } else if (lobbyState.equals("disbanded")) {
            log(_s2s, "[PRL] Lobby disbanded — exiting.");
            complete(false);
        } else if (lobbyState.equals("starting")) {
            log(_s2s, "[PRL] Lobby is starting — proceeding with launch.");
            complete(true);
        } else {
            log(_s2s, "[PRL] Lobby state is '" + lobbyState + "' — waiting for RTT update.");
            _state = PrlState.WaitingForLobbyReady;
        }
    }

    private void log(BrainCloudS2S s2s, String message) {
        if (s2s != null && s2s.getLogEnabled()) {
            System.out.println("#BCC " + message);
        }
    }
}

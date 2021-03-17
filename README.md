# brainclouds2s-java

Java library for brainCloud S2S

## Intro

**brainCloud** is a ready-made, cloud-based backend for the development of games, apps and things.
More detail can be found here: [https://getbraincloud.com/]()

This library provides a simple API for server-to-server communications with brainCloud. 

## Building

clone the repo. 

``` 
git clone https://github.com/getbraincloud/brainclouds2s-java.git
```

Build using maven
```
mvn package
```
The resulting jar file is in **target/brainclouds2s-x.x.x.jar**

## Sample Usage

### Using included org.json encoding/decoding
``` java
        import com.bitheads.brainclouds2s.BrainCloudS2S;
        import org.json.JSONArray;
        import org.json.JSONObject;

        String appId = "<Your app ID>";
        String serverName = "<Your Server defined name>";
        String serverSecret = "<Your server secret>";

        BrainCloudS2S instance = new BrainCloudS2S();
        instance.init(appId, serverName, serverSecret);

        JSONObject json = new JSONObject();
        json.put("service", "globalEntity");
        json.put("operation", "GET_LIST");

        JSONObject orderBy = new JSONObject();
        JSONObject where = new JSONObject();
        JSONObject params = new JSONObject();
        
        // Get the enity of type address
        where.put("entityType", "address");

        params.put("where", where);     // Set the where clause
        params.put("orderBy", orderBy); // Set the return order (none here)
        params.put("maxReturn", 5);     // Max 5 entity
        json.put("data",params);

        instance.request(json,  (BrainCloudS2S context, JSONObject jsonData) -> {
            if (jsonData.getInt("status").isEqual(200)) {
                JSONArray list = jsonData.getJSONObject("data").getJSONArray("entityList");
                // Now list contains the address entities.
                // Your code here.
            }
        });
```

### Using Your own JSON encoding/decoding

``` java
        import com.bitheads.braincloud.s2s.Brainclouds2s;
        import org.json.JSONArray;
        import org.json.JSONObject;

        String appId = "<Your app ID>";
        String serverName = "<Your Server defined name>";
        String serverSecret = "<Your server secret>";

        BrainCloudS2S instance = new BrainCloudS2S();
        instance.init(appId, serverName, serverSecret);

        String requestString ="{\"service\":\"globalEntity", \"operation\":\"READ__LIST\",\"data\":{\"where\":{\"entityType\":\"address\"},\"orderBy\":{\"data.address\":1},\"maxReturn\":50}";

        instance.request(requestString,  (BrainCloudS2S context, String jsonString) -> {
            // decode and evaluate jsonString
            // Your code here.
        });
```
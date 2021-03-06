package com.treasuredata.android.cdp;

import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("WeakerAccess")
public class CDPAPIException extends Exception {

    private int status;
    private String error;

    /**
     * @param status Error JSON response's status property or HTTP status code
     *               (the earlier should be preferred if exists)
     * @param error is nullable, in case the HTTP response body is not a JSON
     * @param message, either the `message` property in response JSON, or the entire body
     */
    CDPAPIException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    /**
     * @return Original "error" property from the responded error JSON from server,
     * or null be null if the response body is not a JSON
     **/
    public String getError() {
        return error;
    }

    /**
     * @return "status" property from the error JSON response,
     * or the actual HTTP Status Code responded from server if response body is not a JSON
     */
    public int getStatus() {
        return status;
    }

    /**
     * Will attempt to treat body as a JSON first,
     * if it's not then use the entire body as the message.
     **/
    static CDPAPIException from(int statusCode, String body) {
        try {
            return CDPAPIException.from(statusCode, new JSONObject(body));
        } catch (JSONException e) {
            // Ignore
        }
        return new CDPAPIException(statusCode, null, body);
    }

    /**
     * @param statusCode responded HTTP status code,
     *                   but only be used of json doesn't contain a
     *                   "status" property.
     * @param json Error body response from CDP API,
     *             the return exception wil prefer "status" property
     *             inside this result if it exists, otherwise use
     *             the provided status parameter.
     */
    static CDPAPIException from(int statusCode, JSONObject json) {
        if (json == null) {
            return new CDPAPIException(statusCode, null, null);
        }
        String error = json.optString("error", null);
        String message = json.optString("message", null);
        int status = json.optInt("status", statusCode);

        if (error == null && message == null) {
            // Hopefully doesn't happen, but if the received JSON Object contains unexpected schema,
            // then use the entire JSON as the exception message.
            return new CDPAPIException(status, null, json.toString());
        } else {
            return new CDPAPIException(status, error, message);
        }
    }

}

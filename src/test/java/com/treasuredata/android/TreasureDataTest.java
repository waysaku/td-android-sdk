package com.treasuredata.android;

import android.app.Application;
import android.content.Context;

import io.keen.client.java.KeenCallback;
import io.keen.client.java.KeenClient;
import io.keen.client.java.KeenProject;
import junit.framework.TestCase;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TreasureDataTest extends TestCase {
    private static final String DUMMY_API_KEY = "dummy_api_key";
    boolean onSuccessCalledForAddEvent;
    boolean onSuccessCalledForUploadEvents;
    Exception exceptionOnFailedCalledForAddEvent;
    Exception exceptionOnFailedCalledForUploadEvents;
    String errorCodeForAddEvent;
    String errorCodeForUploadEvents;

    class Event {
        String tag;
        Map<String, Object> event;
        Event(String tag, Map<String, Object>event) {
            this.tag = tag;
            this.event = event;
        }
    }

    class MockTDClient extends TDClient {
        Exception exceptionOnQueueEventCalled;
        Exception exceptionOnSendQueuedEventsCalled;
        String errorCodeOnQueueEventCalled;
        String errorCodeOnSendQueuedEventsCalled;
        List<Event> addedEvent = new ArrayList<Event>();

        MockTDClient(String apiKey) throws IOException {
            super(apiKey);
        }

        public void clearAddedEvent() {
            addedEvent = new ArrayList<Event>();
        }

        @Override
        public void queueEvent(KeenProject project, String eventCollection, Map<String, Object> event, Map<String, Object> keenProperties, KeenCallback callback) {
            if (exceptionOnQueueEventCalled == null) {
                addedEvent.add(new Event(eventCollection, event));
                callback.onSuccess();
            }
            else {
                if (callback instanceof KeenCallbackWithErrorCode) {
                    KeenCallbackWithErrorCode callbackWithErrorCode = (KeenCallbackWithErrorCode) callback;
                    callbackWithErrorCode.setErrorCode(errorCodeOnQueueEventCalled);
                    callbackWithErrorCode.onFailure(exceptionOnQueueEventCalled);
                }
                else {
                    callback.onFailure(exceptionOnQueueEventCalled);
                }
            }
        }

        @Override
        public void sendQueuedEventsAsync(KeenProject project, KeenCallback callback) {
            if (exceptionOnSendQueuedEventsCalled == null) {
                callback.onSuccess();
            }
            else {
                if (callback instanceof KeenCallbackWithErrorCode) {
                    ((KeenCallbackWithErrorCode) callback).setErrorCode(errorCodeOnSendQueuedEventsCalled);
                }
                callback.onFailure(exceptionOnSendQueuedEventsCalled);
            }
        }
    }

    private Context context;
    private MockTDClient client;
    private TreasureData td;

    private TreasureData createTreasureData(Context context, TDClient client) {
        return new TreasureData(context, client, null);
    }

    public void setUp() throws IOException {
        init();

        Application application = mock(Application.class);
        context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(application);

        client = new MockTDClient(DUMMY_API_KEY);
        td = spy(createTreasureData(context, client));
    }

    public void tearDown() {
        TreasureData.setSessionTimeoutMilli(Session.DEFAULT_SESSION_PENDING_MILLIS);
    }

    private void init() {
        onSuccessCalledForAddEvent = false;
        onSuccessCalledForUploadEvents = false;
        exceptionOnFailedCalledForAddEvent = null;
        exceptionOnFailedCalledForUploadEvents = null;
        errorCodeForAddEvent = null;
        errorCodeForUploadEvents = null;
    }

    private void enableCallbackForAddEvent() {
        TDCallback callback = new TDCallback() {
            @Override
            public void onSuccess() {
                onSuccessCalledForAddEvent = true;
                exceptionOnFailedCalledForAddEvent = null;
                errorCodeForAddEvent = null;
            }

            @Override
            public void onError(String errorCode, Exception e) {
                onSuccessCalledForAddEvent = false;
                exceptionOnFailedCalledForAddEvent = e;
                errorCodeForAddEvent = errorCode;
            }
        };
        td.setAddEventCallBack(callback);
    }

    private void enableCallbackForUploadEvents() {
        TDCallback callback = new TDCallback() {
            @Override
            public void onSuccess() {
                onSuccessCalledForUploadEvents = true;
                exceptionOnFailedCalledForUploadEvents = null;
                errorCodeForUploadEvents = null;
            }

            @Override
            public void onError(String errorCode, Exception e) {
                onSuccessCalledForUploadEvents = false;
                exceptionOnFailedCalledForUploadEvents = e;
                errorCodeForUploadEvents = errorCode;
            }
        };
        td.setUploadEventsCallBack(callback);
    }

    public void testApiKey() throws IOException, NoSuchAlgorithmException {
        String apikey1 = DUMMY_API_KEY + "1";
        MockTDClient client1 = new MockTDClient(apikey1);

        String apikey2 = DUMMY_API_KEY + "2";
        MockTDClient client2 = new MockTDClient(apikey2);

        assertNotSame(client1.getDefaultProject().getProjectId(), client2.getDefaultProject().getProjectId());
    }

    public void testAddEventAndUploadCustomEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        when(td.isCustomEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(0, client.addedEvent.size());
    }

    public void testEnableAddEventAndUploadAppLifecycleEventWhileCustomEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_app_lifecycle_event", "true");
        when(td.isAppLifecycleEventEnabled()).thenReturn(true);
        when(td.isCustomEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
    }

    public void testDisableAddEventAndUploadAppLifecycleEventWhileCustomEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_app_lifecycle_event", "true");
        when(td.isAppLifecycleEventEnabled()).thenReturn(false);
        when(td.isCustomEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(0, client.addedEvent.size());
    }

    public void testEnableAddEventAndUploadInAppPurchaseEventWhileCustomEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_in_app_purchase_event", "true");
        when(td.isInAppPurchaseEventEnabled()).thenReturn(true);
        when(td.isCustomEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
    }
    public void testDisableAddEventAndUploadInAppPurchaseEventWhileCustomEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_in_app_purchase_event", "true");
        when(td.isInAppPurchaseEventEnabled()).thenReturn(false);
        when(td.isCustomEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(0, client.addedEvent.size());
    }

    public void testEnableAddEventAndUploadInAppPurchaseEventWhileOtherEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_in_app_purchase_event", "true");
        when(td.isInAppPurchaseEventEnabled()).thenReturn(true);
        when(td.isCustomEventEnabled()).thenReturn(false);
        when(td.isAppLifecycleEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
    }
    public void testDisableAddEventAndUploadInAppPurchaseEventWhileOtherEventDisabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        records.put("__is_in_app_purchase_event", "true");
        when(td.isInAppPurchaseEventEnabled()).thenReturn(false);
        when(td.isCustomEventEnabled()).thenReturn(false);
        when(td.isAppLifecycleEventEnabled()).thenReturn(false);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(0, client.addedEvent.size());
    }
    
    public void testAddEventAndUploadCustomEventEnabled() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        when(td.isCustomEventEnabled()).thenReturn(true);
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
    }

    public void testAddEventAndUploadEventsWithoutCallBack() throws IOException {
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventAndUploadEventsWithoutCallBackWithServerSideUploadTimestamp() throws IOException {
        td.enableServerSideUploadTimestamp();
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
        assertTrue(client.addedEvent.get(0).event.containsKey("#SSUT"));
        assertTrue(client.addedEvent.get(0).event.containsValue(true));
    }

    public void testDisableServerSideUploadTimestampForDefaultColumnName() throws IOException {
        td.enableServerSideUploadTimestamp();
        td.disableServerSideUploadTimestamp();
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventAndUploadEventsWithServerSideUploadTimestampWithCustomizedColumnName() throws IOException {
        td.enableServerSideUploadTimestamp("uploaded_time");
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
        assertTrue(client.addedEvent.get(0).event.containsKey("#SSUT"));
        assertTrue(client.addedEvent.get(0).event.containsValue("uploaded_time"));
    }

    public void testDisableServerSideUploadTimestampForCustomizedColumnName() throws IOException {
        td.enableServerSideUploadTimestamp("uploaded_time");
        td.disableServerSideUploadTimestamp();
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventAndUploadEventsWithServerSideUploadTimestampWithEmptyColumnName() throws IOException {
        td.enableServerSideUploadTimestamp(null);
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventWithUniqueRecordIdWithDefaultColumnName() throws IOException {
        td.enableAutoAppendRecordUUID();
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
        assertTrue(client.addedEvent.get(0).event.containsKey("record_uuid"));
    }

    public void testDisableAutoAppendRecordUUID() throws IOException {
        td.enableAutoAppendRecordUUID();
        td.disableAutoAppendRecordUUID();
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventWithUniqueRecordIdWithCustomizedColumnName() throws IOException {
        td.enableAutoAppendRecordUUID("my_record_id");
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
        assertTrue(client.addedEvent.get(0).event.containsKey("my_record_id"));
    }

    public void testAddEventWithUniqueRecordIdWithEmptyColumnName() throws IOException {
        td.enableAutoAppendRecordUUID(null);
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventAndUploadEventsWithDefaultDatabaseWithoutCallBack() throws IOException {
        td.setDefaultDatabase("db_");
        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        td.uploadEvents();
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventWithSuccess() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testDeprecatedStartSession() throws IOException {
        td.setDefaultDatabase("db_");

        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        td.startSession("tbl");
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        assertTrue(((String) client.addedEvent.get(0).event.get("td_session_id")).length() > 0);
        assertEquals("start", client.addedEvent.get(0).event.get("td_session_event"));
    }

    public void testDeprecatedEndSessionWithoutStartSession() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        td.endSession("db_", "tbl");
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertEquals("end", client.addedEvent.get(0).event.get("td_session_event"));
    }

    public void testDeprecatedStartSessionAndEndSession() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        assertNull(td.getSessionId());
        td.startSession("db_", "tbl");
        String sessionIdFromAPI = td.getSessionId();
        assertNotNull(sessionIdFromAPI);

        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(2, client.addedEvent.get(0).event.size());
        String sessionId = (String) client.addedEvent.get(0).event.get("td_session_id");
        assertEquals(sessionIdFromAPI, sessionId);
        assertTrue(sessionId.length() > 0);
        assertEquals("start", client.addedEvent.get(0).event.get("td_session_event"));

        td.setDefaultDatabase("db_");

        assertEquals(sessionIdFromAPI, td.getSessionId());
        td.endSession("tbl");
        assertNull(td.getSessionId());
        
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(2, client.addedEvent.size());

        Event endEvent = client.addedEvent.get(1);
        assertEquals("db_.tbl", endEvent.tag);
        assertEquals(2, endEvent.event.size());
        assertEquals(sessionId, endEvent.event.get("td_session_id"));
        assertEquals("end", endEvent.event.get("td_session_event"));
    }

    public void testStartSessionAndEndSession() throws IOException, InterruptedException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        td.setDefaultDatabase("db_");

        TreasureData.startSession(context);
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        Event event = client.addedEvent.get(0);
        assertEquals("db_.tbl", event.tag);
        assertEquals(2, event.event.size());
        assertEquals("val", event.event.get("key"));
        String firstSessionId = (String) client.addedEvent.get(0).event.get("td_session_id");
        assertTrue(firstSessionId.length() > 0);

        init();
        client.clearAddedEvent();

        TreasureData.endSession(context);
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());

        init();
        TimeUnit.MILLISECONDS.sleep(500);

        TreasureData.startSession(context);

        records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        event = client.addedEvent.get(0);
        assertEquals("db_.tbl", event.tag);
        assertEquals(2, event.event.size());
        assertEquals("val", event.event.get("key"));
        String secondSessionId = (String) client.addedEvent.get(0).event.get("td_session_id");
        assertEquals(firstSessionId, secondSessionId);
    }

    public void testStartSessionAndEndSessionUsingSetSessionTimeout() throws IOException, InterruptedException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        TreasureData.setSessionTimeoutMilli(200);

        td.setDefaultDatabase("db_");

        TreasureData.startSession(context);
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        Event event = client.addedEvent.get(0);
        assertEquals("db_.tbl", event.tag);
        assertEquals(2, event.event.size());
        assertEquals("val", event.event.get("key"));
        String firstSessionId = (String) client.addedEvent.get(0).event.get("td_session_id");
        assertTrue(firstSessionId.length() > 0);

        init();
        client.clearAddedEvent();

        TreasureData.endSession(context);
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());

        init();
        TimeUnit.MILLISECONDS.sleep(500);

        TreasureData.startSession(context);

        records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        event = client.addedEvent.get(0);
        assertEquals("db_.tbl", event.tag);
        assertEquals(2, event.event.size());
        assertEquals("val", event.event.get("key"));
        String secondSessionId = (String) client.addedEvent.get(0).event.get("td_session_id");
        assertNotSame(firstSessionId, secondSessionId);
    }

    public void testGetSessionId() throws IOException, InterruptedException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        assertNull(TreasureData.getSessionId(context));

        TreasureData.setSessionTimeoutMilli(200);

        TreasureData.startSession(context);
        assertTrue(TreasureData.getSessionId(context).length() > 0);

        TreasureData.endSession(context);
        assertNull(TreasureData.getSessionId(context));

        TimeUnit.MILLISECONDS.sleep(500);

        assertNull(TreasureData.getSessionId(context));

        TreasureData.startSession(context);
        assertTrue(TreasureData.getSessionId(context).length() > 0);
    }

    public void testAddEventWithSuccessWithDefaultDatabase() throws IOException {
        td.setDefaultDatabase("db_");

        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertTrue(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(1, client.addedEvent.size());
        assertEquals("db_.tbl", client.addedEvent.get(0).tag);
        assertEquals(1, client.addedEvent.get(0).event.size());
        assertTrue(client.addedEvent.get(0).event.containsKey("key"));
        assertTrue(client.addedEvent.get(0).event.containsValue("val"));
    }

    public void testAddEventWithErrorWithoutDefaultDatabase() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("tbl", records);
        assertFalse(onSuccessCalledForAddEvent);
        assertTrue(exceptionOnFailedCalledForAddEvent instanceof IllegalArgumentException);
        assertEquals(KeenClient.ERROR_CODE_INVALID_PARAM, errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }

    public void testAddEventWithError() throws IOException {
        client.exceptionOnQueueEventCalled = new IOException("hello world");
        client.errorCodeOnQueueEventCalled = KeenClient.ERROR_CODE_STORAGE_ERROR;

        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", "tbl", records);
        assertFalse(onSuccessCalledForAddEvent);
        assertTrue(exceptionOnFailedCalledForAddEvent instanceof IOException);
        assertEquals("hello world", exceptionOnFailedCalledForAddEvent.getMessage());
        assertEquals(KeenClient.ERROR_CODE_STORAGE_ERROR, errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }

    public void testAddEventWithDatabaseNameError() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        for (String db : Arrays.asList("db", "Db_", "db$")) {
            Map<String, Object> records = new HashMap<String, Object>();
            records.put("key", "val");
            td.addEvent(db, "tbl", records);
            assertFalse(onSuccessCalledForAddEvent);
            assertTrue(exceptionOnFailedCalledForAddEvent instanceof IllegalArgumentException);
            assertEquals(KeenClient.ERROR_CODE_INVALID_PARAM, errorCodeForAddEvent);
            assertFalse(onSuccessCalledForUploadEvents);
            assertNull(exceptionOnFailedCalledForUploadEvents);
            assertNull(errorCodeForUploadEvents);
        }
        assertEquals(0, client.addedEvent.size());
    }

    public void testAddEventWithTableNameError() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        for (String tbl : Arrays.asList("tb", "tBl", "tbl$")) {
            Map<String, Object> records = new HashMap<String, Object>();
            records.put("key", "val");
            td.addEvent("db", tbl, records);
            assertFalse(onSuccessCalledForAddEvent);
            assertTrue(exceptionOnFailedCalledForAddEvent instanceof IllegalArgumentException);
            assertEquals(KeenClient.ERROR_CODE_INVALID_PARAM, errorCodeForAddEvent);
            assertFalse(onSuccessCalledForUploadEvents);
            assertNull(exceptionOnFailedCalledForUploadEvents);
            assertNull(errorCodeForUploadEvents);
        }
        assertEquals(0, client.addedEvent.size());
    }

    public void testAddEventWithNullDatabaseName() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent(null, "tbl", records);
        assertFalse(onSuccessCalledForAddEvent);
        assertTrue(exceptionOnFailedCalledForAddEvent instanceof IllegalArgumentException);
        assertEquals(KeenClient.ERROR_CODE_INVALID_PARAM, errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }

    public void testAddEventWithNullTableName() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        Map<String, Object> records = new HashMap<String, Object>();
        records.put("key", "val");
        td.addEvent("db_", null, records);
        assertFalse(onSuccessCalledForAddEvent);
        assertTrue(exceptionOnFailedCalledForAddEvent instanceof IllegalArgumentException);
        assertEquals(KeenClient.ERROR_CODE_INVALID_PARAM, errorCodeForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }

    public void testUploadEventsWithSuccess() throws IOException {
        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        td.uploadEvents();
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertNull(errorCodeForAddEvent);
        assertTrue(onSuccessCalledForUploadEvents);
        assertNull(exceptionOnFailedCalledForUploadEvents);
        assertNull(errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }

    public void testUploadEventsWithError() throws IOException {
        client.exceptionOnSendQueuedEventsCalled = new IllegalArgumentException("foo bar");
        client.errorCodeOnSendQueuedEventsCalled = KeenClient.ERROR_CODE_NETWORK_ERROR;

        enableCallbackForAddEvent();
        enableCallbackForUploadEvents();

        td.uploadEvents();
        assertFalse(onSuccessCalledForAddEvent);
        assertNull(exceptionOnFailedCalledForAddEvent);
        assertFalse(onSuccessCalledForUploadEvents);
        assertNull(errorCodeForAddEvent);
        assertTrue(exceptionOnFailedCalledForUploadEvents instanceof IllegalArgumentException);
        assertEquals("foo bar", exceptionOnFailedCalledForUploadEvents.getMessage());
        assertEquals(KeenClient.ERROR_CODE_NETWORK_ERROR, errorCodeForUploadEvents);
        assertEquals(0, client.addedEvent.size());
    }
}
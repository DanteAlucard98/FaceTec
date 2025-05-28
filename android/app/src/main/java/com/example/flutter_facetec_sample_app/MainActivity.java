package com.example.flutter_facetec_sample_app;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKStatus;
import com.facetec.sdk.FaceTecSessionActivity;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;
import com.facetec.sdk.FaceTecIDScanProcessor;
import com.facetec.sdk.FaceTecIDScanResult;
import com.facetec.sdk.FaceTecIDScanResultCallback;
import com.facetec.sdk.FaceTecIDScanStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import org.json.JSONObject;
import org.json.JSONException;

public class MainActivity extends FlutterActivity implements FaceTecFaceScanProcessor, FaceTecIDScanProcessor {
    // MainActivity acts as a makeshift processor for the FaceTec session, since the actual
    // processing code in Dart cannot directly be called. It implements the required methods of a
    // processor delegate, processSessionWhileFaceTecSDKWaits() and onFaceTecSDKCompletelyDone().
    private static final String CHANNEL = "com.facetec.sdk";
    private static final String PROCESSOR_CHANNEL = "com.facetec.sdk/livenesscheck";
    private static final String PROCESSOR_CHANNEL_IDSCANN = "com.facetec.sdk/idscann";
    private static final String TAG = "MainActivity";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SESSION_RETRY_DELAY = 500;
    private static final long SELFIE_PROCESSING_TIMEOUT = 10000;
    private static final long SESSION_RESUME_DELAY = 500;
    private static final long SESSION_ACTIVITY_CHECK_DELAY = 100;
    private static final long SURFACE_RECOVERY_DELAY = 200;
    private static final long TRANSITION_DELAY = 1500;

    // Channels
    private MethodChannel processorChannel;
    private MethodChannel idscannChannel;

    // Session state flags
    private boolean isProcessing = false;
    private boolean isActivityPaused = false;
    private boolean isSessionInProgress = false;
    private boolean isIdScanMode = false;
    private boolean isSessionActive = false;
    private boolean isSessionResuming = false;
    private boolean isSessionActivityAttached = false;
    private boolean isSurfaceReleased = false;
    private boolean isSurfaceValid = true;
    private boolean isActivityDestroyed = false;
    private boolean isAppVisible = true;
    private boolean isActivityStopped = false;
    private boolean isSelfieProcessing = false;
    private boolean hasWindowFocus = false;
    private boolean isCameraInitialized = false;
    private int retryCount = 0;
    private boolean isPaused = false;
    private boolean isStopped = false;
    private boolean pendingSessionResume = false;

    // Callbacks and handlers
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private FaceTecIDScanResultCallback idScanResultCallbackRef;
    private Handler mainHandler;
    private Handler sessionHandler;
    private FaceTecSessionResult latestSessionResult;
    private FaceTecIDScanResult latestIDScanResult;
    private String latestExternalDatabaseRefID;
    private Processor currentProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeHandlers();
        
        // Preload FaceTec SDK resources
        FaceTecSDK.preload(this);
    }

    private void initializeHandlers() {
        mainHandler = new Handler(Looper.getMainLooper());
        sessionHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hasWindowFocus = hasFocus;
        Log.d(TAG, "Window focus changed: " + hasFocus);
        
        if (hasFocus && isSessionInProgress && !isSessionActive && !isSessionResuming && pendingSessionResume) {
            Log.d(TAG, "Window focus gained, attempting to resume session");
            isSessionResuming = true;
            retryResumeSession();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        
        if (!isSessionInProgress) {
            updateActivityState(true);
        }
        
        if (shouldResumeSession()) {
            resumeSession();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        
        isPaused = true;
        if (!isSessionInProgress) {
            updateActivityState(false);
        }
        
        // Don't cleanup callbacks if session is in progress
        if (!isSessionInProgress && shouldCleanupCallbacks()) {
            cleanupCallbacks();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
        
        isStopped = true;
        if (!isSessionInProgress) {
            isActivityStopped = true;
        }
        
        // Don't cleanup callbacks if session is in progress
        if (!isSessionInProgress && shouldCleanupCallbacks()) {
            cleanupCallbacks();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called");
        
        if (!isSessionInProgress) {
            isActivityStopped = false;
            isAppVisible = true;
        }
        
        if (shouldResumeSession()) {
            resumeSession();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        isActivityDestroyed = true;
        cleanupHandlers();
        
        if (shouldCleanupCallbacks()) {
            cleanupCallbacks();
        }
    }

    private void updateActivityState(boolean isActive) {
        if (!isSessionInProgress) {
            isActivityPaused = !isActive;
            isAppVisible = isActive;
            isSurfaceValid = isActive;
            isSurfaceReleased = !isActive;
        }
    }

    private boolean shouldResumeSession() {
        return isSessionInProgress && !isSessionActive && !isActivityDestroyed && !isActivityStopped && !isActivityPaused;
    }

    private boolean shouldCleanupCallbacks() {
        return !isSessionInProgress || (!isSessionActive && !isSessionResuming);
    }

    private void cleanupHandlers() {
        if (sessionHandler != null) {
            sessionHandler.removeCallbacksAndMessages(null);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    private void startIDScan() {
        if (!canStartSession()) {
            Log.d(TAG, "Cannot start session - activity not ready");
            return;
        }

        // Get session token from Flutter
        idscannChannel.invokeMethod("getSessionToken", null, new MethodChannel.Result() {
            @Override
            public void success(Object result) {
                if (result instanceof String) {
                    String sessionToken = (String) result;
                    // Create and start the ID scan processor
                    new PhotoIDScanProcessor(sessionToken, MainActivity.this, idscannChannel);
                } else {
                    Log.e(TAG, "Invalid session token received from Flutter");
                }
            }

            @Override
            public void error(String errorCode, String errorMessage, Object errorDetails) {
                Log.e(TAG, "Error getting session token: " + errorCode + " - " + errorMessage);
            }

            @Override
            public void notImplemented() {
                Log.e(TAG, "getSessionToken method not implemented in Flutter");
            }
        });
    }

    private boolean canStartSession() {
        return !isPaused && !isStopped && hasWindowFocus;
    }

    private void initializeSessionState() {
        isIdScanMode = true;
        isSessionInProgress = true;
        isSessionActive = true;
        isProcessing = false;
        isCameraInitialized = false;
        isSurfaceValid = true;
        isSessionActivityAttached = false;
        isSurfaceReleased = false;
        retryCount = 0;
    }

    private void configureSession() {
        FaceTecSDK.setCustomization(getCurrentCustomization());
    }

    private void launchSession(String sessionToken) {
        FaceTecSessionActivity.createAndLaunchSession(
            (Context)this, 
            (FaceTecIDScanProcessor)this, 
            sessionToken
        );
    }

    private void handleSessionError(Exception e) {
        isSessionInProgress = false;
        isSessionActive = false;
        cleanupCallbacks();
    }

    private void resumeSession() {
        if (sessionHandler != null) {
            sessionHandler.removeCallbacksAndMessages(null);
        }
        
        sessionHandler = new Handler(Looper.getMainLooper());
        sessionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (canResumeSession()) {
                    Log.d(TAG, "Resuming session");
                    isSessionResuming = true;
                    resumeSessionCallback();
                    isSessionResuming = false;
                }
            }
        }, TRANSITION_DELAY);
    }

    private boolean canResumeSession() {
        return !isPaused && !isStopped && hasWindowFocus;
    }

    private void resumeSessionCallback() {
        if (isIdScanMode && idScanResultCallbackRef != null) {
            idScanResultCallbackRef.proceedToNextStep(null);
        } else if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.proceedToNextStep(null);
        }
    }

    private void cleanupCallbacks() {
        Log.d(TAG, "cleanupCallbacks called - isSessionInProgress: " + isSessionInProgress + 
              ", isSessionActive: " + isSessionActive + 
              ", isSurfaceValid: " + isSurfaceValid +
              ", isSelfieProcessing: " + isSelfieProcessing +
              ", isSessionResuming: " + isSessionResuming +
              ", isSessionActivityAttached: " + isSessionActivityAttached +
              ", isSurfaceReleased: " + isSurfaceReleased);
              
        if (shouldCleanupCallbacks()) {
            cleanupHandlers();
            cleanupCallbacksOnUiThread();
            resetSessionState();
        }
    }

    private void cleanupCallbacksOnUiThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cleanupFaceScanCallback();
                cleanupIDScanCallback();
            }
        });
    }

    private void cleanupFaceScanCallback() {
        if (faceScanResultCallbackRef != null && shouldCleanupFaceScan()) {
            Log.d(TAG, "Cleaning up face scan callback");
            faceScanResultCallbackRef.cancel();
            faceScanResultCallbackRef = null;
        }
    }

    private boolean shouldCleanupFaceScan() {
        return !isSessionInProgress || !isSessionActive || !isSurfaceValid || !isSelfieProcessing || !isSessionResuming;
    }

    private void cleanupIDScanCallback() {
        if (idScanResultCallbackRef != null && shouldCleanupIDScan()) {
            Log.d(TAG, "Cleaning up ID scan callback");
            idScanResultCallbackRef.cancel();
            idScanResultCallbackRef = null;
        }
    }

    private boolean shouldCleanupIDScan() {
        return !isSessionInProgress || !isSessionActive || !isSurfaceValid || !isSessionResuming;
    }

    private void resetSessionState() {
        if (shouldResetSessionState()) {
            isProcessing = false;
            isIdScanMode = false;
            isCameraInitialized = false;
            isSelfieProcessing = false;
            isSessionResuming = false;
            isSessionActivityAttached = false;
            isSurfaceReleased = true;
            retryCount = 0;
        }
    }

    private boolean shouldResetSessionState() {
        return !isSessionInProgress || (!isSessionActive && !isSessionResuming) || !isSurfaceValid;
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        mainHandler = new Handler(Looper.getMainLooper());

        // configureFlutterEngine() creates processor channels for commmunicating with main.dart and LivenessCheckProcessor.dart.
        // Other processors you may create will be instantiated through another method channel.
        MethodChannel SDKChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL);
        processorChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), PROCESSOR_CHANNEL);
        idscannChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), PROCESSOR_CHANNEL_IDSCANN);

        SDKChannel.setMethodCallHandler(this::receivedFaceTecSDKMethodCall);
        processorChannel.setMethodCallHandler(this::receivedLivenessCheckProcessorCall);
        idscannChannel.setMethodCallHandler(this::receivedIdscannProcessorCall);

        setupMethodChannel();
    }

    private void setupMethodChannel() {
        MethodChannel channel = new MethodChannel(getFlutterEngine().getDartExecutor().getBinaryMessenger(), "flutter_facetec_sample_app");
        channel.setMethodCallHandler((call, result) -> {
            if (call.method.equals("startPhotoIDScan")) {
                String sessionToken = call.argument("sessionToken");
                if (sessionToken != null) {
                    new PhotoIDMatchProcessor(sessionToken, this, processorChannel);
                    result.success(null);
                } else {
                    result.error("INVALID_ARGUMENT", "Session token is required", null);
                }
            } else {
                result.notImplemented();
            }
        });
    }

    private void receivedFaceTecSDKMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        // Used to handle calls received over the "com.facetec.sdk" channel.
        // Currently two methods are implemented: initialize and startLivenessCheck.
        // When you make a call in main.dart or another file linked to the "com.facetec.sdk"
        // method channel, it will be received here and you will need to add logic for handling
        // that request.
        switch (call.method) {
            case "initialize":
                if (call.hasArgument("deviceKeyIdentifier") && call.hasArgument("publicFaceScanEncryptionKey")) {
                    String deviceKeyIdentifier = call.argument("deviceKeyIdentifier");
                    String faceScanEncryptionKey = call.argument("publicFaceScanEncryptionKey");
                    initialize(deviceKeyIdentifier, faceScanEncryptionKey, result);
                }
                else {
                    result.error("InvalidArguments", "Missing deviceKeyIdentifier or publicFaceScanEncryptionKey", null);
                }
                break;
            case "startLivenessCheck":
                if (call.hasArgument("sessionToken")) {
                    String sessionToken = call.argument("sessionToken");
                    startLivenessCheck(sessionToken, result);
                }
                else {
                    result.error("InvalidArguments", "Missing sessionToken", null);
                }
                break;
            case "startIdscann":
                if (call.hasArgument("sessionToken")) {
                    String sessionToken = call.argument("sessionToken");
                    startIDScan();
                }
                else {
                    result.error("InvalidArguments", "Missing sessionToken", null);
                }
                break;
            case "startMatchIdScan":
                if (call.hasArgument("sessionToken")) {
                    String sessionToken = call.argument("sessionToken");
                    startMatchIdScan(sessionToken, result);
                }
                else {
                    result.error("InvalidArguments", "Missing sessionToken", null);
                }
                break;
            case "cancelSession":
                cleanupCallbacks();
                result.success(true);
                break;
            case "createAPIUserAgentString":
                String data = FaceTecSDK.createFaceTecAPIUserAgentString("");
                result.success(data);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void receivedLivenessCheckProcessorCall(MethodCall call, MethodChannel.Result result) {
        // Used to handle calls received over "com.facetec.sdk/livenesscheck".
        // Currently there is only one method needed, but your processor code may require
        // more communication between dart and native code. If so, you may want to implement
        // any processor code and then receive the results and handle updating logic or run code on completion here.
        Log.d(TAG, "call.method is " + call.method);
        switch (call.method) {
            case "cancelFaceScan":
                cancelFaceScan();
                break;
            case "onScanResultBlobReceived":
                if (call.hasArgument("scanResultBlob")) {
                    String scanResultBlob = call.argument("scanResultBlob");
                    onScanResultBlobReceived(scanResultBlob);
                }
                else {
                    result.error("InvalidArguments", "Missing arguments for onScanResultBlobReceived", null);
                }
                break;
            case "onScanResultUploadDelay":
                if (call.hasArgument("uploadMessage")) {
                    String uploadMessage = call.argument("uploadMessage");
                    onScanResultUploadDelay(uploadMessage);
                }
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void initialize(String deviceKeyIdentifier, String publicFaceScanEncryptionKey, MethodChannel.Result result) {
        if (isActivityPaused) {
            result.error("INITIALIZATION_ERROR", "Activity is paused", null);
            return;
        }

        final Context context = this;
        FaceTecSDK.setCustomization(getCurrentCustomization());

        // Initialize SDK in development mode without API communication
        FaceTecSDK.initializeInDevelopmentMode(context, deviceKeyIdentifier, publicFaceScanEncryptionKey, success -> {
            if (success) {
                result.success(true);
            }
            else {
                FaceTecSDKStatus status = FaceTecSDK.getStatus(context);
                result.error(status.name(), status.toString(), null);
            }
        });
    }

    private void startLivenessCheck(String sessionToken, MethodChannel.Result result) {
        if (isActivityPaused) {
            result.error("SESSION_ERROR", "Activity is paused", null);
            return;
        }
        
        isIdScanMode = false;
        FaceTecSDK.setCustomization(getCurrentCustomization());
        
        // Launch liveness check session
        FaceTecSessionActivity.createAndLaunchSession(
            (Context)this, 
            (FaceTecFaceScanProcessor)this, 
            sessionToken
        );
        result.success(true);
    }

    private void startMatchIdScan(String sessionToken, MethodChannel.Result result) {
        if (isActivityPaused && !isSessionInProgress) {
            result.error("SESSION_ERROR", "Activity is paused", null);
            return;
        }
        
        if (isActivityDestroyed) {
            result.error("SESSION_ERROR", "Activity is destroyed", null);
            return;
        }

        // Reset retry count for new session
        retryCount = 0;
        
        Log.d(TAG, "Starting match ID scan with token: " + sessionToken);
        isIdScanMode = true;
        isSessionInProgress = true;
        isSessionActive = true;
        isProcessing = false;
        isCameraInitialized = false;
        isSurfaceValid = true;
        isSessionActivityAttached = false;
        isSurfaceReleased = false;
        
        // Apply customization before starting session
        FaceTecSDK.setCustomization(getCurrentCustomization());
        
        try {
            Log.d(TAG, "Creating and launching match ID scan session");
            
            // Create and launch the PhotoIDMatchProcessor
            new PhotoIDMatchProcessor(sessionToken, this, processorChannel);
            result.success(true);
        } catch (Exception e) {
            Log.e(TAG, "Error launching match ID scan session: " + e.getMessage());
            result.error("SESSION_ERROR", e.getMessage(), null);
            isSessionInProgress = false;
            isSessionActive = false;
            isSessionActivityAttached = false;
            isSurfaceReleased = true;
            cleanupCallbacks();
        }
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(final FaceTecIDScanResult idScanResult, final FaceTecIDScanResultCallback idScanResultCallback) {
        if (isProcessing) {
            if (idScanResultCallback != null) {
                idScanResultCallback.cancel();
            }
            return;
        }
        
        isProcessing = true;
        idScanResultCallbackRef = idScanResultCallback;
        latestIDScanResult = idScanResult;

        // Handle early exit scenarios
        if(idScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
            Log.d(TAG, "Session was not completed successfully, cancelling.");
            cleanupCallbacks();
            return;
        }

        try {
            // Get essential data off the FaceTecIDScanResult
            JSONObject parameters = new JSONObject();
            parameters.put("idScan", idScanResult.getIDScanBase64());

            ArrayList<String> frontImagesCompressedBase64 = idScanResult.getFrontImagesCompressedBase64();
            ArrayList<String> backImagesCompressedBase64 = idScanResult.getBackImagesCompressedBase64();
            if(frontImagesCompressedBase64.size() > 0) {
                parameters.put("idScanFrontImage", frontImagesCompressedBase64.get(0));
            }
            if(backImagesCompressedBase64.size() > 0) {
                parameters.put("idScanBackImage", backImagesCompressedBase64.get(0));
            }

            // Configure upload messages before starting the process
            FaceTecCustomization.setIDScanUploadMessageOverrides(
                "Uploading\nEncrypted\nID Scan",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing ID Scan",
                "Uploading\nEncrypted\nBack of ID",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing Back of ID",
                "Saving\nYour Confirmed Info",
                "Still Uploading...\nSlow Connection",
                "Info Saved",
                "Processing",
                "Uploading Encrypted\nNFC Details",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing\nNFC Details",
                "Uploading Encrypted\nID Details",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing\nID Details"
            );

            // Configure result screen messages before proceeding
            FaceTecCustomization.setIDScanResultScreenMessageOverrides(
                "Front Scan Complete",
                "Front of ID\nScanned",
                "Front of ID\nScanned",
                "ID Scan Complete",
                "Back of ID\nScanned",
                "Passport Scan Complete",
                "Passport Scanned",
                "Photo ID Scan\nComplete",
                "ID Scan Complete",
                "ID Photo Capture\nComplete",
                "Face Didn't Match\nHighly Enough",
                "ID Document\nNot Fully Visible",
                "ID Text Not Legible",
                "ID Type Mismatch\nPlease Try Again",
                "ID Details\nUploaded"
            );

            // Prepare arguments to send to Flutter
            final Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", idScanResult.getSessionId());
            args.put("scanType", "idScan");
            args.put("success", true);
            args.put("idScanBase64", idScanResult.getIDScanBase64());
            
            if(frontImagesCompressedBase64.size() > 0) {
                args.put("idScanFrontImage", frontImagesCompressedBase64.get(0));
            }
            if(backImagesCompressedBase64.size() > 0) {
                args.put("idScanBackImage", backImagesCompressedBase64.get(0));
            }

            // Send data to Flutter for processing
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Send session data to Flutter and wait for response
                        idscannChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                            @Override
                            public void success(Object result) {
                                try {
                                    if (result instanceof Map) {
                                        Map<String, Object> resultMap = (Map<String, Object>) result;
                                        
                                        // Create response similar to the server response in the example
                                        JSONObject responseJSON = new JSONObject();
                                        responseJSON.put("wasProcessed", true);
                                        responseJSON.put("error", false);
                                        
                                        // If Flutter sent back a scanResultBlob, use it
                                        if (resultMap.containsKey("scanResultBlob")) {
                                            responseJSON.put("scanResultBlob", resultMap.get("scanResultBlob"));
                                        } else {
                                            // Otherwise use our parameters as the scanResultBlob
                                            responseJSON.put("scanResultBlob", parameters.toString());
                                        }
                                        
                                        String responseString = responseJSON.toString();
                                        Log.d(TAG, "Using response: " + responseString);
                                        
                                        // Complete the progress bar
                                        if (idScanResultCallbackRef != null) {
                                            idScanResultCallbackRef.uploadProgress(1);
                                            
                                            // Proceed to next step with the scan result
                                            boolean success = idScanResultCallbackRef.proceedToNextStep(responseString);
                                            
                                            if (success) {
                                                Log.d(TAG, "Successfully proceeded to next step");
                                            } else {
                                                Log.d(TAG, "Failed to proceed to next step");
                                                cleanupCallbacks();
                                            }
                                        }
                                    } else {
                                        // Handle case where Flutter returns something other than a Map
                                        defaultResponse();
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error handling Flutter response: " + e.getMessage());
                                    e.printStackTrace();
                                    defaultResponse();
                                }
                            }

                            @Override
                            public void error(String errorCode, String errorMessage, Object errorDetails) {
                                Log.e(TAG, "Flutter returned error: " + errorCode + " - " + errorMessage);
                                defaultResponse();
                            }

                            @Override
                            public void notImplemented() {
                                Log.e(TAG, "Method not implemented in Flutter");
                                defaultResponse();
                            }
                            
                            private void defaultResponse() {
                                try {
                                    // Create fallback response in the same format
                                    JSONObject fallbackResponse = new JSONObject();
                                    fallbackResponse.put("wasProcessed", true);
                                    fallbackResponse.put("error", false);
                                    fallbackResponse.put("scanResultBlob", parameters.toString());
                                    
                                    if (idScanResultCallbackRef != null) {
                                        idScanResultCallbackRef.uploadProgress(1);
                                        idScanResultCallbackRef.proceedToNextStep(fallbackResponse.toString());
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Fallback response failed: " + ex.getMessage());
                                    cleanupCallbacks();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in ID scan processing: " + e.getMessage());
                        e.printStackTrace();
                        cleanupCallbacks();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing ID scan: " + e.getMessage());
            e.printStackTrace();
            cleanupCallbacks();
        }
    }

    // Override the existing processSessionWhileFaceTecSDKWaits to handle only liveness checks
    @Override
    public void processSessionWhileFaceTecSDKWaits(final FaceTecSessionResult sessionResult, final FaceTecFaceScanResultCallback faceScanResultCallback) {
        if (!isIdScanMode) {
            // Handle regular liveness check
            if (isProcessing || isActivityPaused) {
                if (faceScanResultCallback != null) {
                    faceScanResultCallback.cancel();
                }
                return;
            }
            
            isProcessing = true;
            isSessionActive = true;
            faceScanResultCallbackRef = faceScanResultCallback;

            if (sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
                Log.d(TAG, "Status was not successful, status: " + sessionResult.getStatus());
                isSessionActive = false;
                cleanupCallbacks();
                return;
            }

            try {
                // Send mock success response without API communication
                final Map<String, Object> args = new HashMap<>();
                args.put("status", "sessionCompletedSuccessfully");
                args.put("sessionId", sessionResult.getSessionId());
                args.put("scanType", "liveness");
                args.put("lowQualityAuditTrailCompressedBase64", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);
                args.put("auditTrailCompressedBase64", sessionResult.getAuditTrailCompressedBase64()[0]);
                args.put("faceScanBase64", sessionResult.getFaceScanBase64());

                // Create minimal success JSON response
                final String successResponse = "{\"success\":true,\"data\":{\"livenessStatus\":\"Successful\"}}";

                // Post immediate success response
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isActivityPaused && faceScanResultCallbackRef != null) {
                                processorChannel.invokeMethod("processSession", args);
                                Log.d(TAG, "Liveness check successful, proceeding with response");
                                faceScanResultCallbackRef.proceedToNextStep(successResponse);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in liveness processing: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            cleanupCallbacks();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing liveness check: " + e.getMessage());
                e.printStackTrace();
                cleanupCallbacks();
            }
            return;
        }

        // Handle face scan for match ID process
        if (isProcessing || isActivityPaused) {
            if (faceScanResultCallback != null) {
                faceScanResultCallback.cancel();
            }
            return;
        }
        
        isProcessing = true;
        isSelfieProcessing = true;
        isSessionActive = true;
        faceScanResultCallbackRef = faceScanResultCallback;

        if (sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d(TAG, "Face scan session was not completed successfully");
            isSessionActive = false;
            isSelfieProcessing = false;
            cleanupCallbacks();
            return;
        }

        try {
            // Prepare face scan data
            final Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", sessionResult.getSessionId());
            args.put("scanType", "faceScan");
            args.put("faceScanBase64", sessionResult.getFaceScanBase64());
            args.put("auditTrailCompressedBase64", sessionResult.getAuditTrailCompressedBase64()[0]);
            args.put("lowQualityAuditTrailCompressedBase64", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);

            // Set a timeout for selfie processing
            sessionHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isSelfieProcessing) {
                        Log.e(TAG, "Selfie processing timeout reached");
                        isSelfieProcessing = false;
                        if (faceScanResultCallbackRef != null) {
                            faceScanResultCallbackRef.cancel();
                        }
                        cleanupCallbacks();
                    }
                }
            }, SELFIE_PROCESSING_TIMEOUT);

            // Send data to Flutter for processing
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        processorChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                            @Override
                            public void success(Object result) {
                                try {
                                    if (result instanceof Map) {
                                        Map<String, Object> resultMap = (Map<String, Object>) result;
                                        String scanResultBlob = (String) resultMap.get("scanResultBlob");
                                        
                                        if (scanResultBlob != null && faceScanResultCallbackRef != null) {
                                            isSelfieProcessing = false;
                                            // Don't cleanup callbacks here to allow ID scan to start
                                            faceScanResultCallbackRef.proceedToNextStep(scanResultBlob);
                                        } else {
                                            isSelfieProcessing = false;
                                            faceScanResultCallbackRef.cancel();
                                            cleanupCallbacks();
                                        }
                                    } else {
                                        isSelfieProcessing = false;
                                        faceScanResultCallbackRef.cancel();
                                        cleanupCallbacks();
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing face scan result: " + e.getMessage());
                                    isSelfieProcessing = false;
                                    if (faceScanResultCallbackRef != null) {
                                        faceScanResultCallbackRef.cancel();
                                    }
                                    cleanupCallbacks();
                                }
                            }

                            @Override
                            public void error(String errorCode, String errorMessage, Object errorDetails) {
                                Log.e(TAG, "Error from Flutter: " + errorCode + " - " + errorMessage);
                                isSelfieProcessing = false;
                                if (faceScanResultCallbackRef != null) {
                                    faceScanResultCallbackRef.cancel();
                                }
                                cleanupCallbacks();
                            }

                            @Override
                            public void notImplemented() {
                                Log.e(TAG, "Method not implemented in Flutter");
                                isSelfieProcessing = false;
                                if (faceScanResultCallbackRef != null) {
                                    faceScanResultCallbackRef.cancel();
                                }
                                cleanupCallbacks();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in face scan processing: " + e.getMessage());
                        isSelfieProcessing = false;
                        if (faceScanResultCallbackRef != null) {
                            faceScanResultCallbackRef.cancel();
                        }
                        cleanupCallbacks();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing face scan: " + e.getMessage());
            isSelfieProcessing = false;
            if (faceScanResultCallbackRef != null) {
                faceScanResultCallbackRef.cancel();
            }
            cleanupCallbacks();
        }
    }

    // Called when FaceTec SDK is completely done
    public void onFaceTecSDKCompletelyDone() {
        Log.d(TAG, "onFaceTecSDKCompletelyDone called - isSessionInProgress: " + isSessionInProgress + 
              ", isSessionActive: " + isSessionActive + 
              ", isSurfaceValid: " + isSurfaceValid +
              ", isSessionResuming: " + isSessionResuming +
              ", isSessionActivityAttached: " + isSessionActivityAttached +
              ", isSurfaceReleased: " + isSurfaceReleased);
        isSessionActive = false;
        isSessionResuming = false;
        isSessionActivityAttached = false;
        isSurfaceReleased = true;
        if (!isSessionInProgress) {
            cleanupCallbacks();
        }
    }

    private void cancelFaceScan() {
        Log.e(TAG, "Face Scan result cancelled");
        cleanupCallbacks();
    }

    private String createSuccessResponse() {
        try {
            JSONObject response = new JSONObject();
            response.put("wasProcessed", true);
            response.put("error", false);
            response.put("scanResultBlob", "{}");
            return response.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating success response: " + e.getMessage());
            return "{\"wasProcessed\":true,\"error\":false,\"scanResultBlob\":\"{}\"}";
        }
    }

    private void onScanResultBlobReceived(String scanResultBlob) {
        Log.d(TAG, "onScanResultBlobReceived: Processing scan result");
        
        if (isIdScanMode) {
            if (idScanResultCallbackRef != null) {
                Log.d(TAG, "Proceeding with ID scan result");
                try {
                    String response = createSuccessResponse();
                    Log.d(TAG, "Using formatted response: " + response);
                    idScanResultCallbackRef.proceedToNextStep(response);
                    
                    // Only clean up if we're not expecting a back scan
                    if (!isSessionInProgress) {
                        idScanResultCallbackRef = null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error proceeding with ID scan: " + e.getMessage());
                    try {
                        String simpleResponse = createSuccessResponse();
                        idScanResultCallbackRef.proceedToNextStep(simpleResponse);
                    } catch (Exception ex) {
                        Log.e(TAG, "Fallback response failed: " + ex.getMessage());
                        idScanResultCallbackRef = null;
                    }
                }
            }
        } else {
            if (faceScanResultCallbackRef != null) {
                Log.d(TAG, "Proceeding with face scan result");
                try {
                    String response = createSuccessResponse();
                    Log.d(TAG, "Using formatted response: " + response);
                    faceScanResultCallbackRef.proceedToNextStep(response);
                } catch (Exception e) {
                    Log.e(TAG, "Error proceeding with face scan: " + e.getMessage());
                    try {
                        String simpleResponse = createSuccessResponse();
                        faceScanResultCallbackRef.proceedToNextStep(simpleResponse);
                    } catch (Exception ex) {
                        Log.e(TAG, "Fallback response failed: " + ex.getMessage());
                    }
                }
                faceScanResultCallbackRef = null;
            }
        }
        
        // Only mark session as completed if we're not transitioning to back scan
        if (!isIdScanMode || !isSessionInProgress) {
            Log.d(TAG, "Scan completed, cleaning up session");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isSessionInProgress = false;
                    isProcessing = false;
                }
            });
        }
    }

    private void onScanResultUploadDelay(String uploadMessage) {
        // Handle if there is a long delay in uploading the face scan to the server
        Log.d(TAG, "Face Scan taking longer than usual, adding upload delay message.");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.uploadMessageOverride(uploadMessage);
        }
    }

    private void receivedIdscannProcessorCall(MethodCall call, MethodChannel.Result result) {
        Log.d(TAG, "call.method is " + call.method);
        switch (call.method) {
            case "cancelSession":
                cleanupCallbacks();
                result.success(true);
                break;
            case "createAPIUserAgentString":
                String userAgent = FaceTecSDK.createFaceTecAPIUserAgentString("");
                result.success(userAgent);
                break;
            case "onScanResultBlobReceived":
                if (call.hasArgument("scanResultBlob")) {
                    Log.d(TAG, "Received scan result blob from Flutter");
                    onScanResultBlobReceived(call.argument("scanResultBlob"));
                    result.success(true);
                } else {
                    result.error("InvalidArguments", "Missing arguments for onScanResultBlobReceived", null);
                }
                break;
            case "onScanResultUploadDelay":
                if (call.hasArgument("uploadMessage")) {
                    String uploadMessage = call.argument("uploadMessage");
                    onScanResultUploadDelay(uploadMessage);
                    result.success(true);
                }
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void reattachSessionActivity() {
        if (isSessionInProgress && !isSessionActivityAttached) {
            Log.d(TAG, "Attempting to reattach session activity");
            sessionHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Force a layout update to recreate the surface
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                View decorView = getWindow().getDecorView();
                                decorView.requestLayout();
                                decorView.invalidate();
                                isSessionActivityAttached = true;
                                Log.d(TAG, "Session activity reattachment initiated");
                                
                                // Try to resume the session after reattachment
                                if (isSessionInProgress && !isSessionActive) {
                                    retryResumeSession();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error reattaching session activity: " + e.getMessage());
                        isSessionActivityAttached = false;
                        cleanupCallbacks();
                    }
                }
            }, SESSION_ACTIVITY_CHECK_DELAY);
        }
    }

    public void setLatestSessionResult(FaceTecSessionResult sessionResult) {
        this.latestSessionResult = sessionResult;
    }

    public void setLatestIDScanResult(FaceTecIDScanResult idScanResult) {
        this.latestIDScanResult = idScanResult;
    }

    public String getLatestExternalDatabaseRefID() {
        return this.latestExternalDatabaseRefID;
    }

    public void setLatestExternalDatabaseRefID(String externalDatabaseRefID) {
        this.latestExternalDatabaseRefID = externalDatabaseRefID;
    }

    private void retryResumeSession() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts reached, cleaning up session");
            isSessionResuming = false;
            cleanupCallbacks();
            return;
        }

        retryCount++;
        Log.d(TAG, "Retry attempt " + retryCount + " of " + MAX_RETRY_ATTEMPTS);

        // Check if surface was released
        if (isSurfaceReleased) {
            Log.d(TAG, "Surface was released, attempting to recover");
            recoverSurface();
            return;
        }

        // Check if FaceTecSessionActivity is still attached
        if (!isSessionActivityAttached) {
            Log.d(TAG, "Session activity not attached, attempting to reattach");
            reattachSessionActivity();
            return;
        }

        sessionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isActivityPaused && !isActivityDestroyed && hasWindowFocus && isSessionActivityAttached && !isSurfaceReleased) {
                    isProcessing = true;
                    isSessionActive = true;
                    isSessionResuming = false;
                    Log.d(TAG, "Session resumed successfully");
                } else {
                    Log.d(TAG, "Activity not ready for session resume, will retry");
                    retryResumeSession();
                }
            }
        }, SESSION_RESUME_DELAY);
    }

    private void recoverSurface() {
        if (isSessionInProgress && isSurfaceReleased) {
            Log.d(TAG, "Attempting to recover surface");
            sessionHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Force a layout update to recreate the surface
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                View decorView = getWindow().getDecorView();
                                decorView.requestLayout();
                                decorView.invalidate();
                                isSurfaceReleased = false;
                                Log.d(TAG, "Surface recovery initiated");
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error recovering surface: " + e.getMessage());
                        isSurfaceReleased = true;
                        cleanupCallbacks();
                    }
                }
            }, SURFACE_RECOVERY_DELAY);
        }
    }

    private FaceTecCustomization getCurrentCustomization() {
        FaceTecCustomization customization = new FaceTecCustomization();
        
        // Set branding image
        customization.getOverlayCustomization().brandingImage = R.drawable.flutter_logo;
        
        // Configure ID scan specific settings
        customization.getIdScanCustomization().showSelectionScreenDocumentImage = true;
        customization.getIdScanCustomization().captureScreenBackgroundColor = android.graphics.Color.WHITE;
        customization.getIdScanCustomization().buttonBackgroundNormalColor = android.graphics.Color.BLACK;
        customization.getIdScanCustomization().buttonTextNormalColor = android.graphics.Color.WHITE;
        customization.getIdScanCustomization().buttonTextHighlightColor = android.graphics.Color.WHITE;

        // Configure upload messages
        FaceTecCustomization.setIDScanUploadMessageOverrides(
            "Uploading\nEncrypted\nID Scan",
            "Still Uploading...\nSlow Connection",
            "Upload Complete",
            "Processing ID Scan",
            "Uploading\nEncrypted\nBack of ID",
            "Still Uploading...\nSlow Connection",
            "Upload Complete",
            "Processing Back of ID",
            "Saving\nYour Confirmed Info",
            "Still Uploading...\nSlow Connection",
            "Info Saved",
            "Processing",
            "Uploading Encrypted\nNFC Details",
            "Still Uploading...\nSlow Connection",
            "Upload Complete",
            "Processing\nNFC Details",
            "Uploading Encrypted\nID Details",
            "Still Uploading...\nSlow Connection",
            "Upload Complete",
            "Processing\nID Details"
        );

        // Configure result screen messages
        FaceTecCustomization.setIDScanResultScreenMessageOverrides(
            "Front Scan Complete",
            "Front of ID\nScanned",
            "Front of ID\nScanned",
            "ID Scan Complete",
            "Back of ID\nScanned",
            "Passport Scan Complete",
            "Passport Scanned",
            "Photo ID Scan\nComplete",
            "ID Scan Complete",
            "ID Photo Capture\nComplete",
            "Face Didn't Match\nHighly Enough",
            "ID Document\nNot Fully Visible",
            "ID Text Not Legible",
            "ID Type Mismatch\nPlease Try Again",
            "ID Details\nUploaded"
        );
        
        return customization;
    }

    private void startPhotoIDMatch(String sessionToken) {
        try {
            // Create a new processor for the face scan
            PhotoIDMatchProcessor processor = new PhotoIDMatchProcessor(sessionToken, this, processorChannel);
            
            // Store the processor for later use
            currentProcessor = processor;
        } catch (Exception e) {
            Log.e(TAG, "Error starting Photo ID Match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startPhotoIDScan(String sessionToken) {
        try {
            // Create a new processor for the ID scan
            PhotoIDScanProcessor processor = new PhotoIDScanProcessor(sessionToken, this, processorChannel);
            
            // Store the processor for later use
            currentProcessor = processor;
        } catch (Exception e) {
            Log.e(TAG, "Error starting Photo ID Scan: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processSessionResult(FaceTecSessionResult sessionResult) {
        if (sessionResult == null) {
            Log.d(TAG, "Session was cancelled or failed");
            isSessionInProgress = false;
            isSessionActive = false;
            cleanupCallbacks();
            return;
        }

        if (sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d(TAG, "Session was not completed successfully");
            isSessionInProgress = false;
            isSessionActive = false;
            cleanupCallbacks();
            return;
        }

        // Process the session result
        try {
            // Get essential data off the FaceTecSessionResult
            JSONObject parameters = new JSONObject();
            parameters.put("faceScan", sessionResult.getFaceScanBase64());
            parameters.put("auditTrailImage", sessionResult.getAuditTrailCompressedBase64()[0]);
            parameters.put("lowQualityAuditTrailImage", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);

            // Prepare arguments to send to Flutter
            final Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", sessionResult.getSessionId());
            args.put("scanType", "faceScan");
            args.put("faceScanBase64", sessionResult.getFaceScanBase64());
            args.put("auditTrailCompressedBase64", sessionResult.getAuditTrailCompressedBase64()[0]);
            args.put("lowQualityAuditTrailCompressedBase64", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);

            // Send data to Flutter for processing
            processorChannel.invokeMethod("processSession", args);
        } catch (Exception e) {
            Log.e(TAG, "Error processing session result: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isSessionInProgress = false;
            isSessionActive = false;
            cleanupCallbacks();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) { // FaceTec SDK request code
            if (currentProcessor == null) {
                return;
            }

            if (latestSessionResult != null && latestSessionResult.getStatus() != null) {
                Log.d(TAG, "Session Status: " + latestSessionResult.getStatus().toString());
            }

            // At this point, you have already handled all results in your Processor code
            if (!currentProcessor.isSuccess()) {
                // Reset any necessary state
                isSessionInProgress = false;
                isSessionActive = false;
                cleanupCallbacks();
            }
        }
    }
}
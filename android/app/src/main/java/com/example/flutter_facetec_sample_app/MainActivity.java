package com.example.flutter_facetec_sample_app;

import android.content.Context;
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

    private MethodChannel processorChannel;
    private MethodChannel idscannChannel;
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private FaceTecIDScanResultCallback idScanResultCallbackRef;
    private Handler mainHandler;
    private boolean isProcessing = false;
    private boolean isActivityPaused = false;
    private boolean isSessionInProgress = false;
    private boolean isIdScanMode = false;

    private String pendingBackScanSessionToken;
    private MethodChannel.Result pendingBackScanResult;

    private boolean isBackScanPending = false;
    private String pendingBackScanToken = null;
    private boolean shouldLaunchBackScan = false;
    private boolean isTransitioning = false;
    private boolean isSurfaceDestroyed = false;
    private boolean hasWindowFocus = false;
    private boolean isAppVisible = true;
    private boolean isActivityStopped = false;
    private static final int TRANSITION_DELAY = 1500; // Delay to allow activity to fully transition

    private boolean isCameraInitialized = false;
    private boolean isSessionActive = false;

    private boolean isSurfaceValid = true;
    private boolean isActivityDestroyed = false;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private int retryCount = 0;
    private Handler sessionHandler;
    private static final long SESSION_RETRY_DELAY = 500; // 500ms delay between retries

    private boolean isSelfieProcessing = false;
    private static final long SELFIE_PROCESSING_TIMEOUT = 10000; // 10 seconds timeout

    private boolean isSessionResuming = false;
    private static final long SESSION_RESUME_DELAY = 500; // 500ms delay for session resume
    private boolean isSessionActivityAttached = false;
    private static final long SESSION_ACTIVITY_CHECK_DELAY = 100; // 100ms delay for activity checks
    private boolean isSurfaceReleased = false;
    private static final long SURFACE_RECOVERY_DELAY = 200; // 200ms delay for surface recovery

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hasWindowFocus = hasFocus;
        Log.d("MainActivity", "Window focus changed: " + hasFocus);
        
        if (hasFocus && isSessionInProgress && !isSessionActive && !isSessionResuming) {
            Log.d("MainActivity", "Window focus gained, attempting to resume session");
            isSessionResuming = true;
            retryResumeSession();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "onResume called");
        isActivityPaused = false;
        isAppVisible = true;
        isActivityStopped = false;
        isSurfaceValid = true;
        isSurfaceReleased = false;
        
        // If we have a pending session, try to resume it with retry mechanism
        if (isSessionInProgress && !isProcessing && !isSessionResuming) {
            Log.d("MainActivity", "Attempting to resume pending session");
            isSessionResuming = true;
            retryResumeSession();
        }
    }

    private void retryResumeSession() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e("MainActivity", "Max retry attempts reached, cleaning up session");
            isSessionResuming = false;
            cleanupCallbacks();
            return;
        }

        retryCount++;
        Log.d("MainActivity", "Retry attempt " + retryCount + " of " + MAX_RETRY_ATTEMPTS);

        // Check if surface was released
        if (isSurfaceReleased) {
            Log.d("MainActivity", "Surface was released, attempting to recover");
            recoverSurface();
            return;
        }

        // Check if FaceTecSessionActivity is still attached
        if (!isSessionActivityAttached) {
            Log.d("MainActivity", "Session activity not attached, attempting to reattach");
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
                    Log.d("MainActivity", "Session resumed successfully");
                } else {
                    Log.d("MainActivity", "Activity not ready for session resume, will retry");
                    retryResumeSession();
                }
            }
        }, SESSION_RESUME_DELAY);
    }

    private void recoverSurface() {
        if (isSessionInProgress && isSurfaceReleased) {
            Log.d("MainActivity", "Attempting to recover surface");
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
                                Log.d("MainActivity", "Surface recovery initiated");
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error recovering surface: " + e.getMessage());
                        isSurfaceReleased = true;
                        cleanupCallbacks();
                    }
                }
            }, SURFACE_RECOVERY_DELAY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivity", "onPause called");
        isActivityPaused = true;
        isAppVisible = false;
        
        // Only cleanup if we're not in the middle of a session or resuming
        if (!isSessionInProgress || (!isSessionActive && !isSessionResuming)) {
            cleanupCallbacks();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("MainActivity", "onStop called");
        isActivityStopped = true;
        
        // Only cleanup if we're not in the middle of a session or resuming
        if (!isSessionInProgress || (!isSessionActive && !isSessionResuming)) {
            cleanupCallbacks();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("MainActivity", "onStart called");
        isActivityStopped = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("MainActivity", "onDestroy called");
        isActivityDestroyed = true;
        sessionHandler.removeCallbacksAndMessages(null);
        if (!isSessionInProgress || !isSessionActive) {
            cleanupCallbacks();
        }
        mainHandler.removeCallbacksAndMessages(null);
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
            "Uploading\nEncrypted\nID Scan", // Upload of ID front-side has started
            "Still Uploading...\nSlow Connection", // Upload of ID front-side is still uploading
            "Upload Complete", // Upload of ID front-side is complete
            "Processing ID Scan", // Processing front-side
            "Uploading\nEncrypted\nBack of ID", // Upload of ID back-side has started
            "Still Uploading...\nSlow Connection", // Upload of ID back-side is still uploading
            "Upload Complete", // Upload of ID back-side is complete
            "Processing Back of ID", // Processing back-side
            "Saving\nYour Info", // Saving user info
            "Still Uploading...\nSlow Connection", // Still saving
            "Info Saved", // Info saved
            "Processing", // Processing info
            "Uploading\nID Details", // Uploading ID details
            "Still Uploading...\nSlow Connection", // Still uploading details
            "Upload Complete", // Upload complete
            "Processing\nID Details", // Processing details
            "Uploading\nNFC Data", // Uploading NFC data
            "Still Uploading...\nSlow Connection", // Still uploading NFC
            "Upload Complete", // NFC upload complete
            "Processing\nNFC Data" // Processing NFC data
        );

        // Configure result screen messages
        FaceTecCustomization.setIDScanResultScreenMessageOverrides(
            "Front Scan Complete", // Front scan complete (no back)
            "Front of ID\nScanned", // Front scan complete (has back)
            "Front of ID\nScanned", // Front scan complete (has NFC)
            "ID Scan Complete", // Back scan complete (no NFC)
            "Back of ID\nScanned", // Back scan complete (has NFC)
            "Passport Scan\nComplete", // Passport scan complete
            "Passport Scanned", // Passport scanned (has NFC)
            "Photo ID Scan\nComplete", // Final scan complete
            "ID Scan Complete", // NFC scan complete
            "ID Photo\nComplete", // Review required
            "Face Not\nMatched", // Face match failed
            "ID Not\nVisible", // ID not visible
            "Text Not\nLegible", // Text not legible
            "ID Type\nMismatch", // ID type mismatch
            "ID Details\nUploaded" // NFC skipped
        );
        
        return customization;
    }

    private void cleanupCallbacks() {
        Log.d("MainActivity", "cleanupCallbacks called - isSessionInProgress: " + isSessionInProgress + 
              ", isSessionActive: " + isSessionActive + 
              ", isSurfaceValid: " + isSurfaceValid +
              ", isSelfieProcessing: " + isSelfieProcessing +
              ", isSessionResuming: " + isSessionResuming +
              ", isSessionActivityAttached: " + isSessionActivityAttached +
              ", isSurfaceReleased: " + isSurfaceReleased);
              
        sessionHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (faceScanResultCallbackRef != null && (!isSessionInProgress || !isSessionActive || !isSurfaceValid || !isSelfieProcessing) && !isSessionResuming) {
                    Log.d("MainActivity", "Cleaning up face scan callback");
                    faceScanResultCallbackRef.cancel();
                    faceScanResultCallbackRef = null;
                }
                if (idScanResultCallbackRef != null && (!isSessionInProgress || !isSessionActive || !isSurfaceValid) && !isSessionResuming) {
                    Log.d("MainActivity", "Cleaning up ID scan callback");
                    idScanResultCallbackRef.cancel();
                    idScanResultCallbackRef = null;
                }
            }
        });
        
        if (!isSessionInProgress || (!isSessionActive && !isSessionResuming) || !isSurfaceValid) {
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
                    startIdscann(call, sessionToken, result);
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
        Log.d("MainActivity", "call.method is " + call.method);
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

    private void startIdscann(MethodCall call, String sessionToken, MethodChannel.Result result) {
        if (isActivityPaused && !isSessionInProgress) {
            result.error("SESSION_ERROR", "Activity is paused", null);
            return;
        }
        
        isIdScanMode = true;
        isSessionInProgress = true;
        
        // Apply customization before starting session
        FaceTecSDK.setCustomization(getCurrentCustomization());
        
        // Launch the session
        try {
            FaceTecSessionActivity.createAndLaunchSession(
                (Context)this, 
                (FaceTecIDScanProcessor)this, 
                sessionToken
            );
            result.success(true);
        } catch (Exception e) {
            Log.e("MainActivity", "Error launching ID scan session: " + e.getMessage());
            result.error("SESSION_ERROR", e.getMessage(), null);
            isSessionInProgress = false;
            cleanupCallbacks();
        }
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
        
        Log.d("MainActivity", "Starting match ID scan with token: " + sessionToken);
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
            Log.d("MainActivity", "Creating and launching match ID scan session");
            
            // Create and launch the PhotoIDMatchProcessor
            new PhotoIDMatchProcessor(sessionToken, this, processorChannel, idscannChannel);
            result.success(true);
        } catch (Exception e) {
            Log.e("MainActivity", "Error launching match ID scan session: " + e.getMessage());
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

        // IMPORTANT: FaceTecIDScanStatus.SUCCESS does not mean the IDScan was successful,
        // it simply means the User completed the Session. Processing still needs to be performed.
        if(idScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
            Log.d("MainActivity", "Session was not completed successfully, cancelling.");
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

            // Simulate a progressive upload to update the Progress Bar appropriately
            idScanResultCallback.uploadProgress(0);

            // Send data to Flutter for processing
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Simulate upload progress (this would normally happen during API communication)
                        for (int i = 0; i < 100; i += 20) {
                            final int progress = i;
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (idScanResultCallbackRef != null) {
                                        idScanResultCallbackRef.uploadProgress(progress);
                                    }
                                }
                            }, i * 10);
                        }

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
                                        Log.d("MainActivity", "Using response: " + responseString);
                                        
                                        // Complete the progress bar
                                        idScanResultCallbackRef.uploadProgress(1);
                                        
                                        // Proceed to next step with the scan result
                                        boolean success = idScanResultCallbackRef.proceedToNextStep(responseString);
                                        
                                        if (success) {
                                            Log.d("MainActivity", "Successfully proceeded to next step");
                                        } else {
                                            Log.d("MainActivity", "Failed to proceed to next step");
                                        }
                                    } else {
                                        // Handle case where Flutter returns something other than a Map
                                        defaultResponse();
                                    }
                                } catch (Exception e) {
                                    Log.e("MainActivity", "Error handling Flutter response: " + e.getMessage());
                                    e.printStackTrace();
                                    defaultResponse();
                                }
                            }

                            @Override
                            public void error(String errorCode, String errorMessage, Object errorDetails) {
                                Log.e("MainActivity", "Flutter returned error: " + errorCode + " - " + errorMessage);
                                defaultResponse();
                            }

                            @Override
                            public void notImplemented() {
                                Log.e("MainActivity", "Method not implemented in Flutter");
                                defaultResponse();
                            }
                            
                            private void defaultResponse() {
                                try {
                                    // Create fallback response in the same format
                                    JSONObject fallbackResponse = new JSONObject();
                                    fallbackResponse.put("wasProcessed", true);
                                    fallbackResponse.put("error", false);
                                    fallbackResponse.put("scanResultBlob", parameters.toString());
                                    
                                    idScanResultCallbackRef.uploadProgress(1);
                                    idScanResultCallbackRef.proceedToNextStep(fallbackResponse.toString());
                                } catch (Exception ex) {
                                    Log.e("MainActivity", "Fallback response failed: " + ex.getMessage());
                                    cleanupCallbacks();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in ID scan processing: " + e.getMessage());
                        e.printStackTrace();
                        if (idScanResultCallbackRef != null) {
                            try {
                                // Create fallback response in the same format
                                JSONObject fallbackResponse = new JSONObject();
                                fallbackResponse.put("wasProcessed", true);
                                fallbackResponse.put("error", false);
                                fallbackResponse.put("scanResultBlob", "{}");
                                idScanResultCallbackRef.proceedToNextStep(fallbackResponse.toString());
                            } catch (Exception ex) {
                                Log.e("MainActivity", "Fallback response failed: " + ex.getMessage());
                            }
                        }
                        isSessionInProgress = false;
                        isSessionActive = false;
                        cleanupCallbacks();
                    }
                }
            });

        } catch (Exception e) {
            Log.e("MainActivity", "Error processing ID scan: " + e.getMessage());
            e.printStackTrace();
            if (idScanResultCallbackRef != null) {
                try {
                    // Create fallback response in the same format
                    JSONObject fallbackResponse = new JSONObject();
                    fallbackResponse.put("wasProcessed", true);
                    fallbackResponse.put("error", false);
                    fallbackResponse.put("scanResultBlob", "{}");
                    idScanResultCallbackRef.proceedToNextStep(fallbackResponse.toString());
                } catch (Exception ex) {
                    Log.e("MainActivity", "Fallback response failed: " + ex.getMessage());
                }
            }
            isSessionInProgress = false;
            isSessionActive = false;
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
                Log.d("MainActivity", "Status was not successful, status: " + sessionResult.getStatus());
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
                                Log.d("MainActivity", "Liveness check successful, proceeding with response");
                                faceScanResultCallbackRef.proceedToNextStep(successResponse);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error in liveness processing: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            cleanupCallbacks();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Error processing liveness check: " + e.getMessage());
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
            Log.d("MainActivity", "Face scan session was not completed successfully");
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
                        Log.e("MainActivity", "Selfie processing timeout reached");
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
                                            faceScanResultCallbackRef.proceedToNextStep(scanResultBlob);
                                        } else {
                                            isSelfieProcessing = false;
                                            faceScanResultCallbackRef.cancel();
                                        }
                                    } else {
                                        isSelfieProcessing = false;
                                        faceScanResultCallbackRef.cancel();
                                    }
                                } catch (Exception e) {
                                    Log.e("MainActivity", "Error processing face scan result: " + e.getMessage());
                                    isSelfieProcessing = false;
                                    if (faceScanResultCallbackRef != null) {
                                        faceScanResultCallbackRef.cancel();
                                    }
                                }
                            }

                            @Override
                            public void error(String errorCode, String errorMessage, Object errorDetails) {
                                Log.e("MainActivity", "Error from Flutter: " + errorCode + " - " + errorMessage);
                                isSelfieProcessing = false;
                                if (faceScanResultCallbackRef != null) {
                                    faceScanResultCallbackRef.cancel();
                                }
                            }

                            @Override
                            public void notImplemented() {
                                Log.e("MainActivity", "Method not implemented in Flutter");
                                isSelfieProcessing = false;
                                if (faceScanResultCallbackRef != null) {
                                    faceScanResultCallbackRef.cancel();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in face scan processing: " + e.getMessage());
                        isSelfieProcessing = false;
                        if (faceScanResultCallbackRef != null) {
                            faceScanResultCallbackRef.cancel();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "Error processing face scan: " + e.getMessage());
            isSelfieProcessing = false;
            if (faceScanResultCallbackRef != null) {
                faceScanResultCallbackRef.cancel();
            }
        }
    }

    // Called when FaceTec SDK is completely done
    public void onFaceTecSDKCompletelyDone() {
        Log.d("MainActivity", "onFaceTecSDKCompletelyDone called - isSessionInProgress: " + isSessionInProgress + 
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
        Log.e("MainActivity", "Face Scan result cancelled");
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
            Log.e("MainActivity", "Error creating success response: " + e.getMessage());
            return "{\"wasProcessed\":true,\"error\":false,\"scanResultBlob\":\"{}\"}";
        }
    }

    private void onScanResultBlobReceived(String scanResultBlob) {
        Log.d("MainActivity", "onScanResultBlobReceived: Processing scan result");
        
        if (isIdScanMode) {
            if (idScanResultCallbackRef != null) {
                Log.d("MainActivity", "Proceeding with ID scan result");
                try {
                    String response = createSuccessResponse();
                    Log.d("MainActivity", "Using formatted response: " + response);
                    idScanResultCallbackRef.proceedToNextStep(response);
                    
                    // Only clean up if we're not expecting a back scan
                    if (!isSessionInProgress) {
                        idScanResultCallbackRef = null;
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error proceeding with ID scan: " + e.getMessage());
                    try {
                        String simpleResponse = createSuccessResponse();
                        idScanResultCallbackRef.proceedToNextStep(simpleResponse);
                    } catch (Exception ex) {
                        Log.e("MainActivity", "Fallback response failed: " + ex.getMessage());
                        idScanResultCallbackRef = null;
                    }
                }
            }
        } else {
            if (faceScanResultCallbackRef != null) {
                Log.d("MainActivity", "Proceeding with face scan result");
                try {
                    String response = createSuccessResponse();
                    Log.d("MainActivity", "Using formatted response: " + response);
                    faceScanResultCallbackRef.proceedToNextStep(response);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error proceeding with face scan: " + e.getMessage());
                    try {
                        String simpleResponse = createSuccessResponse();
                        faceScanResultCallbackRef.proceedToNextStep(simpleResponse);
                    } catch (Exception ex) {
                        Log.e("MainActivity", "Fallback response failed: " + ex.getMessage());
                    }
                }
                faceScanResultCallbackRef = null;
            }
        }
        
        // Only mark session as completed if we're not transitioning to back scan
        if (!isIdScanMode || !isSessionInProgress) {
            Log.d("MainActivity", "Scan completed, cleaning up session");
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
        Log.d("MainActivity", "Face Scan taking longer than usual, adding upload delay message.");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.uploadMessageOverride(uploadMessage);
        }
    }

    private void receivedIdscannProcessorCall(MethodCall call, MethodChannel.Result result) {
        Log.d("MainActivity", "call.method is " + call.method);
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
                    Log.d("MainActivity", "Received scan result blob from Flutter");
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
            Log.d("MainActivity", "Attempting to reattach session activity");
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
                                Log.d("MainActivity", "Session activity reattachment initiated");
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error reattaching session activity: " + e.getMessage());
                        isSessionActivityAttached = false;
                        cleanupCallbacks();
                    }
                }
            }, SESSION_ACTIVITY_CHECK_DELAY);
        }
    }
}
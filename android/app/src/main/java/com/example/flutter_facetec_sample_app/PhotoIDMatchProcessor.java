//
// Welcome to the annotated FaceTec Device SDK core code for performing secure Enrollment!
//
package com.example.flutter_facetec_sample_app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;
import com.facetec.sdk.FaceTecSessionActivity;
import com.facetec.sdk.FaceTecSDK;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;

// This is an example self-contained class to perform Enrollment with the FaceTec SDK.
// You may choose to further componentize parts of this in your own Apps based on your specific requirements.

// Android Note 1:  Some commented "Parts" below are out of order so that they can match iOS and Browser source for this same file on those platforms.
// Android Note 2:  Android does not have a onFaceTecSDKCompletelyDone function that you must implement like "Part 10" of iOS and Android Samples.  Instead, onActivityResult is used as the place in code you get control back from the FaceTec SDK.
public class PhotoIDMatchProcessor implements FaceTecFaceScanProcessor, Processor {
    private static final String TAG = "PhotoIDMatchProcessor";
    private boolean success = false;
    private final MainActivity mainActivity;
    private final MethodChannel processorChannel;
    private String sessionToken;

    public PhotoIDMatchProcessor(String sessionToken, Context context, MethodChannel processorChannel) {
        this.mainActivity = (MainActivity) context;
        this.processorChannel = processorChannel;
        this.sessionToken = sessionToken;

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

        // Start the session
        startSession();
    }

    @Override
    public void processSessionWhileFaceTecSDKWaits(final FaceTecSessionResult sessionResult, final FaceTecFaceScanResultCallback faceScanResultCallback) {
        if (sessionResult == null) {
            Log.d(TAG, "Session was cancelled or failed");
            faceScanResultCallback.cancel();
            return;
        }

        // Set the latest session result
        mainActivity.setLatestSessionResult(sessionResult);

        if (sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d(TAG, "Session was not completed successfully");
            faceScanResultCallback.cancel();
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
            processorChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                @Override
                public void success(Object result) {
                    try {
                        if (result instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) result;
                            
                            // Create response similar to the server response
                            JSONObject responseJSON = new JSONObject();
                            responseJSON.put("wasProcessed", true);
                            responseJSON.put("error", false);
                            
                            // If Flutter sent back a scanResultBlob, use it
                            if (resultMap.containsKey("scanResultBlob")) {
                                responseJSON.put("scanResultBlob", resultMap.get("scanResultBlob"));
                            } else {
                                // Otherwise create a default response
                                responseJSON.put("scanResultBlob", "{}");
                            }
                            
                            String responseString = responseJSON.toString();
                            Log.d(TAG, "Using response: " + responseString);
                            
                            // Set success message
                            FaceTecCustomization.overrideResultScreenSuccessMessage = "Face Scanned\n3D Liveness Proven";
                            
                            // Proceed to next step
                            success = faceScanResultCallback.proceedToNextStep(responseString);
                        } else {
                            Log.e(TAG, "Invalid response format from Flutter");
                            faceScanResultCallback.cancel();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling Flutter response: " + e.getMessage());
                        e.printStackTrace();
                        faceScanResultCallback.cancel();
                    }
                }

                @Override
                public void error(String errorCode, String errorMessage, Object errorDetails) {
                    Log.e(TAG, "Flutter returned error: " + errorCode + " - " + errorMessage);
                    faceScanResultCallback.cancel();
                }

                @Override
                public void notImplemented() {
                    Log.e(TAG, "Method not implemented in Flutter");
                    faceScanResultCallback.cancel();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing session result: " + e.getMessage());
            e.printStackTrace();
            faceScanResultCallback.cancel();
        }
    }

    public void startSession() {
        try {
            // Start the session
            FaceTecSessionActivity.createAndLaunchSession(mainActivity, this, sessionToken);
        } catch (Exception e) {
            Log.e(TAG, "Error starting session: " + e.getMessage());
            e.printStackTrace();
            onSessionError();
        }
    }

    private void onSessionError() {
        success = false;
        if (mainActivity != null) {
            mainActivity.onFaceTecSDKCompletelyDone();
        }
    }

    public boolean isSuccess() {
        return this.success;
    }
}
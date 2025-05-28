package com.example.flutter_facetec_sample_app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecIDScanProcessor;
import com.facetec.sdk.FaceTecIDScanResult;
import com.facetec.sdk.FaceTecIDScanResultCallback;
import com.facetec.sdk.FaceTecIDScanStatus;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;

public class PhotoIDScanProcessor implements FaceTecIDScanProcessor, Processor {
    private static final String TAG = "PhotoIDScanProcessor";
    private boolean success = false;
    private final MainActivity mainActivity;
    private final MethodChannel idscannChannel;

    public PhotoIDScanProcessor(String sessionToken, Context context, MethodChannel idscannChannel) {
        this.mainActivity = (MainActivity) context;
        this.idscannChannel = idscannChannel;

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
        FaceTecSessionActivity.createAndLaunchSession(context, this, sessionToken);
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(final FaceTecIDScanResult idScanResult, final FaceTecIDScanResultCallback idScanResultCallback) {
        mainActivity.setLatestIDScanResult(idScanResult);

        if (idScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
            Log.d(TAG, "Session was not completed successfully, cancelling.");
            idScanResultCallback.cancel();
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

            // Send data to Flutter for processing
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        idscannChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
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
                                            // Otherwise use our parameters as the scanResultBlob
                                            responseJSON.put("scanResultBlob", parameters.toString());
                                        }
                                        
                                        String responseString = responseJSON.toString();
                                        Log.d(TAG, "Using response: " + responseString);
                                        
                                        // Complete the progress bar
                                        idScanResultCallback.uploadProgress(1);
                                        
                                        // Proceed to next step with the scan result
                                        success = idScanResultCallback.proceedToNextStep(responseString);
                                        
                                        if (!success) {
                                            Log.d(TAG, "Failed to proceed to next step");
                                            idScanResultCallback.cancel();
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
                                    
                                    idScanResultCallback.uploadProgress(1);
                                    idScanResultCallback.proceedToNextStep(fallbackResponse.toString());
                                } catch (Exception ex) {
                                    Log.e(TAG, "Fallback response failed: " + ex.getMessage());
                                    idScanResultCallback.cancel();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in ID scan processing: " + e.getMessage());
                        e.printStackTrace();
                        idScanResultCallback.cancel();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing ID scan: " + e.getMessage());
            e.printStackTrace();
            idScanResultCallback.cancel();
        }
    }

    public boolean isSuccess() {
        return this.success;
    }
} 
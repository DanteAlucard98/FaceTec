package com.example.flutter_facetec_sample_app;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import java.util.HashMap;
import java.util.Map;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class LivenessCheckProcessor implements FaceTecFaceScanProcessor, MethodChannel.MethodCallHandler {
    private static final String TAG = "LivenessCheckProcessor";
    private MethodChannel processorChannel;
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private Context context;

    public LivenessCheckProcessor(MethodChannel processorChannel, Context context) {
        this.processorChannel = processorChannel;
        this.context = context.getApplicationContext();
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        switch (call.method) {
            case "cancelFaceScan":
                cancelFaceScan();
                result.success(null);
                break;
            case "onScanResultBlobReceived":
                if (call.hasArgument("scanResultBlob")) {
                    String scanResultBlob = call.argument("scanResultBlob");
                    onScanResultBlobReceived(scanResultBlob);
                    result.success(null);
                } else {
                    result.error("InvalidArguments", "Missing scanResultBlob", null);
                }
                break;
            case "onScanResultUploadDelay":
                if (call.hasArgument("uploadMessage")) {
                    String uploadMessage = call.argument("uploadMessage");
                    onScanResultUploadDelay(uploadMessage);
                    result.success(null);
                } else {
                    result.error("InvalidArguments", "Missing uploadMessage", null);
                }
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public void processSessionWhileFaceTecSDKWaits(FaceTecSessionResult faceTecSessionResult, FaceTecFaceScanResultCallback faceTecFaceScanResultCallback) {
        faceScanResultCallbackRef = faceTecFaceScanResultCallback;

        if (faceTecSessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d(TAG, "Status was not successful, canceling...");
            cancelFaceScan();
            return;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("status", "sessionCompletedSuccessfully");
        args.put("lowQualityAuditTrailCompressedBase64",
                faceTecSessionResult.getLowQualityAuditTrailCompressedBase64()[0]);
        args.put("auditTrailCompressedBase64",
                faceTecSessionResult.getAuditTrailCompressedBase64()[0]);
        args.put("faceScanBase64", faceTecSessionResult.getFaceScanBase64());
        args.put("sessionId", faceTecSessionResult.getSessionId());

        processorChannel.invokeMethod("processSession", args);
    }

    public void onFaceTecSDKCompletelyDone() {
        Log.d(TAG, "onFaceTecSDKCompletelyDone");
    }

    private void cancelFaceScan() {
        Log.e(TAG, "Face Scan result cancelled");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.cancel();
        }
        faceScanResultCallbackRef = null;
    }

    private void onScanResultBlobReceived(String scanResultBlob) {
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.proceedToNextStep(scanResultBlob);
        }
        faceScanResultCallbackRef = null;
    }

    private void onScanResultUploadDelay(String uploadMessage) {
        Log.d(TAG, "Face Scan taking longer than usual, adding upload delay message.");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.uploadMessageOverride(uploadMessage);
        }
    }
} 
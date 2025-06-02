package com.example.flutter_facetec_sample_app;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;
import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKStatus;
import com.facetec.sdk.FaceTecSessionActivity;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import java.util.HashMap;
import java.util.Map;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity implements FaceTecFaceScanProcessor {
    // MainActivity acts as a makeshift processor for the FaceTec session, since the actual
    // processing code in Dart cannot directly be called. It implements the required methods of a
    // processor delegate, processSessionWhileFaceTecSDKWaits() and onFaceTecSDKCompletelyDone().
    private static final String CHANNEL = "com.facetec.sdk";
    private static final String PROCESSOR_CHANNEL = "com.facetec.sdk/livenesscheck";
    private static final String PROCESSOR_CHANNEL_PHOTO_ID_MATCH = "com.facetec.sdk/photo_id_match";

    private MethodChannel processorChannel;
    private MethodChannel photoIDMatchChannel;
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private PhotoIDMatchProcessor photoIDMatchProcessor;
    

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        // configureFlutterEngine() creates processor channels for commmunicating with main.dart and LivenessCheckProcessor.dart.
        // Other processors you may create will be instantiated through another method channel.
        MethodChannel SDKChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL);
        processorChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), PROCESSOR_CHANNEL);
        photoIDMatchChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), PROCESSOR_CHANNEL_PHOTO_ID_MATCH);

        SDKChannel.setMethodCallHandler(this::receivedFaceTecSDKMethodCall);
        processorChannel.setMethodCallHandler(this::receivedLivenessCheckProcessorCall);
        
        // Initialize PhotoIDMatchProcessor with the correct constructor
        photoIDMatchProcessor = new PhotoIDMatchProcessor(photoIDMatchChannel, this);
        photoIDMatchChannel.setMethodCallHandler(photoIDMatchProcessor);
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
            case "startPhotoIDMatch":
                if (call.hasArgument("sessionToken")) {
                    String sessionToken = call.argument("sessionToken");
                    photoIDMatchProcessor.startPhotoIDMatchCheck(sessionToken, result);
                }
                else {
                    result.error("InvalidArguments", "Missing sessionToken", null);
                }
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
        final Context context = this;

        // Configurar personalización del SDK
        FaceTecCustomization ftCustomization = new FaceTecCustomization();
        
        // Configurar la imagen de marca
        ftCustomization.getOverlayCustomization().brandingImage = R.drawable.flutter_logo;
        
        // Configurar el fondo
        ftCustomization.getOverlayCustomization().backgroundColor = android.graphics.Color.WHITE;
        
        // Configurar mensajes personalizados para Photo ID Match
        FaceTecCustomization.setIDScanUploadMessageOverrides(
            "Subiendo\nDocumento\nEncriptado",
            "Seguimos Subiendo...\nConexión Lenta",
            "Subida Completada",
            "Procesando Documento",
            "Subiendo\nSelfie\nEncriptada",
            "Seguimos Subiendo...\nConexión Lenta",
            "Subida Completada",
            "Procesando\nSelfie",
            "Comparando\nDocumento con Selfie",
            "Procesando...\nPor Favor Espere",
            "Comparación Completada",
            "", "", "", "", "", "", "", "", ""
        );
        
        // Aplicar la configuración
        FaceTecSDK.setCustomization(ftCustomization);

        // Inicializar el SDK
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
        // startLivenessCheck() will open the FaceTec interface and start the liveness check process. The
        // method processSessionWhileFaceTecSDKWaits() and onFaceTecSDKCompletelyDone() are not explicitly called,
        // but are implicitly called through the FaceTec controller. If you want to implement multiple processors,
        // you would need to keep track of which method was called to instantiate the session (in this case, startLivenessCheck)
        // and having branching logic in the implicitly called methods to handle multiple processors.
        FaceTecSessionActivity.createAndLaunchSession(this, this, sessionToken);
        result.success(true);
    }

    // FaceTecFaceScanProcessor interface methods
    // These methods are required by the FaceTec SDK 9.7.69
    // Note: @Override annotations removed due to interface compatibility issues
    public void processSessionWhileFaceTecSDKWaits(FaceTecSessionResult faceTecSessionResult, FaceTecFaceScanResultCallback faceTecFaceScanResultCallback) {
        faceScanResultCallbackRef = faceTecFaceScanResultCallback;

        if (faceTecSessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d("MainActivity", "Status was not successful, canceling...");
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
        args.put("ftUserAgentString", FaceTecSDK.createFaceTecAPIUserAgentString(faceTecSessionResult.getSessionId()));

        processorChannel.invokeMethod("processSession", args);
    }

    public void onFaceTecSDKCompletelyDone() {
        Log.d("MainActivity", "onFaceTecSDKCompletelyDone");
    }

    private void cancelFaceScan() {
        Log.e("MainActivity", "Face Scan result cancelled");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.cancel();
        }
        faceScanResultCallbackRef = null;
    }

    private void onScanResultBlobReceived(String scanResultBlob) {
        // Handle a successfully received scanResultBlob from the FaceTec API
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.proceedToNextStep(scanResultBlob);
        }
        faceScanResultCallbackRef = null;
    }

    private void onScanResultUploadDelay(String uploadMessage) {
        // Handle if there is a long delay in uploading the face scan to the server
        Log.d("MainActivity", "Face Scan taking longer than usual, adding upload delay message.");
        if (faceScanResultCallbackRef != null) {
            faceScanResultCallbackRef.uploadMessageOverride(uploadMessage);
        }
    }
}
package com.example.flutter_facetec_sample_app;

import android.content.Context;
import androidx.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionActivity;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;
import com.facetec.sdk.FaceTecIDScanProcessor;
import com.facetec.sdk.FaceTecIDScanResult;
import com.facetec.sdk.FaceTecIDScanResultCallback;
import com.facetec.sdk.FaceTecIDScanStatus;

import java.util.HashMap;
import java.util.Map;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import org.json.JSONObject;

public class PhotoIDMatchProcessor implements FaceTecFaceScanProcessor, FaceTecIDScanProcessor {
    private static final String TAG = "PhotoIDMatchProcessor";
    private static final String PROCESSOR_CHANNEL = "com.facetec.sdk/photo_id_match";
    private MethodChannel processorChannel;
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private FaceTecIDScanResultCallback idScanResultCallbackRef;
    private Context context;
    private boolean isProcessingPhotoID = false;
    private boolean isProcessingDocument = false;
    private static final int FLUTTER_TIMEOUT_MS = 10000; // 10 seconds timeout
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private String currentSessionId = null;

    public PhotoIDMatchProcessor(MethodChannel processorChannel, Context context) {
        this.processorChannel = processorChannel;
        this.context = context;
    }

    public void startPhotoIDMatchCheck(String sessionToken, MethodChannel.Result result) {
        try {
            Log.d(TAG, "Starting Photo ID Match process");
            
            // Configurar mensajes personalizados
            configureCustomMessages();
            
            // Iniciar el proceso
            isProcessingPhotoID = true;
            
            // Lanzar la sesión
            FaceTecSessionActivity.createAndLaunchSession(context, (FaceTecFaceScanProcessor)this, sessionToken);
            result.success(true);
        } catch (Exception e) {
            Log.e(TAG, "Error starting Photo ID Match: " + e.getMessage());
            result.error("START_ERROR", e.getMessage(), null);
        }
    }

    private void configureCustomMessages() {
        try {
            FaceTecCustomization.setIDScanUploadMessageOverrides(
                "Uploading\nEncrypted\nPhoto ID",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing Photo ID",
                "Uploading\nEncrypted\nSelfie",
                "Still Uploading...\nSlow Connection",
                "Upload Complete",
                "Processing\nSelfie",
                "Comparing\nPhoto ID to Selfie",
                "Still Processing...\nPlease Wait",
                "Match Complete",
                "", "", "", "", "", "", "", "", ""
            );
        } catch (Exception e) {
            Log.e(TAG, "Error configuring messages: " + e.getMessage());
        }
    }

    public void receivedPhotoIDMatchProcessorCall(MethodCall call, MethodChannel.Result result) {
        Log.d(TAG, "=== START receivedPhotoIDMatchProcessorCall ===");
        Log.d(TAG, "Received call: " + call.method);
        try {
            switch (call.method) {
                case "cancelPhotoIDMatch":
                    Log.d(TAG, "Handling cancelPhotoIDMatch call");
                    cancelPhotoIDMatch();
                    result.success(null);
                    break;
                case "releaseCamera":
                    Log.d(TAG, "Handling releaseCamera call");
                    releaseCamera();
                    result.success(null);
                    break;
                case "onPhotoIDMatchResultBlobReceived":
                    Log.d(TAG, "Handling onPhotoIDMatchResultBlobReceived call");
                    if (call.hasArgument("photoIDMatchResultBlob")) {
                        String photoIDMatchResultBlob = call.argument("photoIDMatchResultBlob");
                        onPhotoIDMatchResultBlobReceived(photoIDMatchResultBlob);
                        result.success(null);
                    } else {
                        Log.e(TAG, "Missing photoIDMatchResultBlob argument");
                        result.error("INVALID_ARGUMENTS", "Missing photoIDMatchResultBlob", null);
                    }
                    break;
                case "onPhotoIDMatchResultUploadDelay":
                    Log.d(TAG, "Handling onPhotoIDMatchResultUploadDelay call");
                    if (call.hasArgument("uploadMessage")) {
                        String uploadMessage = call.argument("uploadMessage");
                        onPhotoIDMatchResultUploadDelay(uploadMessage);
                        result.success(null);
                    } else {
                        Log.e(TAG, "Missing uploadMessage argument");
                        result.error("INVALID_ARGUMENTS", "Missing uploadMessage", null);
                    }
                    break;
                case "startDocumentScan":
                    Log.d(TAG, "Handling startDocumentScan call");
                    if (call.hasArgument("sessionToken")) {
                        String sessionToken = call.argument("sessionToken");
                        startDocumentScan(sessionToken);
                        result.success(null);
                    } else {
                        Log.e(TAG, "Missing sessionToken argument");
                        result.error("INVALID_ARGUMENTS", "Missing sessionToken", null);
                    }
                    break;
                case "configureDocumentScanMessages":
                    Log.d(TAG, "Handling configureDocumentScanMessages call - Ignoring as messages are configured in Java");
                    // Ignorar esta llamada ya que los mensajes se configuran en Java
                    result.success(null);
                    break;
                default:
                    Log.e(TAG, "Method not implemented: " + call.method);
                    result.notImplemented();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing call: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            result.error("PROCESSING_ERROR", e.getMessage(), null);
        }
        Log.d(TAG, "=== END receivedPhotoIDMatchProcessorCall ===");
    }

    private void startDocumentScan(String sessionToken) {
        try {
            Log.d(TAG, "=== START startDocumentScan ===");
            Log.d(TAG, "Session Token: " + sessionToken);
            Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
            
            if (sessionToken == null || sessionToken.isEmpty()) {
                Log.e(TAG, "Invalid session token");
                cancelPhotoIDMatch();
                return;
            }

            // Asegurarse de que el procesador de ID esté listo
            if (!(this instanceof FaceTecIDScanProcessor)) {
                Log.e(TAG, "This processor does not implement FaceTecIDScanProcessor");
                cancelPhotoIDMatch();
                return;
            }
            
            isProcessingDocument = true;
            Log.d(TAG, "Set isProcessingDocument to true");
            
            // Configurar mensajes directamente en Java
            Log.d(TAG, "Configuring ID scan upload messages");
            try {
                // Configurar mensajes personalizados para la INE
                FaceTecCustomization.setIDScanUploadMessageOverrides(
                    "Uploading\nEncrypted\nPhoto ID",
                    "Still Uploading...\nSlow Connection",
                    "Upload Complete",
                    "Processing Photo ID",
                    "Uploading\nEncrypted\nSelfie",
                    "Still Uploading...\nSlow Connection",
                    "Upload Complete",
                    "Processing\nSelfie",
                    "Comparing\nPhoto ID to Selfie",
                    "Still Processing...\nPlease Wait",
                    "Match Complete",
                    "", "", "", "", "", "", "", "", ""
                );
                Log.d(TAG, "ID scan upload messages configured successfully");

                // Configuración básica del SDK
                FaceTecCustomization customization = new FaceTecCustomization();
                customization.getOverlayCustomization().brandingImage = R.drawable.flutter_logo;
                
                // Configurar el SDK para usar el modo de escaneo de documento
                try {
                    // Configurar el SDK para usar el modo de escaneo de documento
                    FaceTecSDK.setCustomization(customization);
                    Log.d(TAG, "SDK configured for document scanning");
                } catch (Exception e) {
                    Log.e(TAG, "Error configuring SDK: " + e.getMessage());
                }
                
                Log.d(TAG, "Basic SDK configuration set with branding");
            } catch (Exception e) {
                Log.e(TAG, "Error configuring messages: " + e.getMessage());
                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                // Continuar con el proceso incluso si hay error en la configuración de mensajes
            }
            
            // Lanzar la sesión de escaneo de documento
            Log.d(TAG, "Creating and launching ID scan session");
            try {
                // Asegurarse de que el contexto esté disponible
                if (context == null) {
                    Log.e(TAG, "Context is null");
                    cancelPhotoIDMatch();
                    return;
                }

                // Verificar que el procesador esté correctamente inicializado
                if (idScanResultCallbackRef == null) {
                    Log.d(TAG, "Initializing idScanResultCallbackRef");
                    idScanResultCallbackRef = new FaceTecIDScanResultCallback() {
                        @Override
                        public boolean proceedToNextStep(String nextStep) {
                            try {
                                Log.d(TAG, "=== START proceedToNextStep ===");
                                Log.d(TAG, "Next step: " + nextStep);
                                Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                
                                // Verificar el estado actual
                                if (!isProcessingDocument) {
                                    Log.e(TAG, "Not processing document, returning false");
                                    return false;
                                }
                                
                                // Verificar el siguiente paso
                                if (nextStep == null || nextStep.isEmpty()) {
                                    Log.e(TAG, "Next step is null or empty");
                                    return false;
                                }
                                
                                Log.d(TAG, "Proceeding to next step: " + nextStep);
                                Log.d(TAG, "=== END proceedToNextStep ===");
                                return true;
                            } catch (Exception e) {
                                Log.e(TAG, "Error in proceedToNextStep: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                return false;
                            }
                        }

                        @Override
                        public void cancel() {
                            try {
                                Log.d(TAG, "=== START cancel ===");
                                Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                Log.d(TAG, "Cancelling ID scan");
                                cancelPhotoIDMatch();
                                Log.d(TAG, "=== END cancel ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error in cancel: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                            }
                        }

                        @Override
                        public void uploadMessageOverride(String message) {
                            try {
                                Log.d(TAG, "=== START uploadMessageOverride ===");
                                Log.d(TAG, "Message: " + message);
                                Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                Log.d(TAG, "=== END uploadMessageOverride ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error in uploadMessageOverride: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                            }
                        }

                        @Override
                        public void uploadProgress(float progress) {
                            try {
                                Log.d(TAG, "=== START uploadProgress ===");
                                Log.d(TAG, "Progress: " + progress);
                                Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                Log.d(TAG, "=== END uploadProgress ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error in uploadProgress: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                            }
                        }
                    };
                }

                // Lanzar la sesión con el tipo correcto
                FaceTecSessionActivity.createAndLaunchSession(context, (FaceTecIDScanProcessor)this, sessionToken);
                Log.d(TAG, "ID scan session launched successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error launching ID scan session: " + e.getMessage());
                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                cancelPhotoIDMatch();
                return;
            }
            Log.d(TAG, "=== END startDocumentScan ===");
        } catch (Exception e) {
            Log.e(TAG, "Error starting document scan: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            cancelPhotoIDMatch();
        }
    }

    public void processSessionWhileFaceTecSDKWaits(FaceTecSessionResult faceTecSessionResult, FaceTecFaceScanResultCallback faceTecFaceScanResultCallback) {
        try {
            Log.d(TAG, "=== START processSessionWhileFaceTecSDKWaits ===");
            Log.d(TAG, "Session ID: " + faceTecSessionResult.getSessionId());
            Log.d(TAG, "Session Status: " + faceTecSessionResult.getStatus());
            Log.d(TAG, "FaceScan Base64 length: " + (faceTecSessionResult.getFaceScanBase64() != null ? faceTecSessionResult.getFaceScanBase64().length() : 0));
            
            // Guardar el ID de sesión actual
            currentSessionId = faceTecSessionResult.getSessionId();
            Log.d(TAG, "Stored current session ID: " + currentSessionId);
            
            faceScanResultCallbackRef = faceTecFaceScanResultCallback;
            Log.d(TAG, "Stored faceScanResultCallbackRef");

            // Verificar si hay problemas de conexión
            String statusString = faceTecSessionResult.getStatus().toString();
            if (statusString.contains("cancelled") || statusString.contains("network connection")) {
                String errorMessage = "Se requiere conexión a internet para continuar. Por favor, verifica tu conexión e intenta nuevamente.";
                Log.e(TAG, errorMessage);
                if (faceScanResultCallbackRef != null) {
                    faceScanResultCallbackRef.uploadMessageOverride(errorMessage);
                }
                cancelPhotoIDMatch();
                return;
            }

            if (faceTecSessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
                Log.e(TAG, "Session not successful, canceling");
                cancelPhotoIDMatch();
                return;
            }

            // Verificar si tenemos los datos necesarios
            if (faceTecSessionResult.getFaceScanBase64() == null || faceTecSessionResult.getFaceScanBase64().isEmpty()) {
                Log.e(TAG, "Face scan data is missing");
                cancelPhotoIDMatch();
                return;
            }

            Log.d(TAG, "Preparing to send session data to Flutter");
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", currentSessionId);
            args.put("faceScanBase64", faceTecSessionResult.getFaceScanBase64());
            
            // Agregar imágenes de auditoría si están disponibles
            String[] auditTrailImages = faceTecSessionResult.getAuditTrailCompressedBase64();
            String[] lowQualityAuditTrailImages = faceTecSessionResult.getLowQualityAuditTrailCompressedBase64();
            
            Log.d(TAG, "Audit Trail Images available: " + (auditTrailImages != null ? auditTrailImages.length : 0));
            Log.d(TAG, "Low Quality Audit Trail Images available: " + (lowQualityAuditTrailImages != null ? lowQualityAuditTrailImages.length : 0));
            
            if (auditTrailImages != null && auditTrailImages.length > 0) {
                args.put("auditTrailImage", auditTrailImages[0]);
                Log.d(TAG, "Added audit trail image to args");
            }
            if (lowQualityAuditTrailImages != null && lowQualityAuditTrailImages.length > 0) {
                args.put("lowQualityAuditTrailImage", lowQualityAuditTrailImages[0]);
                Log.d(TAG, "Added low quality audit trail image to args");
            }
            
            if (isProcessingPhotoID) {
                Log.d(TAG, "Adding Photo ID specific data");
                args.put("isPhotoID", Boolean.TRUE);
                args.put("sessionStatus", faceTecSessionResult.getStatus().toString());
                args.put("sessionSuccess", Boolean.TRUE);
            }

            // Cancelar cualquier timeout pendiente
            if (timeoutRunnable != null) {
                mainHandler.removeCallbacks(timeoutRunnable);
                Log.d(TAG, "Removed existing timeout");
            }

            // Configurar timeout
            timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Flutter communication timeout");
                    cancelPhotoIDMatch();
                }
            };
            mainHandler.postDelayed(timeoutRunnable, FLUTTER_TIMEOUT_MS);
            Log.d(TAG, "Set new timeout for " + FLUTTER_TIMEOUT_MS + "ms");

            Log.d(TAG, "Sending data to Flutter");
            mainHandler.post(() -> {
                try {
                    Log.d(TAG, "Invoking processSession method in Flutter");
                    processorChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                        @Override
                        public void success(Object result) {
                            Log.d(TAG, "Data sent successfully to Flutter");
                            if (timeoutRunnable != null) {
                                mainHandler.removeCallbacks(timeoutRunnable);
                                Log.d(TAG, "Removed timeout after successful data send");
                            }
                            // Proceder con el escaneo de documentos solo si la selfie fue exitosa
                            if (isProcessingPhotoID && result != null && result.toString().contains("success")) {
                                Log.d(TAG, "Starting document scan after successful photo ID match");
                                startDocumentScan(currentSessionId);
                            } else {
                                Log.e(TAG, "Not proceeding with document scan - selfie was not successful");
                                cancelPhotoIDMatch();
                            }
                        }

                        @Override
                        public void error(String errorCode, String errorMessage, Object errorDetails) {
                            Log.e(TAG, "Error sending data to Flutter: " + errorMessage);
                            Log.e(TAG, "Error code: " + errorCode);
                            if (errorDetails != null) {
                                Log.e(TAG, "Error details: " + errorDetails.toString());
                            }
                            if (timeoutRunnable != null) {
                                mainHandler.removeCallbacks(timeoutRunnable);
                                Log.d(TAG, "Removed timeout after error");
                            }
                            // No continuar con el escaneo de documentos si hay error
                            Log.e(TAG, "Not proceeding with document scan due to error");
                            cancelPhotoIDMatch();
                        }

                        @Override
                        public void notImplemented() {
                            Log.e(TAG, "Method not implemented in Flutter");
                            if (timeoutRunnable != null) {
                                mainHandler.removeCallbacks(timeoutRunnable);
                                Log.d(TAG, "Removed timeout after notImplemented");
                            }
                            // No continuar con el escaneo de documentos si el método no está implementado
                            Log.e(TAG, "Not proceeding with document scan - method not implemented");
                            cancelPhotoIDMatch();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception while sending data to Flutter: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    if (timeoutRunnable != null) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        Log.d(TAG, "Removed timeout after exception");
                    }
                    // No continuar con el escaneo de documentos si hay una excepción
                    Log.e(TAG, "Not proceeding with document scan due to exception");
                    cancelPhotoIDMatch();
                }
            });
            Log.d(TAG, "=== END processSessionWhileFaceTecSDKWaits ===");
        } catch (Exception e) {
            Log.e(TAG, "Error processing session: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            // No continuar con el escaneo de documentos si hay un error
            Log.e(TAG, "Not proceeding with document scan due to error");
            cancelPhotoIDMatch();
        }
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(FaceTecIDScanResult faceTecIDScanResult, FaceTecIDScanResultCallback faceTecIDScanResultCallback) {
        try {
            Log.d(TAG, "=== START processIDScanWhileFaceTecSDKWaits ===");
            Log.d(TAG, "FaceTecIDScanResult: " + (faceTecIDScanResult != null ? "not null" : "null"));
            Log.d(TAG, "Status: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getStatus() : "null"));
            Log.d(TAG, "Session ID: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getSessionId() : "null"));
            Log.d(TAG, "FaceTecIDScanResult class: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getClass().getName() : "null"));
            Log.d(TAG, "FaceTecIDScanResultCallback class: " + (faceTecIDScanResultCallback != null ? faceTecIDScanResultCallback.getClass().getName() : "null"));
            
            // Establecer el estado de procesamiento de documento
            isProcessingDocument = true;
            isProcessingPhotoID = false;
            
            Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
            Log.d(TAG, "Current session ID: " + currentSessionId);
            
            // Guardar la referencia al callback
            idScanResultCallbackRef = faceTecIDScanResultCallback;
            Log.d(TAG, "Stored idScanResultCallbackRef");

            // Verificar si tenemos los datos necesarios
            String idScanBase64 = faceTecIDScanResult != null ? faceTecIDScanResult.getIDScanBase64() : null;
            Log.d(TAG, "ID Scan Base64: " + (idScanBase64 != null ? "present" : "null"));
            if (idScanBase64 != null) {
                Log.d(TAG, "ID Scan Base64 length: " + idScanBase64.length());
                Log.d(TAG, "ID Scan Base64 first 100 chars: " + idScanBase64.substring(0, Math.min(100, idScanBase64.length())));
            }
            
            // Verificar el estado del escaneo
            if (faceTecIDScanResult == null || faceTecIDScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
                String errorMessage = "ID scan status is not success: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getStatus() : "null");
                Log.e(TAG, errorMessage);
                // Enviar error y terminar el proceso
                if (idScanResultCallbackRef != null) {
                    try {
                        // Crear un JSON válido con el scanResultBlob
                        JSONObject errorJson = new JSONObject();
                        errorJson.put("success", false);
                        errorJson.put("error", "ID_SCAN_FAILED");
                        errorJson.put("message", errorMessage);
                        errorJson.put("scanResultBlob", idScanBase64 != null ? idScanBase64 : ""); // Usar el scan base64 si está disponible
                        Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                        Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                        Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                        idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending error JSON: " + e.getMessage());
                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    }
                }
                // Esperar un momento antes de limpiar el estado
                mainHandler.postDelayed(() -> {
                    isProcessingDocument = false;
                    isProcessingPhotoID = false;
                }, 1000);
                return;
            }

            if (idScanBase64 == null || idScanBase64.isEmpty()) {
                String errorMessage = "ID scan data is missing";
                Log.e(TAG, errorMessage);
                // Enviar error y terminar el proceso
                if (idScanResultCallbackRef != null) {
                    try {
                        // Crear un JSON válido con el scanResultBlob
                        JSONObject errorJson = new JSONObject();
                        errorJson.put("success", false);
                        errorJson.put("error", "ID_SCAN_DATA_MISSING");
                        errorJson.put("message", errorMessage);
                        errorJson.put("scanResultBlob", ""); // Asegurar que scanResultBlob esté presente
                        Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                        Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                        idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending error JSON: " + e.getMessage());
                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    }
                }
                // Esperar un momento antes de limpiar el estado
                mainHandler.postDelayed(() -> {
                    isProcessingDocument = false;
                    isProcessingPhotoID = false;
                }, 1000);
                return;
            }

            Log.d(TAG, "Preparing to send ID scan data to Flutter");
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", currentSessionId != null ? currentSessionId : "unknown");
            args.put("idScanBase64", idScanBase64);
            args.put("sessionStatus", faceTecIDScanResult.getStatus() != null ? faceTecIDScanResult.getStatus().toString() : "UNKNOWN");
            args.put("sessionSuccess", Boolean.TRUE);
            args.put("endpoint", "/idscan-only");
            Log.d(TAG, "Arguments prepared for Flutter: " + args.toString());
            Log.d(TAG, "Arguments size: " + args.size());
            Log.d(TAG, "ID Scan Base64 length in args: " + ((String)args.get("idScanBase64")).length());

            // Enviar datos a Flutter
            mainHandler.post(() -> {
                try {
                    Log.d(TAG, "Invoking processIDScan method in Flutter");
                    processorChannel.invokeMethod("processIDScan", args, new MethodChannel.Result() {
                        @Override
                        public void success(Object result) {
                            try {
                                Log.d(TAG, "=== START processIDScan success ===");
                                Log.d(TAG, "Result from endpoint: " + (result != null ? result.toString() : "null"));
                                Log.d(TAG, "Result type: " + (result != null ? result.getClass().getName() : "null"));
                                
                                if (result instanceof Map) {
                                    Map<String, Object> response = (Map<String, Object>) result;
                                    Log.d(TAG, "Endpoint Response Details:");
                                    Log.d(TAG, "- Status: " + response.get("status"));
                                    Log.d(TAG, "- Message: " + response.get("message"));
                                    Log.d(TAG, "- Data: " + response.get("data"));
                                    Log.d(TAG, "- Error: " + response.get("error"));
                                    Log.d(TAG, "- Success: " + response.get("success"));
                                    Log.d(TAG, "- ScanResultBlob: " + (response.get("scanResultBlob") != null ? "present" : "null"));
                                    if (response.get("scanResultBlob") != null) {
                                        Log.d(TAG, "- ScanResultBlob length: " + response.get("scanResultBlob").toString().length());
                                    }
                                }
                                
                                Log.d(TAG, "ID scan data sent successfully to Flutter");
                                if (idScanResultCallbackRef != null) {
                                    Log.d(TAG, "Proceeding to next step with success JSON");
                                    try {
                                        // Crear un JSON válido con el scanResultBlob
                                        JSONObject successJson = new JSONObject();
                                        successJson.put("success", true);
                                        successJson.put("message", "ID scan completed successfully");
                                        successJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                                        Log.d(TAG, "Sending success JSON to endpoint: " + successJson.toString());
                                        Log.d(TAG, "Success JSON length: " + successJson.toString().length());
                                        Log.d(TAG, "Success JSON scanResultBlob length: " + successJson.getString("scanResultBlob").length());
                                        idScanResultCallbackRef.proceedToNextStep(successJson.toString());
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error sending success JSON: " + e.getMessage());
                                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                    }
                                }
                                // Limpiar el estado después de un éxito
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                                Log.d(TAG, "=== END processIDScan success ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing success response: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                if (idScanResultCallbackRef != null) {
                                    try {
                                        // Crear un JSON válido con el scanResultBlob
                                        JSONObject errorJson = new JSONObject();
                                        errorJson.put("success", false);
                                        errorJson.put("error", "PROCESSING_ERROR");
                                        errorJson.put("message", e.getMessage());
                                        errorJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                                        Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                                        Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                                        Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                                        idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                                    } catch (Exception ex) {
                                        Log.e(TAG, "Error sending error JSON: " + ex.getMessage());
                                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(ex));
                                    }
                                }
                                // Limpiar el estado después de un error
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                            }
                        }

                        @Override
                        public void error(String errorCode, String errorMessage, Object errorDetails) {
                            try {
                                Log.e(TAG, "=== START processIDScan error ===");
                                Log.e(TAG, "Endpoint Error Details:");
                                Log.e(TAG, "- Error Code: " + errorCode);
                                Log.e(TAG, "- Error Message: " + errorMessage);
                                Log.e(TAG, "- Error Details: " + (errorDetails != null ? errorDetails.toString() : "null"));
                                
                                if (idScanResultCallbackRef != null) {
                                    try {
                                        // Crear un JSON válido con el scanResultBlob
                                        JSONObject errorJson = new JSONObject();
                                        errorJson.put("success", false);
                                        errorJson.put("error", errorCode);
                                        errorJson.put("message", errorMessage);
                                        errorJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                                        Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                                        Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                                        Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                                        idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error sending error JSON: " + e.getMessage());
                                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                    }
                                }
                                // Limpiar el estado después de un error
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                                Log.e(TAG, "=== END processIDScan error ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling error response: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                // Limpiar el estado después de un error
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                            }
                        }

                        @Override
                        public void notImplemented() {
                            try {
                                Log.e(TAG, "=== START processIDScan notImplemented ===");
                                Log.e(TAG, "Method processIDScan not implemented in Flutter");
                                
                                if (idScanResultCallbackRef != null) {
                                    try {
                                        // Crear un JSON válido con el scanResultBlob
                                        JSONObject errorJson = new JSONObject();
                                        errorJson.put("success", false);
                                        errorJson.put("error", "NOT_IMPLEMENTED");
                                        errorJson.put("message", "Method not implemented");
                                        errorJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                                        Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                                        Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                                        Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                                        idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error sending error JSON: " + e.getMessage());
                                        Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                    }
                                }
                                // Limpiar el estado después de un error
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                                Log.e(TAG, "=== END processIDScan notImplemented ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling notImplemented: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                // Limpiar el estado después de un error
                                mainHandler.postDelayed(() -> {
                                    isProcessingDocument = false;
                                    isProcessingPhotoID = false;
                                }, 1000);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception while sending ID scan data to Flutter: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    if (idScanResultCallbackRef != null) {
                        try {
                            // Crear un JSON válido con el scanResultBlob
                            JSONObject errorJson = new JSONObject();
                            errorJson.put("success", false);
                            errorJson.put("error", "COMMUNICATION_ERROR");
                            errorJson.put("message", e.getMessage());
                            errorJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                            Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                            Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                            Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                            idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                        } catch (Exception ex) {
                            Log.e(TAG, "Error sending error JSON: " + ex.getMessage());
                            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(ex));
                        }
                    }
                    // Limpiar el estado después de un error
                    mainHandler.postDelayed(() -> {
                        isProcessingDocument = false;
                        isProcessingPhotoID = false;
                    }, 1000);
                }
            });
            Log.d(TAG, "=== END processIDScanWhileFaceTecSDKWaits ===");
        } catch (Exception e) {
            Log.e(TAG, "Error processing ID scan: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            if (idScanResultCallbackRef != null) {
                try {
                    // Crear un JSON válido con el scanResultBlob
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("success", false);
                    errorJson.put("error", "PROCESSING_ERROR");
                    errorJson.put("message", e.getMessage());
                    errorJson.put("scanResultBlob", idScanBase64); // Usar el scan base64 real
                    Log.d(TAG, "Sending error JSON to endpoint: " + errorJson.toString());
                    Log.d(TAG, "Error JSON length: " + errorJson.toString().length());
                    Log.d(TAG, "Error JSON scanResultBlob length: " + errorJson.getString("scanResultBlob").length());
                    idScanResultCallbackRef.proceedToNextStep(errorJson.toString());
                } catch (Exception ex) {
                    Log.e(TAG, "Error sending error JSON: " + ex.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(ex));
                }
            }
            // Esperar un momento antes de limpiar el estado
            mainHandler.postDelayed(() -> {
                isProcessingDocument = false;
                isProcessingPhotoID = false;
            }, 1000);
        }
    }

    public void onFaceTecSDKCompletelyDone() {
        Log.d(TAG, "SDK process completed");
        isProcessingPhotoID = false;
        isProcessingDocument = false;
    }

    private void cancelPhotoIDMatch() {
        try {
            Log.d(TAG, "Canceling Photo ID Match");
            if (timeoutRunnable != null) {
                mainHandler.removeCallbacks(timeoutRunnable);
                timeoutRunnable = null;
            }

            // Guardar referencias locales para evitar problemas de concurrencia
            FaceTecFaceScanResultCallback localFaceScanCallback = faceScanResultCallbackRef;
            FaceTecIDScanResultCallback localIDScanCallback = idScanResultCallbackRef;

            // Limpiar las referencias antes de llamar a cancel
            faceScanResultCallbackRef = null;
            idScanResultCallbackRef = null;

            // Llamar a cancel en los callbacks locales
            if (localFaceScanCallback != null) {
                Log.d(TAG, "Calling cancel on faceScanResultCallbackRef");
                try {
                    localFaceScanCallback.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling face scan: " + e.getMessage());
                }
            }

            if (localIDScanCallback != null) {
                Log.d(TAG, "Calling cancel on idScanResultCallbackRef");
                try {
                    localIDScanCallback.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling ID scan: " + e.getMessage());
                }
            }

            // Esperar un momento antes de limpiar el estado para dar tiempo a que la cámara se libere
            mainHandler.postDelayed(() -> {
                try {
                    // Limpiar el estado después de un breve retraso
                    isProcessingPhotoID = false;
                    isProcessingDocument = false;
                    Log.d(TAG, "Photo ID Match process cleaned up after delay");
                } catch (Exception e) {
                    Log.e(TAG, "Error in delayed cleanup: " + e.getMessage());
                }
            }, 1000); // Esperar 1 segundo

        } catch (Exception e) {
            Log.e(TAG, "Error canceling Photo ID Match: " + e.getMessage());
            // Asegurarnos de que el estado se limpie incluso si hay un error
            isProcessingPhotoID = false;
            isProcessingDocument = false;
        }
    }

    private void releaseCamera() {
        try {
            Log.d(TAG, "=== START releaseCamera ===");
            // Guardar referencias locales para evitar problemas de concurrencia
            FaceTecFaceScanResultCallback localFaceScanCallback = faceScanResultCallbackRef;
            FaceTecIDScanResultCallback localIDScanCallback = idScanResultCallbackRef;

            // Limpiar las referencias
            faceScanResultCallbackRef = null;
            idScanResultCallbackRef = null;

            // Llamar a cancel en los callbacks locales
            if (localFaceScanCallback != null) {
                Log.d(TAG, "Calling cancel on faceScanResultCallbackRef");
                try {
                    localFaceScanCallback.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling face scan: " + e.getMessage());
                }
            }

            if (localIDScanCallback != null) {
                Log.d(TAG, "Calling cancel on idScanResultCallbackRef");
                try {
                    localIDScanCallback.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling ID scan: " + e.getMessage());
                }
            }

            // Limpiar el estado
            isProcessingPhotoID = false;
            isProcessingDocument = false;
            Log.d(TAG, "=== END releaseCamera ===");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing camera: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
        }
    }

    private void onPhotoIDMatchResultBlobReceived(String photoIDMatchResultBlob) {
        try {
            Log.d(TAG, "=== START onPhotoIDMatchResultBlobReceived ===");
            Log.d(TAG, "Received result blob - Length: " + (photoIDMatchResultBlob != null ? photoIDMatchResultBlob.length() : 0));
            
            // Determinar qué callback usar basado en el estado actual
            if (isProcessingDocument && idScanResultCallbackRef != null) {
                Log.d(TAG, "Using ID scan callback for document processing");
                try {
                    idScanResultCallbackRef.proceedToNextStep(photoIDMatchResultBlob);
                    Log.d(TAG, "Successfully sent result to ID scan callback");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending result to ID scan callback: " + e.getMessage());
                    cancelPhotoIDMatch();
                }
            } else if (isProcessingPhotoID && faceScanResultCallbackRef != null) {
                Log.d(TAG, "Using face scan callback for selfie processing");
                try {
                    faceScanResultCallbackRef.proceedToNextStep(photoIDMatchResultBlob);
                    Log.d(TAG, "Successfully sent result to face scan callback");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending result to face scan callback: " + e.getMessage());
                    cancelPhotoIDMatch();
                }
            } else {
                Log.e(TAG, "No valid callback available for current state");
                Log.e(TAG, "isProcessingDocument: " + isProcessingDocument);
                Log.e(TAG, "isProcessingPhotoID: " + isProcessingPhotoID);
                Log.e(TAG, "idScanResultCallbackRef: " + (idScanResultCallbackRef != null ? "not null" : "null"));
                Log.e(TAG, "faceScanResultCallbackRef: " + (faceScanResultCallbackRef != null ? "not null" : "null"));
                
                // Si no hay callback válido, cancelar el proceso
                cancelPhotoIDMatch();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing result blob: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            cancelPhotoIDMatch();
        } finally {
            // No limpiar los callbacks aquí, se limpiarán cuando el proceso se complete
            if (isProcessingDocument) {
                isProcessingDocument = false;
            }
            if (isProcessingPhotoID) {
                isProcessingPhotoID = false;
            }
        }
        Log.d(TAG, "=== END onPhotoIDMatchResultBlobReceived ===");
    }

    private void onPhotoIDMatchResultUploadDelay(String uploadMessage) {
        try {
            Log.d(TAG, "Upload delay message: " + uploadMessage);
            if (faceScanResultCallbackRef != null) {
                Log.d(TAG, "Setting upload message override");
                faceScanResultCallbackRef.uploadMessageOverride(uploadMessage);
            } else {
                Log.e(TAG, "faceScanResultCallbackRef is null during upload delay");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling upload delay: " + e.getMessage());
        }
    }
} 
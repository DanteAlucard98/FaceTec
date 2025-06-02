package com.example.flutter_facetec_sample_app;

import android.content.Context;
import android.app.Activity;
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
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PhotoIDMatchProcessor implements FaceTecFaceScanProcessor, FaceTecIDScanProcessor, MethodChannel.MethodCallHandler {
    private static final String TAG = "PhotoIDMatchProcessor";
    private static final String PROCESSOR_CHANNEL = "com.facetec.sdk/photo_id_match";
    private MethodChannel processorChannel;
    private FaceTecFaceScanResultCallback faceScanResultCallbackRef;
    private FaceTecIDScanResultCallback idScanResultCallbackRef;
    private Context applicationContext;
    private Activity activity;
    private boolean isProcessingPhotoID = false;
    private boolean isProcessingDocument = false;
    private static final int FLUTTER_TIMEOUT_MS = 10000; // 10 seconds timeout
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private String currentSessionId = null;
    private String finalIdScanBase64 = null;
    private String finalIdScanBackBase64 = null;
    private boolean isFrontScan = true;
    private boolean isScanningComplete = false;
    private static PhotoIDMatchProcessor instance;

    public static synchronized PhotoIDMatchProcessor getInstance(MethodChannel processorChannel, Activity activity) {
        if (instance == null) {
            instance = new PhotoIDMatchProcessor(processorChannel, activity);
        }
        return instance;
    }

    public PhotoIDMatchProcessor(MethodChannel processorChannel, Activity activity) {
        this.processorChannel = processorChannel;
        this.activity = activity;
        this.applicationContext = activity.getApplicationContext();
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
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

    public void startPhotoIDMatchCheck(String sessionToken, MethodChannel.Result result) {
        try {
            Log.d(TAG, "Starting Photo ID Match process");
            
            if (activity == null) {
                Log.e(TAG, "Activity context is null");
                result.error("START_ERROR", "Activity context is not available", null);
                return;
            }
            
            // Configurar mensajes personalizados
            configureCustomMessages();
            
            // Iniciar el proceso
            isProcessingPhotoID = true;
            isProcessingDocument = false;
            isFrontScan = true;
            isScanningComplete = false;
            finalIdScanBase64 = null;
            finalIdScanBackBase64 = null;
            
            // Configurar el tipo de sesión para Photo ID Match
            FaceTecCustomization customization = new FaceTecCustomization();
            customization.getOverlayCustomization().brandingImage = R.drawable.flutter_logo;
            customization.getOverlayCustomization().backgroundColor = android.graphics.Color.WHITE;
            
            // Configurar el SDK para usar el modo Photo ID Match
            try {
                FaceTecSDK.setCustomization(customization);
                Log.d(TAG, "SDK configured for Photo ID Match");
            } catch (Exception e) {
                Log.e(TAG, "Error configuring SDK: " + e.getMessage());
            }
            
            // Lanzar la sesión con el tipo correcto para Photo ID Match
            FaceTecSessionActivity.createAndLaunchSession(activity, (FaceTecFaceScanProcessor)this, sessionToken);
            result.success(true);
        } catch (Exception e) {
            Log.e(TAG, "Error starting Photo ID Match: " + e.getMessage());
            result.error("START_ERROR", e.getMessage(), null);
        }
    }

    private void configureCustomMessages() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error configuring messages: " + e.getMessage());
        }
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

            if (activity == null) {
                Log.e(TAG, "Activity context is null");
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

                // Lanzar la sesión con el tipo correcto para escaneo combinado
                FaceTecSessionActivity.createAndLaunchSession(activity, (FaceTecIDScanProcessor)this, sessionToken);
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

    @Override
    public void processSessionWhileFaceTecSDKWaits(FaceTecSessionResult faceTecSessionResult, FaceTecFaceScanResultCallback faceTecFaceScanResultCallback) {
        try {
            Log.d(TAG, "=== START processSessionWhileFaceTecSDKWaits ===");
            Log.d(TAG, "Session ID: " + faceTecSessionResult.getSessionId());
            Log.d(TAG, "Session Status: " + faceTecSessionResult.getStatus());
            
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
            args.put("sessionStatus", faceTecSessionResult.getStatus().toString());
            args.put("sessionSuccess", Boolean.TRUE);
            args.put("endpoint", "/photo-id-match");
            
            // Enviar datos a Flutter
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
                            
                            // Enviar el resultado al SDK
                            if (faceScanResultCallbackRef != null) {
                                try {
                                    // Crear un JSON con el resultado de la validación
                                    JSONObject validationResult = new JSONObject();
                                    validationResult.put("success", true);
                                    validationResult.put("sessionId", currentSessionId);
                                    validationResult.put("nextStep", "DOCUMENT_SCAN");
                                    String validationResultString = validationResult.toString();
                                    Log.d(TAG, "Sending validation result to SDK: " + validationResultString);
                                    faceScanResultCallbackRef.proceedToNextStep(validationResultString);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending result to SDK: " + e.getMessage());
                                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                    cancelPhotoIDMatch();
                                }
                            }
                        }

                        @Override
                        public void error(String errorCode, String errorMessage, Object errorDetails) {
                            Log.e(TAG, "Error sending data to Flutter: " + errorMessage);
                            if (faceScanResultCallbackRef != null) {
                                try {
                                    JSONObject errorResult = new JSONObject();
                                    errorResult.put("success", false);
                                    errorResult.put("error", errorCode);
                                    errorResult.put("message", errorMessage);
                                    faceScanResultCallbackRef.proceedToNextStep(errorResult.toString());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending error result to SDK: " + e.getMessage());
                                    cancelPhotoIDMatch();
                                }
                            } else {
                                cancelPhotoIDMatch();
                            }
                        }

                        @Override
                        public void notImplemented() {
                            Log.e(TAG, "Method not implemented in Flutter");
                            if (faceScanResultCallbackRef != null) {
                                try {
                                    JSONObject errorResult = new JSONObject();
                                    errorResult.put("success", false);
                                    errorResult.put("error", "METHOD_NOT_IMPLEMENTED");
                                    errorResult.put("message", "Method not implemented in Flutter");
                                    faceScanResultCallbackRef.proceedToNextStep(errorResult.toString());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending not implemented result to SDK: " + e.getMessage());
                                    cancelPhotoIDMatch();
                                }
                            } else {
                                cancelPhotoIDMatch();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception while sending data to Flutter: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    if (faceScanResultCallbackRef != null) {
                        try {
                            JSONObject errorResult = new JSONObject();
                            errorResult.put("success", false);
                            errorResult.put("error", "EXCEPTION");
                            errorResult.put("message", e.getMessage());
                            faceScanResultCallbackRef.proceedToNextStep(errorResult.toString());
                        } catch (Exception ex) {
                            Log.e(TAG, "Error sending exception result to SDK: " + ex.getMessage());
                            cancelPhotoIDMatch();
                        }
                    } else {
                        cancelPhotoIDMatch();
                    }
                }
            });
            Log.d(TAG, "=== END processSessionWhileFaceTecSDKWaits ===");
        } catch (Exception e) {
            Log.e(TAG, "Error processing session: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            if (faceScanResultCallbackRef != null) {
                try {
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("success", false);
                    errorResult.put("error", "EXCEPTION");
                    errorResult.put("message", e.getMessage());
                    faceScanResultCallbackRef.proceedToNextStep(errorResult.toString());
                } catch (Exception ex) {
                    Log.e(TAG, "Error sending exception result to SDK: " + ex.getMessage());
                    cancelPhotoIDMatch();
                }
            } else {
                cancelPhotoIDMatch();
            }
        }
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(FaceTecIDScanResult faceTecIDScanResult, FaceTecIDScanResultCallback faceTecIDScanResultCallback) {
        Log.d(TAG, "=== START processIDScanWhileFaceTecSDKWaits ===");
        Log.d(TAG, "ID Scan Status: " + faceTecIDScanResult.getStatus());
        
        // Guardar el callback para usarlo más tarde
        this.idScanResultCallbackRef = faceTecIDScanResultCallback;
        
        // Guardar el ID de la sesión
        currentSessionId = faceTecIDScanResult.getSessionId();
        
        // Determinar si es el escaneo frontal o el reverso
        if (faceTecIDScanResult.getStatus() == FaceTecIDScanStatus.SUCCESS) {
            Log.d(TAG, "Processing scan");
            if (isFrontScan) {
                Log.d(TAG, "Storing front scan");
                finalIdScanBase64 = faceTecIDScanResult.getIDScanBase64();
                isFrontScan = false;
                // Indicar al SDK que necesitamos escanear el reverso
                faceTecIDScanResultCallback.proceedToNextStep(faceTecIDScanResult.getSessionId());
            } else {
                Log.d(TAG, "Storing back scan");
                finalIdScanBackBase64 = faceTecIDScanResult.getIDScanBase64();
                isScanningComplete = true;
            }
        }
        
        // Si tenemos ambos escaneos, proceder con el procesamiento
        if (finalIdScanBase64 != null && finalIdScanBackBase64 != null) {
            Log.d(TAG, "Both scans received, proceeding with processing");
            
            // Preparar los argumentos para enviar a Flutter
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", currentSessionId != null ? currentSessionId : "unknown");
            args.put("idScanBase64", finalIdScanBase64);
            args.put("idScanBackBase64", finalIdScanBackBase64);
            args.put("sessionStatus", faceTecIDScanResult.getStatus() != null ? faceTecIDScanResult.getStatus().toString() : "UNKNOWN");
            args.put("sessionSuccess", Boolean.TRUE);
            args.put("endpoint", "/photo-id-match");
            
            // Enviar los argumentos a Flutter
            processorChannel.invokeMethod("processIDScan", args, new MethodChannel.Result() {
                @Override
                public void success(Object result) {
                    Log.d(TAG, "Flutter processIDScan call succeeded");
                    if (idScanResultCallbackRef != null) {
                        try {
                            // Crear un JSON con el resultado final
                            JSONObject finalResult = new JSONObject();
                            finalResult.put("success", true);
                            finalResult.put("sessionId", currentSessionId);
                            finalResult.put("matchComplete", true);
                            idScanResultCallbackRef.proceedToNextStep(finalResult.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error sending final result to SDK: " + e.getMessage());
                            cancelPhotoIDMatch();
                        }
                    }
                }
                
                @Override
                public void error(String errorCode, String errorMessage, Object errorDetails) {
                    Log.e(TAG, "Flutter processIDScan call failed: " + errorMessage);
                    cancelPhotoIDMatch();
                }
                
                @Override
                public void notImplemented() {
                    Log.e(TAG, "Flutter processIDScan method not implemented");
                    cancelPhotoIDMatch();
                }
            });
        }
        
        Log.d(TAG, "=== END processIDScanWhileFaceTecSDKWaits ===");
    }

    public void onFaceTecSDKCompletelyDone() {
        Log.d(TAG, "SDK process completed");
        isProcessingPhotoID = false;
        isProcessingDocument = false;
        isFrontScan = true;
        isScanningComplete = false;
        finalIdScanBase64 = null;
        finalIdScanBackBase64 = null;
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
                    isFrontScan = true;
                    isScanningComplete = false;
                    finalIdScanBase64 = null;
                    finalIdScanBackBase64 = null;
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
            isFrontScan = true;
            isScanningComplete = false;
            finalIdScanBase64 = null;
            finalIdScanBackBase64 = null;
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
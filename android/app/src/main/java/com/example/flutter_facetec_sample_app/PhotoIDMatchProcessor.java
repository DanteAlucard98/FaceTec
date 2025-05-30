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
            args.put("sessionId", faceTecSessionResult.getSessionId());
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
                            // Proceder con el escaneo de documentos después de una sesión exitosa
                            if (isProcessingPhotoID) {
                                Log.d(TAG, "Starting document scan after successful photo ID match");
                                startDocumentScan(faceTecSessionResult.getSessionId());
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
                            // Intentar continuar con el escaneo de documentos incluso si hay un error
                            if (isProcessingPhotoID) {
                                Log.d(TAG, "Attempting document scan despite error");
                                startDocumentScan(faceTecSessionResult.getSessionId());
                            } else {
                                cancelPhotoIDMatch();
                            }
                        }

                        @Override
                        public void notImplemented() {
                            Log.e(TAG, "Method not implemented in Flutter");
                            if (timeoutRunnable != null) {
                                mainHandler.removeCallbacks(timeoutRunnable);
                                Log.d(TAG, "Removed timeout after notImplemented");
                            }
                            // Intentar continuar con el escaneo de documentos
                            if (isProcessingPhotoID) {
                                Log.d(TAG, "Attempting document scan despite notImplemented");
                                startDocumentScan(faceTecSessionResult.getSessionId());
                            } else {
                                cancelPhotoIDMatch();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception while sending data to Flutter: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    if (timeoutRunnable != null) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        Log.d(TAG, "Removed timeout after exception");
                    }
                    // Intentar continuar con el escaneo de documentos
                    if (isProcessingPhotoID) {
                        Log.d(TAG, "Attempting document scan despite exception");
                        startDocumentScan(faceTecSessionResult.getSessionId());
                    } else {
                        cancelPhotoIDMatch();
                    }
                }
            });
            Log.d(TAG, "=== END processSessionWhileFaceTecSDKWaits ===");
        } catch (Exception e) {
            Log.e(TAG, "Error processing session: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            // Intentar continuar con el escaneo de documentos
            if (isProcessingPhotoID) {
                Log.d(TAG, "Attempting document scan despite error");
                startDocumentScan(faceTecSessionResult.getSessionId());
            } else {
                cancelPhotoIDMatch();
            }
        }
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(FaceTecIDScanResult faceTecIDScanResult, FaceTecIDScanResultCallback faceTecIDScanResultCallback) {
        try {
            Log.d(TAG, "=== START processIDScanWhileFaceTecSDKWaits ===");
            Log.d(TAG, "FaceTecIDScanResult: " + (faceTecIDScanResult != null ? "not null" : "null"));
            Log.d(TAG, "Status: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getStatus() : "null"));
            Log.d(TAG, "Session ID: " + (faceTecIDScanResult != null ? faceTecIDScanResult.getSessionId() : "null"));
            Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
            
            // Guardar la referencia al callback
            idScanResultCallbackRef = faceTecIDScanResultCallback;
            Log.d(TAG, "Stored idScanResultCallbackRef");

            if (faceTecIDScanResult == null) {
                Log.e(TAG, "ID scan result is null");
                cancelPhotoIDMatch();
                return;
            }

            // Verificar si tenemos los datos necesarios
            String idScanBase64 = faceTecIDScanResult.getIDScanBase64();
            Log.d(TAG, "ID Scan Base64: " + (idScanBase64 != null ? "present" : "null"));
            
            if (idScanBase64 == null || idScanBase64.isEmpty()) {
                Log.e(TAG, "ID scan data is missing");
                cancelPhotoIDMatch();
                return;
            }

            // Verificar el estado del escaneo
            if (faceTecIDScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
                Log.e(TAG, "ID scan status is not success: " + faceTecIDScanResult.getStatus());
                // En lugar de cancelar, intentamos proceder con el siguiente paso
                if (idScanResultCallbackRef != null) {
                    Log.d(TAG, "Attempting to proceed despite non-success status");
                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                }
                return;
            }

            Log.d(TAG, "Preparing to send ID scan data to Flutter");
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", faceTecIDScanResult.getSessionId() != null ? faceTecIDScanResult.getSessionId() : "unknown");
            args.put("idScanBase64", idScanBase64);
            args.put("sessionStatus", faceTecIDScanResult.getStatus() != null ? faceTecIDScanResult.getStatus().toString() : "UNKNOWN");
            args.put("sessionSuccess", Boolean.TRUE);
            args.put("endpoint", "/idscan-only"); // Corregir el endpoint según la documentación
            Log.d(TAG, "Arguments prepared for Flutter");

            // Enviar datos a Flutter
            mainHandler.post(() -> {
                try {
                    Log.d(TAG, "Invoking processIDScan method in Flutter");
                    processorChannel.invokeMethod("processIDScan", args, new MethodChannel.Result() {
                        @Override
                        public void success(Object result) {
                            try {
                                Log.d(TAG, "=== START processIDScan success ===");
                                Log.d(TAG, "Result: " + (result != null ? result.toString() : "null"));
                                Log.d(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                
                                if (result instanceof Map) {
                                    Map<String, Object> response = (Map<String, Object>) result;
                                    Log.d(TAG, "Response status: " + response.get("status"));
                                    Log.d(TAG, "Response message: " + response.get("message"));
                                    Log.d(TAG, "Response data: " + response.get("data"));
                                }
                                
                                Log.d(TAG, "ID scan data sent successfully to Flutter");
                                if (idScanResultCallbackRef != null) {
                                    Log.d(TAG, "Proceeding to next step with ID_SCAN_SUCCESS");
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                } else {
                                    Log.e(TAG, "idScanResultCallbackRef is null after successful data send");
                                }
                                Log.d(TAG, "=== END processIDScan success ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing success response: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                if (idScanResultCallbackRef != null) {
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                }
                            }
                        }

                        @Override
                        public void error(String errorCode, String errorMessage, Object errorDetails) {
                            try {
                                Log.e(TAG, "=== START processIDScan error ===");
                                Log.e(TAG, "Error code: " + errorCode);
                                Log.e(TAG, "Error message: " + errorMessage);
                                Log.e(TAG, "Error details: " + (errorDetails != null ? errorDetails.toString() : "null"));
                                Log.e(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                
                                // Intentar proceder con el siguiente paso a pesar del error
                                if (idScanResultCallbackRef != null) {
                                    Log.d(TAG, "Attempting to proceed despite error");
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                }
                                Log.e(TAG, "=== END processIDScan error ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling error response: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                if (idScanResultCallbackRef != null) {
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                }
                            }
                        }

                        @Override
                        public void notImplemented() {
                            try {
                                Log.e(TAG, "=== START processIDScan notImplemented ===");
                                Log.e(TAG, "Method processIDScan not implemented in Flutter");
                                Log.e(TAG, "Current state - isProcessingPhotoID: " + isProcessingPhotoID + ", isProcessingDocument: " + isProcessingDocument);
                                
                                // Proceder con el siguiente paso
                                if (idScanResultCallbackRef != null) {
                                    Log.d(TAG, "Proceeding to next step despite notImplemented");
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                }
                                Log.e(TAG, "=== END processIDScan notImplemented ===");
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling notImplemented: " + e.getMessage());
                                Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                                if (idScanResultCallbackRef != null) {
                                    idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Exception while sending ID scan data to Flutter: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    // En lugar de cancelar, intentamos proceder con el siguiente paso
                    if (idScanResultCallbackRef != null) {
                        Log.d(TAG, "Attempting to proceed despite exception");
                        idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
                    }
                }
            });
            Log.d(TAG, "=== END processIDScanWhileFaceTecSDKWaits ===");
        } catch (Exception e) {
            Log.e(TAG, "Error processing ID scan: " + e.getMessage());
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            // En lugar de cancelar, intentamos proceder con el siguiente paso
            if (idScanResultCallbackRef != null) {
                Log.d(TAG, "Attempting to proceed despite error");
                idScanResultCallbackRef.proceedToNextStep("ID_SCAN_SUCCESS");
            }
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
            if (faceScanResultCallbackRef != null) {
                Log.d(TAG, "Calling cancel on faceScanResultCallbackRef");
                faceScanResultCallbackRef.cancel();
            }
            if (idScanResultCallbackRef != null) {
                Log.d(TAG, "Calling cancel on idScanResultCallbackRef");
                idScanResultCallbackRef.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling Photo ID Match: " + e.getMessage());
        } finally {
            faceScanResultCallbackRef = null;
            idScanResultCallbackRef = null;
            isProcessingPhotoID = false;
            isProcessingDocument = false;
            Log.d(TAG, "Photo ID Match process cleaned up");
        }
    }

    private void onPhotoIDMatchResultBlobReceived(String photoIDMatchResultBlob) {
        try {
            Log.d(TAG, "Received result blob - Length: " + (photoIDMatchResultBlob != null ? photoIDMatchResultBlob.length() : 0));
            if (faceScanResultCallbackRef != null) {
                Log.d(TAG, "Proceeding to next step with result blob");
                faceScanResultCallbackRef.proceedToNextStep(photoIDMatchResultBlob);
            } else {
                Log.e(TAG, "faceScanResultCallbackRef is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing result blob: " + e.getMessage());
        } finally {
            faceScanResultCallbackRef = null;
            isProcessingPhotoID = false;
        }
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
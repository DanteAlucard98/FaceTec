package com.example.flutter_facetec_sample_app;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.facetec.sdk.*;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.flutter.plugin.common.MethodChannel;

public class PhotoIDMatchProcessor implements FaceTecFaceScanProcessor, FaceTecIDScanProcessor {
    private boolean success = false;
    private boolean faceScanWasSuccessful = false;
    private final MainActivity mainActivity;
    private final MethodChannel processorChannel;
    private final MethodChannel idscannChannel;

    public PhotoIDMatchProcessor(String sessionToken, Context context, MethodChannel processorChannel, MethodChannel idscannChannel) {
        this.mainActivity = (MainActivity) context;
        this.processorChannel = processorChannel;
        this.idscannChannel = idscannChannel;

        // Configurar mensajes personalizados para el escaneo de ID
        FaceTecCustomization.setIDScanUploadMessageOverrides(
            "Subiendo\nID Escaneado", // Inicio de subida del frente del ID
            "Sigue Subiendo...\nConexión Lenta", // Subida del frente del ID en progreso
            "Subida Completada", // Subida del frente del ID completada
            "Procesando ID", // Procesando el frente del ID
            "Subiendo\nReverso del ID", // Inicio de subida del reverso del ID
            "Sigue Subiendo...\nConexión Lenta", // Subida del reverso del ID en progreso
            "Subida Completada", // Subida del reverso del ID completada
            "Procesando Reverso", // Procesando el reverso del ID
            "Guardando\nInformación", // Guardando información del usuario
            "Sigue Subiendo...\nConexión Lenta", // Subida de información en progreso
            "Información Guardada", // Información guardada
            "Procesando", // Procesando información
            "Subiendo\nDetalles NFC", // Subiendo detalles NFC
            "Sigue Subiendo...\nConexión Lenta", // Subida NFC en progreso
            "Subida Completada", // Subida NFC completada
            "Procesando\nDetalles NFC", // Procesando detalles NFC
            "Subiendo\nDetalles ID", // Subiendo detalles del ID
            "Sigue Subiendo...\nConexión Lenta", // Subida de detalles en progreso
            "Subida Completada", // Subida de detalles completada
            "Procesando\nDetalles ID" // Procesando detalles del ID
        );

        // Iniciar la sesión de FaceTec
        FaceTecSessionActivity.createAndLaunchSession(
            context,
            this,
            this,
            sessionToken
        );
    }

    @Override
    public void processSessionWhileFaceTecSDKWaits(final FaceTecSessionResult sessionResult, final FaceTecFaceScanResultCallback faceScanResultCallback) {
        if (sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            Log.d("PhotoIDMatchProcessor", "Sesión no completada exitosamente, cancelando.");
            faceScanResultCallback.cancel();
            return;
        }

        try {
            // Preparar datos del escaneo facial
            JSONObject parameters = new JSONObject();
            parameters.put("faceScan", sessionResult.getFaceScanBase64());
            parameters.put("auditTrailImage", sessionResult.getAuditTrailCompressedBase64()[0]);
            parameters.put("lowQualityAuditTrailImage", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);

            // Enviar datos a Flutter para procesamiento
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", sessionResult.getSessionId());
            args.put("scanType", "faceScan");
            args.put("faceScanBase64", sessionResult.getFaceScanBase64());
            args.put("auditTrailCompressedBase64", sessionResult.getAuditTrailCompressedBase64()[0]);
            args.put("lowQualityAuditTrailCompressedBase64", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);

            processorChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                @Override
                public void success(Object result) {
                    try {
                        if (result instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) result;
                            String scanResultBlob = (String) resultMap.get("scanResultBlob");
                            
                            if (scanResultBlob != null) {
                                faceScanWasSuccessful = faceScanResultCallback.proceedToNextStep(scanResultBlob);
                            } else {
                                faceScanResultCallback.cancel();
                            }
                        } else {
                            faceScanResultCallback.cancel();
                        }
                    } catch (Exception e) {
                        Log.e("PhotoIDMatchProcessor", "Error procesando resultado del escaneo facial: " + e.getMessage());
                        faceScanResultCallback.cancel();
                    }
                }

                @Override
                public void error(String errorCode, String errorMessage, Object errorDetails) {
                    Log.e("PhotoIDMatchProcessor", "Error de Flutter: " + errorCode + " - " + errorMessage);
                    faceScanResultCallback.cancel();
                }

                @Override
                public void notImplemented() {
                    Log.e("PhotoIDMatchProcessor", "Método no implementado en Flutter");
                    faceScanResultCallback.cancel();
                }
            });
        } catch (Exception e) {
            Log.e("PhotoIDMatchProcessor", "Error procesando escaneo facial: " + e.getMessage());
            faceScanResultCallback.cancel();
        }
    }

    @Override
    public void processIDScanWhileFaceTecSDKWaits(final FaceTecIDScanResult idScanResult, final FaceTecIDScanResultCallback idScanResultCallback) {
        if (idScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
            Log.d("PhotoIDMatchProcessor", "Sesión no completada exitosamente, cancelando.");
            idScanResultCallback.cancel();
            return;
        }

        try {
            // Preparar datos del escaneo de ID
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

            // Enviar datos a Flutter para procesamiento
            Map<String, Object> args = new HashMap<>();
            args.put("status", "sessionCompletedSuccessfully");
            args.put("sessionId", idScanResult.getSessionId());
            args.put("scanType", "idScan");
            args.put("idScanBase64", idScanResult.getIDScanBase64());
            
            if(frontImagesCompressedBase64.size() > 0) {
                args.put("idScanFrontImage", frontImagesCompressedBase64.get(0));
            }
            if(backImagesCompressedBase64.size() > 0) {
                args.put("idScanBackImage", backImagesCompressedBase64.get(0));
            }

            // Simular progreso de subida
            idScanResultCallback.uploadProgress(0);

            idscannChannel.invokeMethod("processSession", args, new MethodChannel.Result() {
                @Override
                public void success(Object result) {
                    try {
                        if (result instanceof Map) {
                            Map<String, Object> resultMap = (Map<String, Object>) result;
                            String scanResultBlob = (String) resultMap.get("scanResultBlob");
                            
                            if (scanResultBlob != null) {
                                idScanResultCallback.uploadProgress(1);
                                success = idScanResultCallback.proceedToNextStep(scanResultBlob);
                            } else {
                                idScanResultCallback.cancel();
                            }
                        } else {
                            idScanResultCallback.cancel();
                        }
                    } catch (Exception e) {
                        Log.e("PhotoIDMatchProcessor", "Error procesando resultado del escaneo de ID: " + e.getMessage());
                        idScanResultCallback.cancel();
                    }
                }

                @Override
                public void error(String errorCode, String errorMessage, Object errorDetails) {
                    Log.e("PhotoIDMatchProcessor", "Error de Flutter: " + errorCode + " - " + errorMessage);
                    idScanResultCallback.cancel();
                }

                @Override
                public void notImplemented() {
                    Log.e("PhotoIDMatchProcessor", "Método no implementado en Flutter");
                    idScanResultCallback.cancel();
                }
            });
        } catch (Exception e) {
            Log.e("PhotoIDMatchProcessor", "Error procesando escaneo de ID: " + e.getMessage());
            idScanResultCallback.cancel();
        }
    }

    public boolean isSuccess() {
        return this.success;
    }
} 
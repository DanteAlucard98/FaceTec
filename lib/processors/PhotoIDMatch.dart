import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import '../facetec_config.dart';

class PhotoIDMatchProcessor {
  String sessionToken = "";
  bool success = false;
  bool isRequestInProgress = false;
  http.Request? latestNetworkRequest;
  bool isDocumentScanning = false;
  bool isProcessingPhotoID = false;
  bool isSelfieCompleted = false;

  static const MethodChannel _channel = MethodChannel('com.facetec.sdk/photo_id_match');
  static const MethodChannel _mainChannel = MethodChannel('com.facetec.sdk');

  PhotoIDMatchProcessor() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    print("PhotoIDMatchProcessor received call: ${call.method}");
    switch (call.method) {
      case 'processSession': {
        print("Processing session with arguments: ${call.arguments}");
        isProcessingPhotoID = true;
        isSelfieCompleted = false;
        await processSession(
          call.arguments['status'],
          call.arguments['faceScanBase64'],
          call.arguments['sessionId'],
          call.arguments['isPhotoID'],
          call.arguments['sessionStatus'],
          call.arguments['sessionSuccess'],
          call.arguments['auditTrailImage'],
          call.arguments['lowQualityAuditTrailImage']
        );
        break;
      }
      case 'processIDScan': {
        print("Processing ID scan with arguments: ${call.arguments}");
        if (!isSelfieCompleted) {
          print("Selfie process not completed yet, waiting...");
          return;
        }
        isDocumentScanning = true;
        await processIDScan(
          call.arguments['status'],
          call.arguments['idScanBase64'],
          call.arguments['sessionId'],
          call.arguments['sessionStatus'],
          call.arguments['sessionSuccess'],
          call.arguments['endpoint'] ?? '/idscan-only'
        );
        break;
      }
      default:
        print("Unhandled method call: ${call.method}");
        break;
    }
  }

  Future<void> processIDScan(
    String status,
    String idScanBase64,
    String sessionId,
    String sessionStatus,
    bool sessionSuccess,
    String endpoint
  ) async {
    print("=== START processIDScan ===");
    print("Status: $status");
    print("Session ID: $sessionId");
    print("Session Status: $sessionStatus");
    print("Session Success: $sessionSuccess");
    print("Endpoint: $endpoint");
    print("ID Scan Base64 length: ${idScanBase64.length}");
    
    if (!isSelfieCompleted) {
      print("Selfie process not completed successfully, canceling ID scan");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      await _channel.invokeMethod("releaseCamera", {});
      return;
    }
    
    if (status != 'sessionCompletedSuccessfully') {
      print("ID scan session was not completed successfully, canceling process");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      await _channel.invokeMethod("releaseCamera", {});
      return;
    }

    final parameters = {
      "idScan": idScanBase64,
      "sessionId": sessionId,
      "sessionStatus": sessionStatus,
      "sessionSuccess": sessionSuccess,
      "ftUserAgentString": await _getAPIUserAgentString(),
      "documentType": "ID_DOCUMENT",
      "countryCode": "ANY"
    };

    print("=== Request Parameters ===");
    print("Parameters keys: ${parameters.keys.join(', ')}");
    print("Session ID length: ${sessionId.length}");
    print("Session Status length: ${sessionStatus.length}");
    print("User Agent String length: ${parameters['ftUserAgentString'].toString().length}");
    
    final uri = Uri.parse('${FaceTecConfig.baseURL}$endpoint');
    print("=== Request Details ===");
    print("Full URL: ${uri.toString()}");
    print("Base URL: ${FaceTecConfig.baseURL}");
    print("Endpoint path: $endpoint");

    final userAgent = await _getAPIUserAgentString();
    print("=== Headers ===");
    print("User-Agent: $userAgent");
    print("X-Device-Key: ${FaceTecConfig.deviceKeyIdentifier}");

    final request = http.Request('POST', uri)
      ..headers['Content-Type'] = 'application/json'
      ..headers['X-Device-Key'] = FaceTecConfig.deviceKeyIdentifier
      ..headers["User-Agent"] = userAgent
      ..headers["X-User-Agent"] = userAgent
      ..body = jsonEncode(parameters);

    isRequestInProgress = true;
    print("=== Sending Request ===");
    print("Request body length: ${request.body.length}");

    try {
      print("=== Making HTTP Request ===");
      final response = await http.Client().send(request);
      print("Response status code: ${response.statusCode}");
      print("Response headers: ${response.headers}");
      
      final responseData = await response.stream.toBytes();
      final responseBody = utf8.decode(responseData);
      print("=== Response Details ===");
      print("Response body length: ${responseBody.length}");
      print("Response body: $responseBody");
      
      if (responseBody.isEmpty) {
        print("Empty response received, canceling scan");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        await _channel.invokeMethod("releaseCamera", {});
        success = false;
        return;
      }

      try {
        print("=== Parsing Response JSON ===");
      final responseJSON = jsonDecode(responseBody);
        print("Response JSON keys: ${responseJSON.keys.join(', ')}");

      if (responseJSON['error'] == true) {
          final errorMessage = responseJSON['errorMessage'] ?? 'Unknown error from endpoint';
          print("=== Error Details ===");
          print("Error message: $errorMessage");
          print("Full error response: $responseJSON");
          
          final errorJson = jsonEncode({
            "success": false,
            "error": "ENDPOINT_ERROR",
            "message": errorMessage
          });
          await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {
            "photoIDMatchResultBlob": errorJson
          });
          await _channel.invokeMethod("releaseCamera", {});
          success = false;
        return;
      }

      if (responseJSON['scanResultBlob'] != null && responseJSON['wasProcessed'] == true) {
        final scanResultBlob = responseJSON['scanResultBlob'];
          print("=== Processing Scan Result ===");
          print("Scan result blob length: ${scanResultBlob.length}");
          print("Was processed: ${responseJSON['wasProcessed']}");
          
          print("Sending scanResultBlob to native SDK");
          try {
            // No intentar parsear el scanResultBlob como JSON
            print("Sending scanResultBlob directly to native SDK");
            await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {
              "photoIDMatchResultBlob": scanResultBlob
            });
            
            await Future.delayed(const Duration(milliseconds: 500));
            
        success = true;
          } catch (e) {
            print("Error sending scan result to native SDK: $e");
            await _channel.invokeMethod("cancelPhotoIDMatch", {});
            await _channel.invokeMethod("releaseCamera", {});
            success = false;
          }
      } else {
          final errorMessage = responseJSON['errorMessage'] ?? 'No scanResultBlob or wasProcessed is false';
          print("=== Error: No Valid Scan Result ===");
          print("Error message: $errorMessage");
          print("Full response: $responseJSON");
          
          final errorJson = jsonEncode({
            "success": false,
            "error": "INVALID_SCAN_RESULT",
            "message": errorMessage
          });
          
          await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {
            "photoIDMatchResultBlob": errorJson
          });
          await _channel.invokeMethod("releaseCamera", {});
          success = false;
        }
      } catch (jsonError) {
        print("=== JSON Parse Error ===");
        print("Error: $jsonError");
        print("Raw response body: $responseBody");
        
        final errorJson = jsonEncode({
          "success": false,
          "error": "JSON_PARSE_ERROR",
          "message": jsonError.toString()
        });
        
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {
          "photoIDMatchResultBlob": errorJson
        });
        await _channel.invokeMethod("releaseCamera", {});
        success = false;
      }
    } catch (e, stackTrace) {
      print("=== Network Error ===");
      print("Error: $e");
      print("Stack trace: $stackTrace");
      
      final errorJson = jsonEncode({
        "success": false,
        "error": "NETWORK_ERROR",
        "message": e.toString()
      });
      
      await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {
        "photoIDMatchResultBlob": errorJson
      });
      await _channel.invokeMethod("releaseCamera", {});
      success = false;
    } finally {
      isRequestInProgress = false;
      isDocumentScanning = false;
      print("=== END processIDScan ===");
    }

    // Update user if upload is taking a while
    Future.delayed(const Duration(seconds: 6), () async {
      if (!isRequestInProgress) {
        return;
      }
      print("=== Upload Delay ===");
      print("ID scan upload is taking longer than expected, showing delay message");
      const uploadMessage = "Still Processing...";
      await _channel.invokeMethod("onPhotoIDMatchResultUploadDelay", {"uploadMessage": uploadMessage});
    });
  }

  Future<void> processSession(
    String status,
    String faceScanBase64,
    String sessionId,
    bool isPhotoID,
    String sessionStatus,
    bool sessionSuccess,
    String? auditTrailImage,
    String? lowQualityAuditTrailImage
  ) async {
    print("Starting processSession with status: $status");
    print("Arguments received:");
    print("status: $status");
    print("sessionId: $sessionId");
    print("isPhotoID: $isPhotoID");
    print("sessionStatus: $sessionStatus");
    print("sessionSuccess: $sessionSuccess");
    print("auditTrailImage length: ${auditTrailImage?.length ?? 0}");
    print("lowQualityAuditTrailImage length: ${lowQualityAuditTrailImage?.length ?? 0}");
    
    if (status != 'sessionCompletedSuccessfully') {
      print("Session was not completed successfully, canceling process");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      isSelfieCompleted = false; // Asegurar que isSelfieCompleted sea false
      return;
    }

    // Verificar que tenemos las imágenes necesarias
    if (auditTrailImage == null || auditTrailImage.isEmpty) {
      print("Audit trail image is missing, canceling process");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      isSelfieCompleted = false; // Asegurar que isSelfieCompleted sea false
      return;
    }

    final parameters = {
      "faceScan": faceScanBase64,
      "sessionId": sessionId,
      "isPhotoID": isPhotoID ?? false,
      "sessionStatus": sessionStatus,
      "sessionSuccess": sessionSuccess ?? false,
      "auditTrailImage": auditTrailImage,
      "lowQualityAuditTrailImage": lowQualityAuditTrailImage ?? auditTrailImage,
      "ftUserAgentString": await _getAPIUserAgentString()
    };

    print("Preparing request with parameters: ${parameters.keys.join(', ')}");
    
    final uri = Uri.parse('${FaceTecConfig.baseURL}/liveness-3d');
    print("Making request to: ${uri.toString()}");

    final userAgent = await _getAPIUserAgentString();
    print("Using User-Agent: $userAgent");

    final request = http.Request('POST', uri)
      ..headers['Content-Type'] = 'application/json'
      ..headers['X-Device-Key'] = FaceTecConfig.deviceKeyIdentifier
      ..headers["User-Agent"] = userAgent
      ..headers["X-User-Agent"] = userAgent
      ..body = jsonEncode(parameters);

    isRequestInProgress = true;
    print("Request prepared, sending...");

    try {
      final response = await http.Client().send(request);
      print("Received response with status code: ${response.statusCode}");
      
      final responseData = await response.stream.toBytes();
      final responseBody = utf8.decode(responseData);
      print("Response body: $responseBody");
      
      final responseJSON = jsonDecode(responseBody);

      if (responseJSON['error'] == true) {
        print("Error while processing Photo ID Match: ${responseJSON['errorMessage']}");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        isSelfieCompleted = false; // Asegurar que isSelfieCompleted sea false
        return;
      }

      if (responseJSON['scanResultBlob'] != null && responseJSON['wasProcessed'] == true) {
        final scanResultBlob = responseJSON['scanResultBlob'];
        print("Received scanResultBlob, proceeding to next step");
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {"photoIDMatchResultBlob": scanResultBlob});
        success = true;
        
        // Marcar la selfie como completada solo si todo fue exitoso
        isSelfieCompleted = true;
        
        // Esperar un momento antes de iniciar el escaneo de documento
        await Future.delayed(const Duration(seconds: 2));
        
        // Iniciar el proceso de escaneo de documento después de la foto exitosa
        sessionToken = sessionId;
        await startDocumentScan();
      } else {
        print("No scanResultBlob or wasProcessed is false, canceling process");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        isSelfieCompleted = false; // Asegurar que isSelfieCompleted sea false
        return;
      }
    } catch (e, stackTrace) {
      print("Error during Photo ID Match processing: $e");
      print("Stack trace: $stackTrace");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      isSelfieCompleted = false; // Asegurar que isSelfieCompleted sea false
      return;
    } finally {
      isRequestInProgress = false;
      isProcessingPhotoID = false;
      print("Request processing completed");
    }

    // Update user if upload is taking a while
    Future.delayed(const Duration(seconds: 6), () async {
      if (!isRequestInProgress) {
        return;
      }
      print("Upload is taking longer than expected, showing delay message");
      const uploadMessage = "Still Processing...";
      await _channel.invokeMethod("onPhotoIDMatchResultUploadDelay", {"uploadMessage": uploadMessage});
    });
  }

  Future<void> startDocumentScan() async {
    try {
      if (!isSelfieCompleted) {
        print("Selfie process not completed successfully, cannot start document scan");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        return;
      }
      
      if (sessionToken.isEmpty) {
        print("No valid session token available, cannot start document scan");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        return;
      }
      
      isDocumentScanning = true;
      print("Initiating document scan process");
      
      // Esperar un momento para asegurar que la cámara esté lista
      await Future.delayed(const Duration(seconds: 2));
      
      // Configurar mensajes personalizados para el escaneo de documento
      await _channel.invokeMethod("configureDocumentScanMessages", {
        "uploadMessage": "Uploading\nEncrypted\nPhoto ID",
        "uploadSlowMessage": "Still Uploading...\nSlow Connection",
        "uploadCompleteMessage": "Upload Complete",
        "processingMessage": "Processing Photo ID"
      });

      // Esperar un momento adicional para asegurar que la cámara esté lista
      await Future.delayed(const Duration(seconds: 1));

      // Iniciar el proceso de escaneo de documento
      final result = await _channel.invokeMethod("startDocumentScan", {
        "sessionToken": sessionToken
      });

      print("Document scan result: $result");
    } catch (e) {
      print("Error during document scan: $e");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      isSelfieCompleted = false; // Resetear el estado en caso de error
    } finally {
      isDocumentScanning = false;
    }
  }

  Future<String> _getAPIUserAgentString() async {
    const platform = MethodChannel('com.facetec.sdk');
    final String result = await platform.invokeMethod("createAPIUserAgentString");
    return result;
  }

  bool isSuccess() {
    return success;
  }
} 
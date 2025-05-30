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

  static const MethodChannel _channel = MethodChannel('com.facetec.sdk/photo_id_match');

  PhotoIDMatchProcessor() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    print("PhotoIDMatchProcessor received call: ${call.method}");
    switch (call.method) {
      case 'processSession': {
        print("Processing session with arguments: ${call.arguments}");
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
        await processIDScan(
          call.arguments['status'],
          call.arguments['idScanBase64'],
          call.arguments['sessionId'],
          call.arguments['sessionStatus'],
          call.arguments['sessionSuccess'],
          call.arguments['endpoint'] ?? '/id-scan'
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
    print("Starting processIDScan with status: $status");
    
    if (status != 'sessionCompletedSuccessfully') {
      print("ID scan session was not completed successfully, canceling.");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
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

    print("Preparing ID scan request with parameters: ${parameters.keys.join(', ')}");
    
    final uri = Uri.parse('${FaceTecConfig.baseURL}/idscan-only');
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
    print("ID scan request prepared, sending...");

    try {
      final response = await http.Client().send(request);
      print("Received response with status code: ${response.statusCode}");
      
      final responseData = await response.stream.toBytes();
      final responseBody = utf8.decode(responseData);
      print("Response body: $responseBody");
      
      if (responseBody.isEmpty) {
        print("Empty response received, proceeding with success");
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {"photoIDMatchResultBlob": "{}"});
        success = true;
        return;
      }

      final responseJSON = jsonDecode(responseBody);

      if (responseJSON['error'] == true) {
        print("Error while processing ID scan: ${responseJSON['errorMessage']}");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
        return;
      }

      if (responseJSON['scanResultBlob'] != null && responseJSON['wasProcessed'] == true) {
        final scanResultBlob = responseJSON['scanResultBlob'];
        print("Received scanResultBlob for ID scan, proceeding to next step");
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {"photoIDMatchResultBlob": scanResultBlob});
        success = true;
      } else {
        print("No scanResultBlob or wasProcessed is false for ID scan, proceeding with success");
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {"photoIDMatchResultBlob": "{}"});
        success = true;
      }
    } catch (e, stackTrace) {
      print("Error during ID scan processing: $e");
      print("Stack trace: $stackTrace");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
    } finally {
      isRequestInProgress = false;
      print("ID scan request processing completed");
    }

    // Update user if upload is taking a while
    Future.delayed(const Duration(seconds: 6), () async {
      if (!isRequestInProgress) {
        return;
      }
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
    
    if (status != 'sessionCompletedSuccessfully') {
      print("Session was not completed successfully, canceling.");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      return;
    }

    // Verificar que tenemos las imágenes necesarias
    if (auditTrailImage == null || auditTrailImage.isEmpty) {
      print("Audit trail image is missing");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
      return;
    }

    final parameters = {
      "faceScan": faceScanBase64,
      "sessionId": sessionId,
      "isPhotoID": isPhotoID,
      "sessionStatus": sessionStatus,
      "sessionSuccess": sessionSuccess,
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
        return;
      }

      if (responseJSON['scanResultBlob'] != null && responseJSON['wasProcessed'] == true) {
        final scanResultBlob = responseJSON['scanResultBlob'];
        print("Received scanResultBlob, proceeding to next step");
        await _channel.invokeMethod("onPhotoIDMatchResultBlobReceived", {"photoIDMatchResultBlob": scanResultBlob});
        success = true;
        
        // Iniciar el proceso de escaneo de documento después de la foto exitosa
        sessionToken = sessionId; // Guardar el sessionId como token para el escaneo del documento
        await startDocumentScan();
      } else {
        print("No scanResultBlob or wasProcessed is false, canceling");
        await _channel.invokeMethod("cancelPhotoIDMatch", {});
      }
    } catch (e, stackTrace) {
      print("Error during Photo ID Match processing: $e");
      print("Stack trace: $stackTrace");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
    } finally {
      isRequestInProgress = false;
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
      isDocumentScanning = true;
      print("Initiating document scan process");
      
      // Configurar mensajes personalizados para el escaneo de documento
      await _channel.invokeMethod("configureDocumentScanMessages", {
        "uploadMessage": "Uploading\nEncrypted\nPhoto ID",
        "uploadSlowMessage": "Still Uploading...\nSlow Connection",
        "uploadCompleteMessage": "Upload Complete",
        "processingMessage": "Processing Photo ID"
      });

      // Iniciar el proceso de escaneo de documento
      final result = await _channel.invokeMethod("startDocumentScan", {
        "sessionToken": sessionToken
      });

      print("Document scan result: $result");
    } catch (e) {
      print("Error during document scan: $e");
      await _channel.invokeMethod("cancelPhotoIDMatch", {});
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
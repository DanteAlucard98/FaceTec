import 'dart:convert';
import 'package:flutter/services.dart';
import '../facetec_config.dart';

class MatchIdScanProcessor {
  bool success = false;
  bool isProcessing = false;

  static const MethodChannel _channel = MethodChannel('com.facetec.sdk/matchidscan');
  static final MatchIdScanProcessor _instance = MatchIdScanProcessor._internal();

  // Singleton pattern
  factory MatchIdScanProcessor() {
    return _instance;
  }

  MatchIdScanProcessor._internal() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    print("MatchIdScanProcessor received method call: ${call.method}");
    switch (call.method) {
      case 'processSession':
        final Map<String, dynamic> args = Map<String, dynamic>.from(call.arguments);
        return _processMatchIdScanResult(args);
      default:
        throw PlatformException(
          code: 'Unimplemented',
          details: "Method ${call.method} not implemented",
        );
    }
  }

  Future<Map<String, dynamic>> _processMatchIdScanResult(Map<String, dynamic> sessionResult) async {
    try {
      print("Processing match ID scan result: $sessionResult");
      
      // Extract session data
      final String sessionId = sessionResult['sessionId'] ?? '';
      final String idScanBase64 = sessionResult['idScanBase64'] ?? '';
      final String faceScanBase64 = sessionResult['faceScanBase64'] ?? '';
      
      // Create parameters for API call
      final parameters = {
        "idScan": idScanBase64,
        "faceScan": faceScanBase64,
        "minMatchLevel": 3, // Minimum match level for face comparison
      };
      
      if (sessionResult['idScanFrontImage'] != null) {
        parameters["idScanFrontImage"] = sessionResult['idScanFrontImage'];
      }
      
      if (sessionResult['idScanBackImage'] != null) {
        parameters["idScanBackImage"] = sessionResult['idScanBackImage'];
      }

      // Process the match result
      Map<String, dynamic> responseData;
      
      // Simulate processing delay
      await Future.delayed(Duration(milliseconds: 500));
      
      // In a real implementation, you would send this data to your server
      // and process it there. For this example, we'll mock a server response.
      responseData = {
        "success": true,
        "wasProcessed": true,
        "error": false,
        "scanResultBlob": jsonEncode({
          "idScanStatus": "Complete",
          "faceMatchStatus": "Matched",
          "isComplete": true,
          "sessionId": sessionId,
          "encryptionKey": "mock_encryption_key",
          "processedAt": DateTime.now().toIso8601String()
        })
      };
      
      print("Sending match scan result to native side: ${jsonEncode(responseData)}");
      
      // Ensure we send the response back to the native side
      await _channel.invokeMethod("onScanResultBlobReceived", {
        "scanResultBlob": responseData["scanResultBlob"]
      });
      
      success = responseData["success"] == true && responseData["error"] != true;
      
      return responseData;
      
    } catch (e) {
      print("Error processing match ID scan result: $e");
      final errorResponse = {
        "success": false,
        "wasProcessed": true,
        "error": true,
        "errorMessage": "Error processing scan: $e",
        "scanResultBlob": "{}"
      };
      
      // Ensure we send the error response back to the native side
      await _channel.invokeMethod("onScanResultBlobReceived", {
        "scanResultBlob": errorResponse["scanResultBlob"]
      });
      
      return errorResponse;
    }
  }

  // Cancel the current scan session
  Future<void> cancelSession() async {
    try {
      print("Cancelling match ID scan session");
      await _channel.invokeMethod("cancelSession");
    } catch (e) {
      print("Error canceling session: $e");
    }
  }

  // Get the User Agent string for API calls
  Future<String> createAPIUserAgentString() async {
    try {
      final String userAgent = await _channel.invokeMethod("createAPIUserAgentString");
      return userAgent;
    } catch (e) {
      print("Error getting user agent: $e");
      return "FaceTecSDK/Flutter-Match-ID-Scan";
    }
  }

  // Update the scan result blob
  Future<void> onScanResultBlobReceived(String scanResultBlob) async {
    try {
      print("Sending scan result blob to native side: $scanResultBlob");
      await _channel.invokeMethod("onScanResultBlobReceived", {
        "scanResultBlob": scanResultBlob
      });
    } catch (e) {
      print("Error sending scan result blob: $e");
    }
  }

  // Handle upload delay by showing a message
  Future<void> onScanResultUploadDelay(String uploadMessage) async {
    try {
      print("Sending upload delay message: $uploadMessage");
      await _channel.invokeMethod("onScanResultUploadDelay", {
        "uploadMessage": uploadMessage
      });
    } catch (e) {
      print("Error sending upload delay message: $e");
    }
  }
} 
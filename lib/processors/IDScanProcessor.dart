import 'dart:convert';
import 'package:flutter/services.dart';
import '../facetec_config.dart';

// This is a dedicated class to perform Photo ID Scanning with the FaceTecSDK.
// It handles the method channel communication with the native code and processes scan results.
class IDScanProcessor {
  bool success = false;
  bool isProcessing = false;

  static const MethodChannel _channel = MethodChannel('com.facetec.sdk/idscann');
  static final IDScanProcessor _instance = IDScanProcessor._internal();

  // Singleton pattern
  factory IDScanProcessor() {
    return _instance;
  }

  IDScanProcessor._internal() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'processSession':
        final Map<String, dynamic> args = Map<String, dynamic>.from(call.arguments);
        return _processIDScanResult(args);
      default:
        throw PlatformException(
          code: 'Unimplemented',
          details: "Method ${call.method} not implemented",
        );
    }
  }

  // Process the ID scan result received from the native side
  Future<Map<String, dynamic>> _processIDScanResult(Map<String, dynamic> sessionResult) async {
    try {
      print("Processing ID scan result: $sessionResult");
      
      // Check what type of scan we have (front/back)
      bool isFrontScan = sessionResult['idScanFrontImage'] != null && sessionResult['idScanBackImage'] == null;
      bool isBackScan = sessionResult['idScanBackImage'] != null;
      
      print("Scan type - Front: $isFrontScan, Back: $isBackScan");
      
      // Extract session data
      final String sessionId = sessionResult['sessionId'] ?? '';
      final String idScanBase64 = sessionResult['idScanBase64'] ?? '';
      
      // Create parameters for a server API call (if needed)
      final parameters = {
        "idScan": idScanBase64,
      };
      
      if (sessionResult['idScanFrontImage'] != null) {
        parameters["idScanFrontImage"] = sessionResult['idScanFrontImage'];
      }
      
      if (sessionResult['idScanBackImage'] != null) {
        parameters["idScanBackImage"] = sessionResult['idScanBackImage'];
      }
      
      // Process based on scan type
      Map<String, dynamic> responseData;
      
      if (isFrontScan) {
        print("Front scan completed successfully");
        
        // In a real implementation, you would send this data to your server
        // and process it there. For this example, we'll mock a server response.
        
        // Mock server response for front scan
        responseData = {
          "success": true,
          "wasProcessed": true,
          "error": false,
          "scanResultBlob": jsonEncode({
            "idScanStatus": "FrontSuccessful",
            "hasBackSide": true,
            "requiresBackScan": true
          })
        };
      } else if (isBackScan) {
        print("Back scan completed successfully");
        
        // Mock server response for back scan
        responseData = {
          "success": true,
          "wasProcessed": true,
          "error": false,
          "scanResultBlob": jsonEncode({
            "idScanStatus": "BackSuccessful",
            "isComplete": true
          })
        };
      } else {
        print("Unknown scan type received");
        
        // Default response for unknown scan type
        responseData = {
          "success": false,
          "wasProcessed": true,
          "error": true,
          "errorMessage": "Unknown scan type",
          "scanResultBlob": jsonEncode({
            "idScanStatus": "Failed"
          })
        };
      }
      
      print("Sending scan result to native side: ${jsonEncode(responseData)}");
      success = responseData["success"] == true && responseData["error"] != true;
      
      // Return the response to the native side via method channel
      return responseData;
      
    } catch (e) {
      print("Error processing ID scan result: $e");
      // Handle errors by returning an error response
      return {
        "success": false,
        "wasProcessed": true,
        "error": true,
        "errorMessage": "Error processing scan: $e",
        "scanResultBlob": "{}"
      };
    }
  }

  // Cancel the current scan session
  Future<void> cancelSession() async {
    try {
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
      return "FaceTecSDK/Flutter-ID-Scan";
    }
  }

  // Update the scan result blob
  Future<void> onScanResultBlobReceived(String scanResultBlob) async {
    try {
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
      await _channel.invokeMethod("onScanResultUploadDelay", {
        "uploadMessage": uploadMessage
      });
    } catch (e) {
      print("Error sending upload delay message: $e");
    }
  }
} 
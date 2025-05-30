import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import '../facetec_config.dart';

// This is an example of a self-contained class to perform Liveness checks with the FaceTecSDK.
// You may choose to further componentize parts of this in your own apps based on your specific requirements.
class LivenessCheckProcessor {
  String sessionToken = "dOnJPnRcVtCrh1OPxxPzBpSbfvw3WnL4";
  bool success = false;
  bool isRequestInProgress = false;
  http.Request? latestNetworkRequest;

  static const MethodChannel _channel = MethodChannel('com.facetec.sdk/livenesscheck');

  LivenessCheckProcessor() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    // Handle incoming calls from native code, either Android or iOS.
    switch (call.method) {
      case 'processSession': {

        await processSession(
          call.arguments['status'],
          call.arguments['lowQualityAuditTrailCompressedBase64'],
          call.arguments['auditTrailCompressedBase64'],
          call.arguments['faceScanBase64'],
          call.arguments['sessionId'],
          call.arguments['ftUserAgentString']
          );
        break;
      }
      default:
        break;
    }
  }

  //
  // Part 2: Handle the result of a FaceScan
  //
  processSession(
    String status,
    String lowQualityAuditTrailCompressedBase64,
    String auditTrailCompressedBase64,
    String faceScanBase64,
    String sessionId,
    String ftUserAgentString) async {
      //
      // Part 3:  Handles early exit scenarios where there is no FaceScan to handle -- i.e. User Cancellation, Timeouts, etc.
      //
      if (status != 'sessionCompletedSuccessfully') {
        print("Session was not completed successfully, canceling.");
        await _channel.invokeMethod("cancelFaceScan", {});
        return;
      }

      // IMPORTANT:  FaceTecSDK.FaceTecSessionStatus.SessionCompletedSuccessfully DOES NOT mean the Liveness Check was Successful.
      // It simply means the User completed the Session and a 3D FaceScan was created.  You still need to perform the Liveness Check on your Servers.
      //
      // Part 4:  Get essential data off the FaceTecSessionResult
      //
      final parameters = {
        "faceScan" : faceScanBase64,
        "auditTrailImage" : auditTrailCompressedBase64,
        "lowQualityAuditTrailImage" : lowQualityAuditTrailCompressedBase64,
      };

      //
      // Part 5:  Make the Networking Call to Your Servers.  Below is just example code, you are free to customize based on how your own API works.
      //
      final uri = Uri.parse('${FaceTecConfig.baseURL}/liveness-3d');
      final request = http.Request('POST', uri)
        ..headers['Content-Type'] = 'application/json'
        ..headers['X-Device-Key'] = FaceTecConfig.deviceKeyIdentifier
        ..headers["User-Agent"] = ftUserAgentString
        ..headers["X-User-Agent"] = ftUserAgentString
        ..body = jsonEncode(parameters);

      isRequestInProgress = true;

      //
      // Part 6: Actually send the request
      //
      final response = await http.Client().send(request);

      // Part 7: Handle the response
      response.stream.transform(utf8.decoder).listen((value) async {
        final responseJSON = jsonDecode(value);

        if (responseJSON['error'] == true) {
          // CASE:  Parsing the response into JSON failed --> 
          // You define your own API contracts with yourself and may choose to do something different here based on the error. 
          // Solid server-side code should ensure you don't get to this case.
          print("Error while processing FaceScan: ${responseJSON['errorMessage']}");
          await _channel.invokeMethod("cancelFaceScan", {});
          return;
        }

        // In v9.2.0+, simply pass in scanResultBlob to the proceedToNextStep function to advance the User flow.
        // scanResultBlob is a proprietary, encrypted blob that controls the logic for what happens next for the User.
        if (responseJSON['scanResultBlob'] != null && responseJSON['wasProcessed'] == true) {
          final scanResultBlob = responseJSON['scanResultBlob'];
          await _channel.invokeMethod("onScanResultBlobReceived", {"scanResultBlob": scanResultBlob});
          success = true;
        }
        else { 
          // CASE:  UNEXPECTED response from API.  Our Sample Code keys off a wasProcessed boolean on the root of the JSON object --> 
          // You define your own API contracts with yourself and may choose to do something different here based on the error.
          await _channel.invokeMethod("cancelFaceScan", {});
        }
      }).onDone(() {
        isRequestInProgress = false;
      });

      // Part 8: For better UX, update the User if the upload is take a while.
      // You are free to customize and enhance this behavior to your liking.
      Future.delayed(const Duration(seconds: 6), () async {
        if (!isRequestInProgress) {
          return;
        }
        const uploadMessage = "Still Uploading...";
        await _channel.invokeMethod("onScanResultUploadDelay", {"uploadMessage": uploadMessage});
      });
  }

  bool isSuccess() {
    return success;
  }
}
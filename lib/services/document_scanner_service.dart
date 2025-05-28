import 'dart:async';
import 'package:flutter/services.dart';

class DocumentScannerService {
  static const MethodChannel _channel = MethodChannel('com.facetec.sdk');
  static const MethodChannel _processorChannel = MethodChannel('com.facetec.sdk/livenesscheck');
  static const MethodChannel _idscannChannel = MethodChannel('com.facetec.sdk/idscann');

  // Singleton instance
  static final DocumentScannerService _instance = DocumentScannerService._internal();
  factory DocumentScannerService() => _instance;
  DocumentScannerService._internal();

  // Stream controllers for different events
  final _scanResultController = StreamController<Map<String, dynamic>>.broadcast();
  final _errorController = StreamController<String>.broadcast();
  final _progressController = StreamController<double>.broadcast();

  // Getters for streams
  Stream<Map<String, dynamic>> get scanResultStream => _scanResultController.stream;
  Stream<String> get errorStream => _errorController.stream;
  Stream<double> get progressStream => _progressController.stream;

  // Initialize the service
  Future<void> initialize({
    required String deviceKeyIdentifier,
    required String publicFaceScanEncryptionKey,
  }) async {
    try {
      await _channel.invokeMethod('initialize', {
        'deviceKeyIdentifier': deviceKeyIdentifier,
        'publicFaceScanEncryptionKey': publicFaceScanEncryptionKey,
      });
    } catch (e) {
      _errorController.add(e.toString());
      rethrow;
    }
  }

  // Start document scanning process
  Future<void> startDocumentScan(String sessionToken) async {
    try {
      // Set up method call handler for processing results
      _processorChannel.setMethodCallHandler(_handleProcessorCall);
      _idscannChannel.setMethodCallHandler(_handleIdscannCall);

      // Start the scan
      await _channel.invokeMethod('startMatchIdScan', {
        'sessionToken': sessionToken,
      });
    } catch (e) {
      _errorController.add(e.toString());
      rethrow;
    }
  }

  // Handle processor channel calls
  Future<dynamic> _handleProcessorCall(MethodCall call) async {
    switch (call.method) {
      case 'processSession':
        final Map<String, dynamic> args = Map<String, dynamic>.from(call.arguments);
        _scanResultController.add(args);
        return {'scanResultBlob': '{}'}; // Return empty blob for now
      default:
        throw PlatformException(
          code: 'notImplemented',
          message: 'Method ${call.method} not implemented',
        );
    }
  }

  // Handle ID scan channel calls
  Future<dynamic> _handleIdscannCall(MethodCall call) async {
    switch (call.method) {
      case 'processSession':
        final Map<String, dynamic> args = Map<String, dynamic>.from(call.arguments);
        _scanResultController.add(args);
        return {'scanResultBlob': '{}'}; // Return empty blob for now
      default:
        throw PlatformException(
          code: 'notImplemented',
          message: 'Method ${call.method} not implemented',
        );
    }
  }

  // Cancel current scan
  Future<void> cancelScan() async {
    try {
      await _channel.invokeMethod('cancelSession');
    } catch (e) {
      _errorController.add(e.toString());
      rethrow;
    }
  }

  // Dispose resources
  void dispose() {
    _scanResultController.close();
    _errorController.close();
    _progressController.close();
  }
} 
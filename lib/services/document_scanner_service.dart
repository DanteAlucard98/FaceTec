import 'dart:async';
import 'package:flutter/services.dart';
import '../facetec_config.dart';

class DocumentScannerService {
  static const MethodChannel _channel = MethodChannel('com.example.flutter_facetec_sample_app/document_scanner');
  static const MethodChannel _processorChannel = MethodChannel('com.facetec.sdk/livenesscheck');
  static const MethodChannel _idscannChannel = MethodChannel('com.facetec.sdk/idscann');

  // Singleton instance
  static final DocumentScannerService _instance = DocumentScannerService._internal();
  factory DocumentScannerService() => _instance;
  DocumentScannerService._internal();

  // Stream controllers for different events
  final _scanResultController = StreamController<Map<String, dynamic>>();
  final _errorController = StreamController<String>();
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
        'baseURL': FaceTecConfig.baseURL,
      });
    } catch (e) {
      _errorController.add(e.toString());
    }
  }

  // Start document scanning process
  Future<void> startDocumentScan(String sessionToken) async {
    try {
      final result = await _channel.invokeMethod('startDocumentScan', {
        'sessionToken': sessionToken,
      });
      
      if (result is Map<String, dynamic>) {
        _scanResultController.add(result);
      }
    } catch (e) {
      _errorController.add(e.toString());
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
    }
  }

  // Dispose resources
  void dispose() {
    _scanResultController.close();
    _errorController.close();
    _progressController.close();
  }
} 
import 'package:flutter/material.dart';
import '../services/document_scanner_service.dart';
import '../facetec_config.dart';

class DocumentScannerWidget extends StatefulWidget {
  final String sessionToken;
  final Function(Map<String, dynamic>) onScanComplete;
  final Function(String) onError;

  const DocumentScannerWidget({
    Key? key,
    required this.sessionToken,
    required this.onScanComplete,
    required this.onError,
  }) : super(key: key);

  @override
  State<DocumentScannerWidget> createState() => _DocumentScannerWidgetState();
}

class _DocumentScannerWidgetState extends State<DocumentScannerWidget> {
  final DocumentScannerService _scannerService = DocumentScannerService();
  bool _isInitialized = false;
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    _initializeScanner();
  }

  Future<void> _initializeScanner() async {
    try {
      // Initialize with FaceTec credentials from facetec_config.dart
      await _scannerService.initialize(
        deviceKeyIdentifier: FaceTecConfig.deviceKeyIdentifier,
        publicFaceScanEncryptionKey: FaceTecConfig.publicFaceScanEncryptionKey,
      );
      setState(() => _isInitialized = true);
    } catch (e) {
      widget.onError(e.toString());
    }
  }

  Future<void> _startScan() async {
    if (!_isInitialized) return;

    setState(() => _isScanning = true);

    try {
      // Listen to scan results
      _scannerService.scanResultStream.listen((result) {
        widget.onScanComplete(result);
      });

      // Listen to errors
      _scannerService.errorStream.listen((error) {
        widget.onError(error);
      });

      // Start the scan
      await _scannerService.startDocumentScan(widget.sessionToken);
    } catch (e) {
      widget.onError(e.toString());
    } finally {
      setState(() => _isScanning = false);
    }
  }

  @override
  void dispose() {
    _scannerService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (!_isInitialized)
          const CircularProgressIndicator()
        else if (!_isScanning)
          ElevatedButton(
            onPressed: _startScan,
            child: const Text('Escanear Documento'),
          )
        else
          const CircularProgressIndicator(),
      ],
    );
  }
} 
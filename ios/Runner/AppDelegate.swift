import UIKit
import Flutter
import FaceTecSDK

@main
@objc class AppDelegate: FlutterAppDelegate, FaceTecFaceScanProcessorDelegate, FaceTecIDScanProcessorDelegate, URLSessionDelegate {
    //
    // AppDelegate acts as the ViewController for the FaceTec session and implements the processor delegate.
    // As such, it has two required methods, processSessionWhileFaceTecSDKWaits() and
    // onFaceTecSDKCompletelyDone().
    //

    var flutterEngine: FlutterEngine?
    var faceScanResultCallbackRef: FaceTecFaceScanResultCallback? = nil
    var idScanResultCallbackRef: FaceTecIDScanResultCallback? = nil
    var processorChannel: FlutterMethodChannel?
    var idscannChannel: FlutterMethodChannel?
    private var livenessCheckResult: FlutterResult?
    private var idScanResult: FlutterResult?
    private var isBackScan: Bool = false
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        flutterEngine = FlutterEngine(name: "LivenessCheckProcessor")
        flutterEngine?.run()

        let controller: FlutterViewController = window?.rootViewController as! FlutterViewController
        
        // Application() creates processor channels for commmunicating with main.dart and LivenessCheckProcessor.dart.
        // Other processors you may create will be instantiated through another method channel.
        let faceTecSDKChannel = FlutterMethodChannel(name: "com.facetec.sdk", binaryMessenger: controller.binaryMessenger)
        self.processorChannel = FlutterMethodChannel(name: "com.facetec.sdk/livenesscheck", binaryMessenger: flutterEngine?.binaryMessenger ?? controller.binaryMessenger)
        self.idscannChannel = FlutterMethodChannel(name: "com.facetec.sdk/idscann", binaryMessenger: flutterEngine?.binaryMessenger ?? controller.binaryMessenger)
        
        faceTecSDKChannel.setMethodCallHandler(receivedFaceTecSDKMethodCall(call:result:))
        self.processorChannel!.setMethodCallHandler(receivedLivenessCheckProcessorCall(call:result:))
        self.idscannChannel!.setMethodCallHandler(receivedIdScanProcessorCall(call:result:))

        GeneratedPluginRegistrant.register(with: self)
        GeneratedPluginRegistrant.register(with: flutterEngine ?? self)
        
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
    
    private func receivedFaceTecSDKMethodCall(call: FlutterMethodCall, result: @escaping FlutterResult) -> Void {
        // Used to handle calls received over the "com.facetec.sdk" channel.
        // Currently two methods are implemented: initialize and startLivenessCheck.
        // When you make a call in main.dart or another file linked to the "com.facetec.sdk"
        // method channel, it will be received here and you will need to add logic for handling
        // that request.
        switch(call.method) {
        case "initialize":
            guard let args = call.arguments as? Dictionary<String, Any>,
                  let deviceKeyIdentifier = args["deviceKeyIdentifier"] as? String,
                  let faceScanEncryptionKey = args["publicFaceScanEncryptionKey"] as? String
            else {
                return result(FlutterError())
            }
            return initialize(deviceKeyIdentifier: deviceKeyIdentifier, publicFaceScanEncryptionKey: faceScanEncryptionKey, result: result)
        case "startLivenessCheck":
            guard let args = call.arguments as? Dictionary<String, Any>,
                  let sessionToken = args["sessionToken"] as? String
            else {
                return result(FlutterError())
            }
            return startLivenessCheck(sessionToken: sessionToken, result: result);
        case "startIdscann":
            guard let args = call.arguments as? Dictionary<String, Any>,
                  let sessionToken = args["sessionToken"] as? String
            else {
                return result(FlutterError())
            }
            
            // Extraer parámetros adicionales
            let isBackScan = args["isBackScan"] as? Bool ?? false
            let shouldReturnBothSides = args["shouldReturnBothSides"] as? Bool ?? false
            
            return startIdScan(sessionToken: sessionToken, isBackScan: isBackScan, shouldReturnBothSides: shouldReturnBothSides, result: result)
        case "createAPIUserAgentString":
            let data = FaceTec.sdk.createFaceTecAPIUserAgentString("")
            result(data)
        default:
            result(FlutterMethodNotImplemented)
            return
        }
    }

    private func receivedLivenessCheckProcessorCall(call: FlutterMethodCall, result: @escaping FlutterResult) -> Void {
        // Used to handle calls received over "com.facetec.sdk/livenesscheck".
        // Currently there is only one method needed, but your processor code may require
        // more communication between dart and native code. If so, you may want to implement
        // any processor code and then receive the results and handle updating logic or run code on completion here.
        switch(call.method) {
        case "onScanResultBlobReceived":
            let args = call.arguments as? Dictionary<String, Any> ?? nil
            let scanResultBlob = args?["scanResultBlob"] as? String ?? ""
            return onScanResultBlobReceived(scanResultBlob: scanResultBlob)
        case "cancelFaceScan":
            return cancelFaceScan()
        case "onScanResultUploadDelay":
            let args = call.arguments as? Dictionary<String, Any> ?? nil
            let uploadMessage = args?["uploadMessage"] as? String ?? ""
            return onScanResultUploadDelay(uploadMessage: uploadMessage)
        default:
            result(FlutterMethodNotImplemented);
            return;
        }
    }
    
    private func receivedIdScanProcessorCall(call: FlutterMethodCall, result: @escaping FlutterResult) -> Void {
        // Usado para manejar llamadas recibidas a través de "com.facetec.sdk/idscann"
        switch(call.method) {
        case "onScanResultBlobReceived":
            let args = call.arguments as? Dictionary<String, Any> ?? nil
            let scanResultBlob = args?["scanResultBlob"] as? String ?? ""
            return onIdScanResultBlobReceived(scanResultBlob: scanResultBlob)
        case "cancelSession":
            return cancelIdScan()
        default:
            result(FlutterMethodNotImplemented);
            return;
        }
    }

    private func initialize(deviceKeyIdentifier: String, publicFaceScanEncryptionKey: String, result: @escaping FlutterResult) {
        let ftCustomization = FaceTecCustomization()
        ftCustomization.overlayCustomization.brandingImage = UIImage(named: "FaceTec_logo")
        FaceTec.sdk.setCustomization(ftCustomization)
        
        FaceTec.sdk.initializeInDevelopmentMode(deviceKeyIdentifier: deviceKeyIdentifier, faceScanEncryptionKey: publicFaceScanEncryptionKey, completion: { initializationSuccessful in
            if (initializationSuccessful) {
                result(true)
            }
            else {
                let statusStr = FaceTec.sdk.description(for: FaceTec.sdk.getStatus())
                result(FlutterError(code: "InitError", message: statusStr, details: nil))
            }
        })
    }
    
    private func startLivenessCheck(sessionToken: String, result: @escaping FlutterResult) {
        // This is where the FaceTec View Controller is actually called.
        // It will open the FaceTec interface and start the liveness check process. The
        // method processSessionWhileFaceTecSDKWaits() and onFaceTecSDKCompletelyDone() are not explicitly called,
        // but are implicitly called through the FaceTec controller. If you want to implement multiple processors,
        // you would need to keep track of which method was called to instantiate the session (in this case, startLivenessCheck)
        // and having branching logic in the implicitly called methods to handle multiple processors.
        self.livenessCheckResult = result
        let livenessCheckViewController = FaceTec.sdk.createSessionVC(faceScanProcessorDelegate: self, sessionToken: sessionToken)

        let controller: FlutterViewController = self.window?.rootViewController as! FlutterViewController;
        controller.present(livenessCheckViewController, animated: true, completion: nil)
    }
    
    private func startIdScan(sessionToken: String, isBackScan: Bool, shouldReturnBothSides: Bool, result: @escaping FlutterResult) {
        // Almacenar el tipo de escaneo para usarlo en el procesamiento
        self.isBackScan = isBackScan
        self.idScanResult = result
        
        // Personalizar la experiencia de escaneo de ID según el tipo (frontal o trasero)
        let customization = FaceTecCustomization()
        
        if isBackScan {
            // Configuración específica para escaneo trasero
            print("Configuring SDK for back scan")
            customization.idScanCustomization.showSelectionScreenDocumentImage = true
            customization.idScanCustomization.captureScreenBackgroundColor = .white
            // Eliminamos propiedades que pueden no existir
            print("Configurado para escanear el REVERSO de tu ID")
        } else {
            // Configuración específica para escaneo frontal
            print("Configuring SDK for front scan")
            customization.idScanCustomization.showSelectionScreenDocumentImage = true
            customization.idScanCustomization.captureScreenBackgroundColor = .white
            // Eliminamos propiedades que pueden no existir
            print("Configurado para escanear el FRENTE de tu ID")
        }
        
        // Configuración común para ambos tipos de escaneo
        customization.idScanCustomization.buttonBackgroundNormalColor = .black
        customization.idScanCustomization.buttonTextNormalColor = .white
        customization.idScanCustomization.buttonTextHighlightColor = .white
        
        FaceTec.sdk.setCustomization(customization)
        
        // Crear el controlador de vista para el escaneo de ID
        let idScanViewController = FaceTec.sdk.createIDScanVC(idScanProcessorDelegate: self, sessionToken: sessionToken)
        
        // Presentar la vista de escaneo
        let controller: FlutterViewController = self.window?.rootViewController as! FlutterViewController
        controller.present(idScanViewController, animated: true, completion: nil)
    }

    // FaceTecFaceScanProcessorDelegate required method
    func processSessionWhileFaceTecSDKWaits(sessionResult: FaceTecSessionResult, faceScanResultCallback: FaceTecFaceScanResultCallback) {
        // This callback will be called outside the scope of this method, when the scan is received via method channel from Flutter.
        faceScanResultCallbackRef = faceScanResultCallback

        if sessionResult.status != FaceTecSessionStatus.sessionCompletedSuccessfully {
            print("Session was not completed successfully or canceled by the user, cancelling.")
            faceScanResultCallback.onFaceScanResultCancel()
            return
        }

        // Ready arguments to be sent from native code in iOS to Dart files accessed by Flutter.
        let args = [
            "status" : "sessionCompletedSuccessfully",
            "lowQualityAuditTrailCompressedBase64" : sessionResult.lowQualityAuditTrailCompressedBase64?[0] as Any,
            "auditTrailCompressedBase64" : sessionResult.auditTrailCompressedBase64?[0] as Any,
            "faceScanBase64" : sessionResult.faceScanBase64 as Any,
            "sessionId" : sessionResult.sessionId,
            "ftUserAgentString" : FaceTec.sdk.createFaceTecAPIUserAgentString(sessionResult.sessionId)
        ] as [String : Any]

        // Send arguments across the com.facetec.sdk/livenesscheck method channel, to the Liveness Check Processor code.
        // New processors you add would invoke a different method or communicate across a different channel. Therefore
        // any processor decisions you make should be tracked and branching logic should occur here.
        self.processorChannel?.invokeMethod("processSession",
                                                   arguments:args)
        
    }
    
    // FaceTecIDScanProcessorDelegate required method
    func processIDScanWhileFaceTecSDKWaits(idScanResult: FaceTecIDScanResult, idScanResultCallback: FaceTecIDScanResultCallback) {
        // Almacenar el callback para usar fuera del ámbito de este método
        idScanResultCallbackRef = idScanResultCallback

        if idScanResult.status != .success {
            print("ID Scan was not completed successfully, status: \(idScanResult.status)")
            idScanResultCallback.onIDScanResultCancel()
            return
        }

        // Preparar argumentos para enviar a Flutter
        var args: [String: Any] = [
            "status": "sessionCompletedSuccessfully",
            "sessionId": idScanResult.sessionId,
            "scanType": "idScan",
            "success": true
        ]
        
        // Comprobar si tenemos imágenes del escaneo
        let hasFrontImage = idScanResult.frontImages?.count ?? 0 > 0
        let hasBackImage = idScanResult.backImages?.count ?? 0 > 0
        
        print("ID Scan details - Front image: \(hasFrontImage), Back image: \(hasBackImage), isBackScan: \(isBackScan)")
        
        // Añadir información específica según el tipo de escaneo
        if hasFrontImage {
            args["idScanFrontImage"] = idScanResult.frontImages?[0]
        }
        
        if hasBackImage {
            args["idScanBackImage"] = idScanResult.backImages?[0]
        }
        
        // Establecer flags según el tipo de escaneo
        if isBackScan {
            args["isFrontScan"] = false
            args["isBackScan"] = true
            args["isBackScanRequired"] = false
        } else {
            args["isFrontScan"] = true
            args["isBackScan"] = false
            args["isBackScanRequired"] = true
        }
        
        // Enviar los datos a Flutter
        self.idscannChannel?.invokeMethod("processSession", arguments: args)
        
        // Simular progreso de carga exitoso
        idScanResultCallback.onIDScanUploadProgress(uploadedPercent: 100)
        
        // Crear una respuesta de éxito
        let successResponse = "{\"success\":true,\"data\":{\"idScanStatus\":\"\(isBackScan ? "BackSuccessful" : "FrontSuccessful")\"}}"
        idScanResultCallback.onIDScanResultProceedToNextStep(scanResultBlob: successResponse)
    }

    // FaceTecFaceScanProcessorDelegate method
    func onFaceTecSDKCompletelyDone() {
        //
        // DEVELOPER NOTE:  onFaceTecSDKCompletelyDone() is called after the Session has completed or you signal the FaceTec SDK with cancel().
        // Calling a custom function on the Sample App Controller is done for demonstration purposes to show you that here is where you get control back from the FaceTec SDK.
        //

        // In this case, we don't perform any post-processing, other than resolving a successful session to the Flutter layer.
        livenessCheckResult?("Success")
        livenessCheckResult = nil
        
        idScanResult?("Success")
        idScanResult = nil
        
        print("FaceTecSDK completely done");
    }

    func cancelFaceScan() {
        self.faceScanResultCallbackRef?.onFaceScanResultCancel()
        self.faceScanResultCallbackRef = nil
        livenessCheckResult?(FlutterError(code: "LivenessCheckFailed", message: "Liveness check failed and session was canceled.", details: nil))
        livenessCheckResult = nil
    }
    
    func cancelIdScan() {
        self.idScanResultCallbackRef?.onIDScanResultCancel()
        self.idScanResultCallbackRef = nil
        idScanResult?(FlutterError(code: "IDScanFailed", message: "ID scan failed and session was canceled.", details: nil))
        idScanResult = nil
    }

    func onScanResultBlobReceived(scanResultBlob: String) {
        // Handle a successfully received scanResultBlob from the FaceTec API
        self.faceScanResultCallbackRef?.onFaceScanGoToNextStep(scanResultBlob: scanResultBlob)
        self.faceScanResultCallbackRef = nil
    }
    
    func onIdScanResultBlobReceived(scanResultBlob: String) {
        // Manejar un scanResultBlob recibido exitosamente para el escaneo de ID
        self.idScanResultCallbackRef?.onIDScanResultProceedToNextStep(scanResultBlob: scanResultBlob)
        self.idScanResultCallbackRef = nil
    }

    func onScanResultUploadDelay(uploadMessage: String = "") {
        // Handle if there is a long delay in uploading the FaceScan to the server
        self.faceScanResultCallbackRef?.onFaceScanUploadMessageOverride(uploadMessageOverride: NSMutableAttributedString(string: uploadMessage))
    }
}

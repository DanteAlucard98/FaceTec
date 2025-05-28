import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'facetec_config.dart';
import 'package:http/http.dart' as http;
import 'processors/LivenessCheck.dart';
import 'processors/IDScanProcessor.dart';
import 'processors/MatchIdScanProcessor.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LivenessCheckProcessor();
  IDScanProcessor();
  MatchIdScanProcessor();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter FaceTec Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // TRY THIS: Try running your application with "flutter run". You'll see
        // the application has a blue toolbar. Then, without quitting the app,
        // try changing the seedColor in the colorScheme below to Colors.green
        // and then invoke "hot reload" (save your changes or press the "hot
        // reload" button in a Flutter-supported IDE, or press "r" if you used
        // the command line to start the app).
        //
        // Notice that the counter didn't reset back to zero; the application
        // state is not lost during the reload. To reset the state, use hot
        // restart instead.
        //
        // This works for code too, not just values: Most code changes can be
        // tested with just a hot reload.
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'FaceTecSDK Flutter Demo'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> with WidgetsBindingObserver {
  bool _showLoading = true;
  bool _isLivenessEnabled = false;
  bool _isSessionActive = false;

  static const faceTecSDK = MethodChannel('com.facetec.sdk');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _initializeFaceTecSDK());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      // Re-initialize SDK if needed when app comes to foreground
      if (!_isLivenessEnabled && !_showLoading) {
        _initializeFaceTecSDK();
      }
      
      // If we were in the middle of an ID scan session, we need to re-enable UI
      if (_isSessionActive) {
        print("Resuming from paused state, ID scan may be in progress");
      }
    } else if (state == AppLifecycleState.paused) {
      // When paused, do not cancel the session if we're in the middle of an ID scan
      // The SDK handles its own state between screens for ID scanning
      print("App paused, session active: $_isSessionActive");
    }
  }

  Future<void> _cleanupSession() async {
    try {
      await faceTecSDK.invokeMethod("cancelSession");
    } catch (e) {
      print("Error cleaning up session: $e");
    } finally {
      setState(() {
        _isSessionActive = false;
        _isLivenessEnabled = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        // TRY THIS: Try changing the color here to a specific color (to
        // Colors.amber, perhaps?) and trigger a hot reload to see the AppBar
        // change color while the other colors stay the same.
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: Text(widget.title),
      ),
      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: Column(
          // Column is also a layout widget. It takes a list of children and
          // arranges them vertically. By default, it sizes itself to fit its
          // children horizontally, and tries to be as tall as its parent.
          //
          // Column has various properties to control how it sizes itself and
          // how it positions its children. Here we use mainAxisAlignment to
          // center the children vertically; the main axis here is the vertical
          // axis because Columns are vertical (the cross axis would be
          // horizontal).
          //
          // TRY THIS: Invoke "debug painting" (choose the "Toggle Debug Paint"
          // action in the IDE, or press "p" in the console), to see the
          // wireframe for each widget.
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            OutlinedButton(
              onPressed: _isLivenessEnabled
                  ? () {
                      _startLiveness();
                    }
                  : null,
              child: const Text('Start Liveness'),
            ),
            OutlinedButton(
              onPressed: _isLivenessEnabled
                  ? () {
                      _startIdscann();
                    }
                  : null,
              child: const Text('Start ID Scan'),
            ),
            OutlinedButton(
              onPressed: _isLivenessEnabled
                  ? () {
                      _startMatchIdScan();
                    }
                  : null,
              child: const Text('Start Match ID Scan'),
            ),
            Visibility(
                visible: _showLoading,
                child: const Text('Initializing FaceTec SDK...'))
          ],
        ),
      ),
    );
  }

  Future<void> _initializeFaceTecSDK() async {
    try {
      if (FaceTecConfig.deviceKeyIdentifier.isEmpty) {
        return await _showErrorDialog(
            "Config Error", "You must define your deviceKeyIdentifier.");
      }

      await faceTecSDK.invokeMethod("initialize", {
        "deviceKeyIdentifier": FaceTecConfig.deviceKeyIdentifier,
        "publicFaceScanEncryptionKey": FaceTecConfig.publicFaceScanEncryptionKey
      });
      setState(() {
        _showLoading = false;
        _isLivenessEnabled = true;
      });
    } on PlatformException catch (e) {
      await _showErrorDialog("Initialize Error", "${e.code}: ${e.message}");
    }
  }

  Future<String> _getAPIUserAgentString() async {
    final String result = await faceTecSDK.invokeMethod("createAPIUserAgentString");
    return result;
  }

  Future<String?> getSessionToken() async {
    final userAgent = await _getAPIUserAgentString();
    final xUserAgent = await _getAPIUserAgentString();

    final endpoint = Uri.parse('${FaceTecConfig.baseURL}/session-token');
    final request = http.Request('GET', endpoint)
      ..headers['X-Device-Key'] = FaceTecConfig.deviceKeyIdentifier
      ..headers["User-Agent"] = userAgent
      ..headers["X-User-Agent"] = xUserAgent;

    final response = await http.Client().send(request);
    final responseData = await response.stream.toBytes();

    if (responseData.isEmpty) {
      throw Exception("Exception raised while attempting HTTPS call.");
    }
    try {
      final responseJSONObj = jsonDecode(utf8.decode(responseData)) as Map<String, dynamic>;

      if (responseJSONObj['sessionToken'] is String) {
        return responseJSONObj['sessionToken'];
      }
      else {
        throw Exception("Exception raised while attempting HTTPS call.");
      }
    } 
    catch (e) {
      throw Exception("JSON parsing error: $e");
    }
  }

  Future<void> _startLiveness() async {
    setState(() {
      _isLivenessEnabled = false;
      _isSessionActive = true;
    });
    try {
      String sessionToken = await getSessionToken() as String;
      await faceTecSDK
          .invokeMethod("startLivenessCheck", {"sessionToken": sessionToken});
    }
    catch (e) {
      await _showErrorDialog("Error", e.toString());
    }
    finally {
      setState(() {
        _isSessionActive = false;
        _isLivenessEnabled = true;
      });
    }
  }

  Future<void> _startIdscann() async {
    setState(() {
      _isLivenessEnabled = false;
      _isSessionActive = true;
    });
    try {
      final sessionToken = await getSessionToken();
      if (sessionToken == null) {
        throw Exception("Failed to get session token");
      }

      // Show a dialog explaining the ID scan process
      await _showInfoDialog("ID Scan Instructions", 
          "Please prepare your ID document. You will need to scan both the front and back of your ID.\n\n" +
          "Ensure good lighting and hold your ID steady during the scan.");

      // Start the ID scan session
      await faceTecSDK.invokeMethod('startIdscann', {
        'sessionToken': sessionToken
      });

      // The session is now managed by the SDK until it completes
    } catch (e) {
      print("Error in ID scan: $e");
      await _showErrorDialog("ID Scan Error", e.toString());
    } finally {
      // Reset state after session completes
      setState(() {
        _isLivenessEnabled = true;
        _isSessionActive = false;
      });
    }
  }

  Future<void> _startMatchIdScan() async {
    setState(() {
      _isLivenessEnabled = false;
      _isSessionActive = true;
    });
    try {
      final sessionToken = await getSessionToken();
      if (sessionToken == null) {
        throw Exception("Failed to get session token");
      }

      // Show a dialog explaining the match ID scan process
      await _showInfoDialog("Match ID Scan Instructions", 
          "Please prepare your ID document and follow these steps:\n\n" +
          "1. First, scan your ID document (front and back)\n" +
          "2. Then, take a selfie for face matching\n\n" +
          "Ensure good lighting and hold your ID steady during the scan.");

      // Start the match ID scan session
      await faceTecSDK.invokeMethod('startMatchIdScan', {
        'sessionToken': sessionToken
      });

    } catch (e) {
      print("Error in match ID scan: $e");
      await _showErrorDialog("Match ID Scan Error", e.toString());
    } finally {
      setState(() {
        _isLivenessEnabled = true;
        _isSessionActive = false;
      });
    }
  }

  Future<void> _showErrorDialog(String errorTitle, String errorMessage) async {
    return showDialog(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text(errorTitle),
            content: Text(errorMessage),
            actions: <Widget>[
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                },
                child: const Text("Dismiss"),
              )
            ],
          );
        });
  }

  Future<void> _showInfoDialog(String infoTitle, String infoMessage) async {
    return showDialog(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text(infoTitle),
            content: Text(infoMessage),
            actions: <Widget>[
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                },
                child: const Text("OK"),
              )
            ],
          );
        });
  }
}

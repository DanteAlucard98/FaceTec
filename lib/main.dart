import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'facetec_config.dart';
import 'package:http/http.dart' as http;

void main() {
  WidgetsFlutterBinding.ensureInitialized();                              
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

class _MyHomePageState extends State<MyHomePage> {
  bool _showLoading = true;
  bool _isLivenessEnabled = false;

  static const platform = MethodChannel('com.facetec.sdk');
  static const processorChannel = MethodChannel('com.facetec.sdk/livenesscheck');
  static const photoIDMatchChannel = MethodChannel('com.facetec.sdk/photo_id_match');

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
            ElevatedButton(
              onPressed: () async {
                try {
                  final String? sessionToken = await getSessionToken();
                  if (sessionToken != null) {
                    await platform.invokeMethod('startLivenessCheck', {
                      'sessionToken': sessionToken,
                    });
                  }
                } catch (e) {
                  print('Error: $e');
                }
              },
              child: Text('Start Liveness Check'),
            ),
            SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                try {
                  final String? sessionToken = await getSessionToken();
                  if (sessionToken != null) {
                    await platform.invokeMethod('startPhotoIDMatch', {
                      'sessionToken': sessionToken,
                      'sessionType': 'photo-id-match',
                    });
                  }
                } catch (e) {
                  print('Error: $e');
                }
              },
              child: Text('Start Photo ID Match'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _initializeFaceTecSDK());
  }

  Future<void> _initializeFaceTecSDK() async {
    try {
      if (FaceTecConfig.deviceKeyIdentifier.isEmpty) {
        return await _showErrorDialog(
            "Config Error", "You must define your deviceKeyIdentifier.");
      }

      await platform.invokeMethod("initialize", {
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
    final String result = await platform.invokeMethod("createAPIUserAgentString");
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
}

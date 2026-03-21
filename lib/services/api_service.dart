import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import '../models/app_config.dart';

class ApiService {
  Dio _dio;
  String _serverUrl;

  ApiService({String serverUrl = 'http://10.0.2.2:8000'})
    : _serverUrl = serverUrl,
      _dio = _createDio(serverUrl);

  static Dio _createDio(String baseUrl) {
    final dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 60),
        receiveTimeout: const Duration(seconds: 60),
      ),
    );
    dio.interceptors.add(
      LogInterceptor(
        requestBody: true,
        requestHeader: true,
        responseBody: true,
        responseHeader: true,
        error: true,
      ),
    );
    return dio;
  }

  void updateServerUrl(String serverUrl) {
    _serverUrl = serverUrl;
    _dio = _createDio(serverUrl);
    debugPrint('=== API Server URL updated: $serverUrl ===');
  }

  String get serverUrl => _serverUrl;

  Future<List<GoModel>> getModels() async {
    try {
      debugPrint('=== API: GET /api/models ===');
      debugPrint('=== Base URL: $_serverUrl ===');
      final response = await _dio.get('/api/models');
      debugPrint('=== API Response: ${response.data} ===');
      final List<dynamic> data = response.data;
      return data.map((e) => GoModel.fromJson(e)).toList();
    } on DioException catch (e) {
      debugPrint('=== API Error: ${e.message} ===');
      throw _handleError(e);
    }
  }

  Future<AnalyzeResult> analyzeBoard({
    required Uint8List imageBytes,
    required String modelId,
    String sideToPlay = 'B',
    int size = 19,
    String mode = 'normal',
  }) async {
    try {
      debugPrint('=== API: POST /api/analyze ===');
      debugPrint('=== Base URL: $_serverUrl ===');
      debugPrint('=== modelId: $modelId ===');
      debugPrint('=== sideToPlay: $sideToPlay ===');
      debugPrint('=== imageBytes length: ${imageBytes.length} ===');

      final formData = FormData.fromMap({
        'image': MultipartFile.fromBytes(imageBytes, filename: 'board.jpg'),
        'model_id': modelId,
        'side_to_play': sideToPlay,
        'size': size,
        'mode': mode,
      });

      final response = await _dio.post('/api/analyze', data: formData);

      debugPrint('=== API Response: ${response.statusCode} ===');
      return AnalyzeResult.fromJson(response.data);
    } on DioException catch (e) {
      debugPrint('=== API Error: ${e.type} - ${e.message} ===');
      debugPrint('=== Response: ${e.response?.data} ===');
      throw _handleError(e);
    }
  }

  Future<AnalyzeResult> analyzeBoardFromBase64({
    required String base64Image,
    required String modelId,
    String sideToPlay = 'B',
    int size = 19,
    String mode = 'normal',
  }) async {
    debugPrint('=== analyzeBoardFromBase64 ===');
    debugPrint('=== modelId: $modelId ===');

    if (modelId.isEmpty) {
      throw Exception('modelId 不能为空，请先选择模型');
    }

    final imageBytes = base64Decode(base64Image);
    return analyzeBoard(
      imageBytes: imageBytes,
      modelId: modelId,
      sideToPlay: sideToPlay,
      size: size,
      mode: mode,
    );
  }

  Exception _handleError(DioException error) {
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
        return Exception('连接超时');
      case DioExceptionType.sendTimeout:
        return Exception('发送超时');
      case DioExceptionType.receiveTimeout:
        return Exception('接收超时');
      case DioExceptionType.badResponse:
        final statusCode = error.response?.statusCode;
        final message = error.response?.data?['detail'] ?? '服务器错误';
        return Exception('服务器错误 ($statusCode): $message');
      case DioExceptionType.connectionError:
        return Exception('无法连接到服务器，请确保后端服务已启动 ($_serverUrl)');
      default:
        return Exception('网络错误: ${error.message}');
    }
  }
}

void debugPrint(String message) {
  print('[GoAI] $message');
}

class AnalyzeResult {
  final int boardSize;
  final String sideToPlay;
  final List<Recommendation> recommendations;

  AnalyzeResult({
    required this.boardSize,
    required this.sideToPlay,
    required this.recommendations,
  });

  factory AnalyzeResult.fromJson(Map<String, dynamic> json) {
    return AnalyzeResult(
      boardSize: json['board_size'] ?? 19,
      sideToPlay: json['side_to_play'] ?? 'B',
      recommendations:
          (json['recommendations'] as List?)
              ?.map((e) => Recommendation.fromJson(e))
              .toList() ??
          [],
    );
  }

  List<Map<String, dynamic>> toScreenPoints() {
    return recommendations.asMap().entries.map((entry) {
      final index = entry.key;
      final rec = entry.value;
      final x = _letterToX(rec.x);
      final y = _numberToY(rec.y, boardSize);
      return {
        'x': x,
        'y': y,
        'label': '${index + 1}',
        'winRate': rec.winRate,
        'score': rec.score,
        'coord': '${rec.x}${rec.y}',
      };
    }).toList();
  }

  double _letterToX(String letter) {
    const letters = 'ABCDEFGHJKLMNOPQRST';
    final index = letters.indexOf(letter.toUpperCase());
    if (index == -1) return 0.5;
    return (index + 1) / 19.0;
  }

  double _numberToY(int number, int boardSize) {
    return (boardSize - number + 1) / boardSize.toDouble();
  }
}

class Recommendation {
  final String x;
  final int y;
  final double winRate;
  final double score;
  final int visits;
  final String moveSgf;

  Recommendation({
    required this.x,
    required this.y,
    required this.winRate,
    required this.score,
    required this.visits,
    required this.moveSgf,
  });

  factory Recommendation.fromJson(Map<String, dynamic> json) {
    return Recommendation(
      x: json['x'] ?? 'A',
      y: json['y'] ?? 1,
      winRate: (json['win_rate'] ?? 0.0).toDouble(),
      score: (json['score'] ?? 0.0).toDouble(),
      visits: json['visits'] ?? 0,
      moveSgf: json['move_sgf'] ?? '',
    );
  }
}

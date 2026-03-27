import 'package:flutter/foundation.dart';
import '../models/app_config.dart';
import '../services/storage_service.dart';
import '../services/api_service.dart';

class ConfigProvider extends ChangeNotifier {
  final StorageService _storageService;
  final ApiService _apiService;

  AppConfig _config = AppConfig();
  List<GoModel> _models = [];
  bool _isLoading = true;
  bool _isLoadingModels = false;
  String? _error;

  ConfigProvider(this._storageService, this._apiService) {
    _loadConfig();
  }

  AppConfig get config => _config;
  List<GoModel> get models => _models;
  bool get isLoading => _isLoading;
  bool get isLoadingModels => _isLoadingModels;
  String? get error => _error;
  GoModel? get selectedModel => _models.isNotEmpty
      ? _models.where((m) => m.id == _config.modelId).firstOrNull
      : null;

  Future<void> _loadConfig() async {
    _config = await _storageService.loadConfig();
    _isLoading = false;

    if (_config.serverUrl.isNotEmpty) {
      _apiService.updateServerUrl(_config.serverUrl);
    }

    notifyListeners();
    await _loadModels();
  }

  Future<void> _loadModels() async {
    _isLoadingModels = true;
    _error = null;
    notifyListeners();

    try {
      _models = await _apiService.getModels();

      if (_config.modelId.isEmpty && _models.isNotEmpty) {
        _config = _config.copyWith(
          modelId: _models.first.id,
          modelName: _models.first.name,
        );
        await _storageService.saveConfig(_config);
      }
    } catch (e) {
      _error = _formatError(e.toString());
    }

    _isLoadingModels = false;
    notifyListeners();
  }

  String _formatError(String error) {
    if (error.contains('SocketException') ||
        error.contains('Connection refused')) {
      return '无法连接到服务器，请检查订阅地址是否正确';
    } else if (error.contains('TimeoutException') ||
        error.contains('timeout')) {
      return '连接超时，请检查网络或服务器状态';
    } else if (error.contains('404')) {
      return '接口不存在，请检查订阅地址格式';
    } else if (error.contains('FormatException') || error.contains('Invalid')) {
      return '订阅地址格式错误，请输入正确的URL';
    }
    return '获取模型失败: $error';
  }

  Future<void> refreshModels() async {
    await _loadModels();
  }

  Future<void> refreshModelsIfNotEmpty() async {
    _isLoadingModels = true;
    _error = null;
    notifyListeners();

    try {
      final newModels = await _apiService.getModels();

      if (newModels.isNotEmpty) {
        _models = newModels;

        if (_config.modelId.isEmpty && _models.isNotEmpty) {
          _config = _config.copyWith(
            modelId: _models.first.id,
            modelName: _models.first.name,
          );
          await _storageService.saveConfig(_config);
        }
      }
    } catch (e) {
      _error = _formatError(e.toString());
    }

    _isLoadingModels = false;
    notifyListeners();
  }

  Future<void> setSide(String side) async {
    _config = _config.copyWith(side: side);
    await _storageService.saveConfig(_config);
    notifyListeners();
  }

  Future<void> setModel(GoModel model) async {
    _config = _config.copyWith(modelId: model.id, modelName: model.name);
    await _storageService.saveConfig(_config);
    notifyListeners();
  }

  Future<void> setServerUrl(String serverUrl) async {
    _config = _config.copyWith(serverUrl: serverUrl);
    await _storageService.saveConfig(_config);
    _apiService.updateServerUrl(serverUrl);
    _error = null;
    notifyListeners();
    await _loadModels();
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}

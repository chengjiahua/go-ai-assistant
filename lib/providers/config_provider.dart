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
      _error = e.toString();
    }

    _isLoadingModels = false;
    notifyListeners();
  }

  Future<void> refreshModels() async {
    await _loadModels();
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
    notifyListeners();
    await _loadModels();
  }
}

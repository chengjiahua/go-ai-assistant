import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import '../models/app_config.dart';

class StorageService {
  static const String _configKey = 'app_config';

  Future<AppConfig> loadConfig() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_configKey);
    if (jsonString != null) {
      return AppConfig.fromJson(json.decode(jsonString));
    }
    return AppConfig();
  }

  Future<void> saveConfig(AppConfig config) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_configKey, json.encode(config.toJson()));
  }
}

import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../providers/config_provider.dart';
import '../services/api_service.dart' hide debugPrint;
import '../models/app_config.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with TickerProviderStateMixin {
  bool _showSettings = false;
  bool _isAnalyzing = false;
  bool _hasOverlayPermission = false;
  bool _hasCapturePermission = false;
  bool _isFloatingServiceRunning = false;

  final ApiService _apiService = ApiService();

  List<Map<String, dynamic>> _analysisPoints = [];
  String _statusMessage = '';
  bool _showResults = false;

  static const platform = MethodChannel('com.goai.go_ai_assistant/floating');

  @override
  void initState() {
    super.initState();
    _checkPermissions();
    _setupMethodCallHandler();
  }

  Future<void> _checkPermissions() async {
    try {
      final hasOverlay = await platform.invokeMethod('checkOverlayPermission');
      final hasCapture = await platform.invokeMethod('checkCapturePermission');
      setState(() {
        _hasOverlayPermission = hasOverlay as bool;
        _hasCapturePermission = hasCapture as bool;
      });
    } catch (e) {
      debugPrint('[GoAI] checkPermissions error: $e');
    }
  }

  void _setupMethodCallHandler() {
    platform.setMethodCallHandler((call) async {
      debugPrint('[GoAI] 收到原生调用: ${call.method}');

      switch (call.method) {
        case 'startAnalyze':
          _startAnalyze();
          break;
        case 'analyzeFromFloating':
          return await _handleAnalyzeFromFloating(call.arguments);
      }
      return null;
    });
  }

  Future<String> _handleAnalyzeFromFloating(dynamic arguments) async {
    try {
      final args = arguments as Map<dynamic, dynamic>;
      final base64Image = args['base64Image'] as String;
      final side = args['side'] as String;

      final config = context.read<ConfigProvider>().config;
      final modelId = config.modelId;

      debugPrint('[GoAI] 悬浮窗分析请求: modelId=$modelId, side=$side');

      if (modelId.isEmpty) {
        throw Exception('请先选择模型');
      }

      _apiService.updateServerUrl(config.serverUrl);

      final result = await _apiService.analyzeBoardFromBase64(
        base64Image: base64Image,
        modelId: modelId,
        sideToPlay: side,
      );

      final resultJson = jsonEncode({
        'board_size': result.boardSize,
        'side_to_play': result.sideToPlay,
        'recommendations': result.recommendations
            .map(
              (r) => {
                'x': r.x,
                'y': r.y,
                'win_rate': r.winRate,
                'score': r.score,
                'visits': r.visits,
                'move_sgf': r.moveSgf,
              },
            )
            .toList(),
      });

      debugPrint('[GoAI] 分析完成，返回结果');
      return resultJson;
    } catch (e) {
      debugPrint('[GoAI] 悬浮窗分析失败: $e');
      rethrow;
    }
  }

  Future<void> _requestOverlayPermission() async {
    try {
      final result = await platform.invokeMethod('requestOverlayPermission');
      if (result == true) {
        setState(() {
          _hasOverlayPermission = true;
        });
      }
    } catch (e) {
      debugPrint('[GoAI] requestOverlayPermission error: $e');
    }
  }

  Future<void> _requestCapturePermission() async {
    try {
      final result = await platform.invokeMethod('requestCapturePermission');
      if (result == true) {
        setState(() {
          _hasCapturePermission = true;
        });
      }
    } catch (e) {
      debugPrint('[GoAI] requestCapturePermission error: $e');
    }
  }

  Future<void> _startFloatingService() async {
    try {
      if (!_hasOverlayPermission) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('请先开启悬浮窗权限')));
        }
        return;
      }

      final config = context.read<ConfigProvider>();

      if (config.config.modelId.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('请先在设置中选择模型')));
        }
        return;
      }

      final models = config.models
          .map((m) => {'id': m.id, 'name': m.displayName})
          .toList();

      await platform.invokeMethod('startFloatingService', {
        'serverUrl': config.config.serverUrl,
        'modelId': config.config.modelId,
        'side': config.config.side,
        'models': models,
      });

      setState(() {
        _isFloatingServiceRunning = true;
      });
    } catch (e) {
      debugPrint('[GoAI] startFloatingService error: $e');
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('启动悬浮窗失败: $e')));
      }
    }
  }

  Future<void> _stopFloatingService() async {
    try {
      await platform.invokeMethod('stopFloatingService');
      setState(() {
        _isFloatingServiceRunning = false;
      });
    } catch (e) {
      debugPrint('[GoAI] stopFloatingService error: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF020617),
      body: Stack(
        children: [
          _buildMainContent(),
          if (_showSettings) _buildSettingsModal(),
          if (_isAnalyzing) _buildLoadingOverlay(),
          if (_showResults) _buildResultOverlay(),
        ],
      ),
    );
  }

  Widget _buildMainContent() {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF0f172a),
        borderRadius: BorderRadius.all(Radius.circular(42)),
      ),
      margin: const EdgeInsets.all(12),
      child: ClipRRect(
        borderRadius: const BorderRadius.all(Radius.circular(42)),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 48),
            child: Column(
              children: [
                _buildHeader(),
                const SizedBox(height: 40),
                _buildStatusSection(),
                const Spacer(),
                _buildFooterAction(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Text(
                  'AI ',
                  style: TextStyle(
                    fontSize: 36,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -1,
                    color: Colors.white,
                  ),
                ),
                Text(
                  'VISION',
                  style: TextStyle(
                    fontSize: 36,
                    fontWeight: FontWeight.w300,
                    letterSpacing: -1,
                    color: Color(0xFF3b82f6),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 2),
            Text(
              'GO ANALYSIS ASSISTANT',
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey[500],
                fontWeight: FontWeight.w600,
                letterSpacing: 1,
              ),
            ),
          ],
        ),
        GestureDetector(
          onTap: () => _toggleSettings(true),
          child: Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: const Color(0xFF1e293b),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Icon(Icons.settings, color: Colors.white, size: 24),
          ),
        ),
      ],
    );
  }

  Widget _buildStatusSection() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: const Color(0xFF161e2e),
        borderRadius: BorderRadius.circular(32),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '系统权限状态',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[600],
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 20),
          _buildPermissionRow(
            title: '悬浮窗展示权限',
            isGranted: _hasOverlayPermission,
            onTap: _hasOverlayPermission ? null : _requestOverlayPermission,
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionRow({
    required String title,
    required bool isGranted,
    VoidCallback? onTap,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
      decoration: BoxDecoration(
        color: const Color(0xFF0f172a),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isGranted ? Colors.transparent : const Color(0x33ef4444),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isGranted
                  ? const Color(0xFF22c55e)
                  : const Color(0xFFef4444),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: Colors.white,
              ),
            ),
          ),
          GestureDetector(
            onTap: onTap,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                color: isGranted
                    ? const Color(0xFF1e293b)
                    : const Color(0xFF2563eb),
                borderRadius: BorderRadius.circular(100),
                boxShadow: isGranted
                    ? null
                    : [
                        BoxShadow(
                          color: const Color(0x662563eb),
                          blurRadius: 15,
                          offset: const Offset(0, 4),
                        ),
                      ],
              ),
              child: Text(
                isGranted ? '已授权' : '立即开启',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  color: isGranted ? const Color(0xFF94a3b8) : Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFooterAction() {
    final isRunning = _isFloatingServiceRunning;

    return Column(
      children: [
        GestureDetector(
          onTap: isRunning ? _stopFloatingService : _startFloatingService,
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: const Color(0xFF161e2e),
              borderRadius: BorderRadius.circular(100),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.5),
                  blurRadius: 10,
                  offset: const Offset(0, 2),
                  spreadRadius: 0,
                ),
              ],
            ),
            child: Text(
              isRunning ? 'RUNNING' : 'START SERVICE',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w900,
                letterSpacing: 2,
                color: isRunning
                    ? const Color(0xFF22c55e)
                    : const Color(0xFF2563eb),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (isRunning)
          Text(
            'Service is running, switch to other apps to use',
            style: TextStyle(
              fontSize: 12,
              color: const Color(0xFF22c55e).withValues(alpha: 0.9),
              fontStyle: FontStyle.italic,
              fontWeight: FontWeight.w500,
            ),
          ),
      ],
    );
  }

  Widget _buildSettingsModal() {
    return GestureDetector(
      onTap: () => _toggleSettings(false),
      child: Container(
        color: Colors.black.withValues(alpha: 0.7),
        child: Align(
          alignment: Alignment.bottomCenter,
          child: GestureDetector(
            onTap: () {},
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(32, 40, 32, 40),
              decoration: const BoxDecoration(
                color: Color(0xFF0f172a),
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(40),
                  topRight: Radius.circular(40),
                ),
              ),
              child: Consumer<ConfigProvider>(
                builder: (context, provider, child) {
                  return Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            '设置',
                            style: TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                          GestureDetector(
                            onTap: () => _toggleSettings(false),
                            child: const Icon(
                              Icons.close,
                              color: Colors.white54,
                              size: 28,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      _buildSettingsField(
                        label: '订阅地址',
                        value: provider.config.serverUrl,
                        onChanged: (value) {
                          if (value.isNotEmpty) {
                            provider.setServerUrl(value.trim());
                          }
                        },
                      ),
                      const SizedBox(height: 16),
                      _buildSettingsDropdown(
                        label: '选择模型',
                        value: provider.config.modelId,
                        items: provider.models,
                        onChanged: (value) {
                          if (value != null) {
                            final model = provider.models.firstWhere(
                              (m) => m.id == value,
                            );
                            provider.setModel(model);
                          }
                        },
                        isLoading: provider.isLoadingModels,
                      ),
                      const SizedBox(height: 24),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: () {
                            _toggleSettings(false);
                            if (provider.config.serverUrl.isNotEmpty) {
                              provider.refreshModels();
                            }
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF3b82f6),
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(20),
                            ),
                          ),
                          child: const Text(
                            '确定',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSettingsField({
    required String label,
    required String value,
    required Function(String) onChanged,
  }) {
    final controller = TextEditingController(text: value);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Colors.white70,
          ),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          style: const TextStyle(fontSize: 14, color: Colors.white),
          decoration: InputDecoration(
            hintText: 'http://192.168.x.x:8000',
            hintStyle: TextStyle(
              fontSize: 14,
              color: Colors.white.withValues(alpha: 0.4),
            ),
            filled: true,
            fillColor: const Color(0xFF1e293b),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: const BorderSide(color: Color(0xFF334155)),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: const BorderSide(color: Color(0xFF334155)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: const BorderSide(color: Color(0xFF3b82f6)),
            ),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 18,
              vertical: 18,
            ),
          ),
          onSubmitted: onChanged,
        ),
      ],
    );
  }

  Widget _buildSettingsDropdown({
    required String label,
    required String value,
    required List<GoModel> items,
    required Function(String?) onChanged,
    bool isLoading = false,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              label,
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: Colors.white70,
              ),
            ),
            const Spacer(),
            if (isLoading)
              const SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 18),
          decoration: BoxDecoration(
            color: const Color(0xFF1e293b),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: const Color(0xFF334155)),
          ),
          child: DropdownButton<String>(
            value: (value.isNotEmpty && items.any((item) => item.id == value))
                ? value
                : null,
            isExpanded: true,
            underline: const SizedBox(),
            dropdownColor: const Color(0xFF1e293b),
            style: const TextStyle(fontSize: 14, color: Colors.white),
            hint: const Text(
              '选择模型...',
              style: TextStyle(fontSize: 14, color: Colors.white54),
            ),
            items: items.map((model) {
              return DropdownMenuItem<String>(
                value: model.id,
                child: Text(model.displayName),
              );
            }).toList(),
            onChanged: onChanged,
          ),
        ),
      ],
    );
  }

  Widget _buildLoadingOverlay() {
    return Container(
      color: Colors.black.withValues(alpha: 0.54),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: Colors.white),
            const SizedBox(height: 16),
            Text(
              _statusMessage.isEmpty ? '正在分析...' : _statusMessage,
              style: const TextStyle(color: Colors.white, fontSize: 16),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultOverlay() {
    return GestureDetector(
      onTap: _closeResults,
      child: Container(
        color: Colors.transparent,
        child: Stack(
          children: [
            ..._analysisPoints.asMap().entries.map((entry) {
              final index = entry.key;
              final point = entry.value;
              return _buildResultPoint(
                index: index + 1,
                x: point['x'] as double,
                y: point['y'] as double,
                isTop: index == 0,
              );
            }),
            Positioned(
              bottom: 40,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 8),
                color: Colors.black.withValues(alpha: 0.38),
                child: const Text(
                  '点击屏幕任意位置关闭预览',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 10, color: Colors.white),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultPoint({
    required int index,
    required double x,
    required double y,
    required bool isTop,
  }) {
    final screenSize = MediaQuery.of(context).size;
    final left = x * screenSize.width - 17;
    final top = y * screenSize.height - 17;

    return Positioned(
      left: left,
      top: top,
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0.0, end: 1.0),
        duration: const Duration(milliseconds: 500),
        builder: (context, value, child) {
          return Transform.scale(scale: value, child: child);
        },
        child: Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(
            color: isTop ? Colors.red[500] : Colors.blue[500],
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.white.withValues(alpha: 0.5),
                blurRadius: 15,
              ),
            ],
          ),
          child: Center(
            child: Text(
              '$index',
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: Colors.white,
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _toggleSettings(bool show) {
    setState(() {
      _showSettings = show;
    });
  }

  Future<void> _startAnalyze() async {
    setState(() {
      _isAnalyzing = true;
      _statusMessage = '正在截取屏幕...';
    });

    try {
      final String? base64Image = await platform.invokeMethod('captureScreen');

      if (base64Image == null || base64Image.isEmpty) {
        throw Exception('截图失败，请先授权屏幕录制');
      }

      setState(() {
        _statusMessage = '正在分析棋盘...';
      });

      final config = context.read<ConfigProvider>().config;

      if (config.modelId.isEmpty) {
        throw Exception('请先选择模型');
      }

      _apiService.updateServerUrl(config.serverUrl);

      final result = await _apiService.analyzeBoardFromBase64(
        base64Image: base64Image,
        modelId: config.modelId,
        sideToPlay: config.side,
      );

      setState(() {
        _analysisPoints = result.toScreenPoints();
        _showResults = true;
        _isAnalyzing = false;
        _statusMessage = '';
      });

      if (_analysisPoints.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('未找到推荐落子')));
        }
      }
    } catch (e) {
      setState(() {
        _isAnalyzing = false;
        _statusMessage = '';
      });

      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('分析失败: $e')));
      }
    }
  }

  void _closeResults() {
    setState(() {
      _showResults = false;
    });
  }
}

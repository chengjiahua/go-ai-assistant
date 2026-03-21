# 围棋棋盘识别 API 文档

## 基础信息

- **服务地址**: `http://localhost:8000`
- **数据格式**: JSON
- **图片格式**: 支持 JPG、PNG 等常见图片格式

---

## 识别接口

### 识别棋盘并返回 SGF

**接口地址**: `POST /api/recognize`

**Content-Type**: `multipart/form-data`

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| image | File | 是 | 棋盘图片文件（也支持 `file` 字段名） |
| model_id | String | 是 | 模型ID，从模型列表接口获取 |
| size | Integer | 否 | 棋盘大小，默认 19（支持 9、13、19） |
| mode | String | 否 | 识别模式，默认 `normal` |
| threshold | JSON String | 否 | 自定义阈值，格式见下方说明 |
| side_to_play | String | 否 | 期望下一手执子方（"B"黑子，"W"白子），默认 "B" |

**threshold 参数格式**:
```json
{
  "sat_threshold": 49.3,
  "black_gray_threshold": 94.9,
  "white_gray_threshold": 160.8
}
```

#### 请求示例

**cURL**:
```bash
curl -X POST http://localhost:8000/api/recognize \
  -F "image=@/path/to/board.jpg" \
  -F "model_id=abfa0648"
```

**Python**:
```python
import requests

url = "http://localhost:8000/api/recognize"

with open("/path/to/board.jpg", "rb") as f:
    files = {"image": f}
    data = {"model_id": "abfa0648"}
    response = requests.post(url, files=files, data=data)

result = response.json()
print(result["sgf"])
```

**JavaScript (Fetch)**:
```javascript
const formData = new FormData();
formData.append('image', imageFile);
formData.append('model_id', 'abfa0648');

fetch('http://localhost:8000/api/recognize', {
  method: 'POST',
  body: formData
})
.then(res => res.json())
.then(data => console.log(data.sgf));
```

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| board_size | Integer | 棋盘大小 |
| side_to_play | String | 下一步执子方（"B" 或 "W"） |
| mode | String | 识别模式 |
| stones | Array | 棋子列表 |
| stones[].x | Integer | 棋子 X 坐标（0-18） |
| stones[].y | Integer | 棋子 Y 坐标（0-18） |
| stones[].color | String | 棋子颜色（"B" 黑子，"W" 白子） |
| sgf | String | SGF 格式棋谱字符串 |
| debug_image_url | String | 调试图片 URL（可选） |
| original_image_url | String | 原始图片 URL（可选） |
| board_roi_url | String | 棋盘区域图片 URL（可选） |
| color_info | Object | 颜色信息（可选） |
| gray_values | Object | 各位置灰度值（可选） |

#### 响应示例

```json
{
  "board_size": 19,
  "side_to_play": "B",
  "mode": "normal",
  "stones": [
    {"x": 3, "y": 3, "color": "B"},
    {"x": 15, "y": 3, "color": "W"},
    {"x": 3, "y": 15, "color": "B"}
  ],
  "sgf": "(;GM[1]FF[4]SZ[19]PB[Black]PW[White];B[dd];W[pp];B[pd])",
  "debug_image_url": "/debug/20260321-12-00/20260321_120000_normal_debug.png",
  "original_image_url": "/debug/20260321-12-00/20260321_120000_normal_original.png",
  "board_roi_url": "/debug/20260321-12-00/20260321_120000_normal_board_roi.png"
}
```

---

## 分析接口

### 分析棋盘并返回推荐落子

**接口地址**: `POST /api/analyze`

**Content-Type**: `multipart/form-data`

识别棋盘并调用 KataGo 分析引擎，返回前5手推荐落子。

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| image | File | 是 | 棋盘图片文件（也支持 `file` 字段名） |
| model_id | String | 是 | 模型ID，从模型列表接口获取 |
| side_to_play | String | 否 | 期望下一手执子方（"B"黑子，"W"白子），默认 "B" |
| size | Integer | 否 | 棋盘大小，默认 19（支持 9、13、19） |
| mode | String | 否 | 识别模式，默认 `normal` |

#### 请求示例

**cURL**:
```bash
curl -X POST http://localhost:8000/api/analyze \
  -F "image=@/path/to/board.jpg" \
  -F "model_id=abfa0648" \
  -F "side_to_play=B"
```

**Python**:
```python
import requests

url = "http://localhost:8000/api/analyze"

with open("/path/to/board.jpg", "rb") as f:
    files = {"image": f}
    data = {"model_id": "abfa0648", "side_to_play": "B"}
    response = requests.post(url, files=files, data=data)

result = response.json()
for rec in result["recommendations"]:
    print(f"{rec['x']}{rec['y']}: 胜率 {rec['win_rate']:.1%}")
```

#### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| board_size | Integer | 棋盘大小 |
| side_to_play | String | 下一步执子方（"B" 或 "W"） |
| recommendations | Array | 推荐落子列表（最多5个） |
| recommendations[].x | String | X坐标（A-S，字母） |
| recommendations[].y | Integer | Y坐标（1-19，数字） |
| recommendations[].win_rate | Float | 胜率（0-1） |
| recommendations[].score | Float | 预期目数差 |
| recommendations[].visits | Integer | 搜索次数 |
| recommendations[].move_sgf | String | KataGo原始坐标 |

#### 响应示例

```json
{
  "board_size": 19,
  "side_to_play": "B",
  "recommendations": [
    {
      "x": "D",
      "y": 16,
      "win_rate": 0.52,
      "score": 1.5,
      "visits": 100,
      "move_sgf": "Bdd"
    },
    {
      "x": "Q",
      "y": 16,
      "win_rate": 0.48,
      "score": 0.8,
      "visits": 85,
      "move_sgf": "Bpd"
    }
  ]
}
```

#### 坐标说明

- **X坐标**: A 到 S（从左到右）
- **Y坐标**: 1 到 19（从下到上）
- 例如：`D16` 表示第4列第16行（星位）

---

## 模型管理接口

### 获取模型列表

**接口地址**: `GET /api/models`

#### 响应示例

```json
[
  {
    "id": "abfa0648",
    "name": "小米14-腾讯围棋",
    "board_size": 19,
    "thumbnail": "/models/abfa0648/image.jpg",
    "color_info": {
      "black_mean": 45.5,
      "white_mean": 210.2,
      "empty_sat_mean": 98.7
    },
    "threshold": {
      "sat_threshold": 49.3,
      "black_gray_threshold": 94.9,
      "white_gray_threshold": 160.8
    }
  }
]
```

### 获取模型详情

**接口地址**: `GET /api/models/{id}`

#### 响应示例

```json
{
  "id": "abfa0648",
  "name": "小米14-腾讯围棋",
  "board_size": 19,
  "boundary": {"x": 38, "y": 532, "w": 1128, "h": 1128},
  "color_info": {
    "black_mean": 45.5,
    "white_mean": 210.2,
    "empty_sat_mean": 98.7
  },
  "grid_info": {
    "cell_size": 62.7,
    "stone_radius": 28
  },
  "threshold": {
    "sat_threshold": 49.3,
    "black_gray_threshold": 94.9,
    "white_gray_threshold": 160.8
  }
}
```

### 获取模型名称列表

**接口地址**: `GET /api/models/names`

获取所有模型的 ID 和名称，适用于下拉选择等场景。

#### 响应示例

```json
[
  {
    "id": "abfa0648",
    "name": "小米14-腾讯围棋"
  },
  {
    "id": "8f650994",
    "name": "1"
  }
]
```

---

## SGF 格式说明

返回的 `sgf` 字段是标准 SGF (Smart Game Format) 格式字符串，可直接用于围棋软件。

**SGF 示例**:
```
(;GM[1]FF[4]SZ[19]PB[Black]PW[White];B[dd];W[pp];B[pd])
```

**坐标说明**:
- SGF 使用字母坐标：a=0, b=1, ..., s=18
- 例如 `dd` 表示 (3, 3)，即星位

---

## 错误响应

| HTTP 状态码 | 说明 |
|-------------|------|
| 400 | 请求参数错误 |
| 404 | 模型不存在 |
| 500 | 服务器内部错误 |

**错误响应示例**:
```json
{
  "error": "Model not found"
}
```

---

## 完整调用示例

### Python 完整示例

```python
import requests

BASE_URL = "http://localhost:8000"

def get_models():
    """获取可用模型列表"""
    response = requests.get(f"{BASE_URL}/api/models")
    return response.json()

def recognize_board(image_path, model_id):
    """识别棋盘并返回 SGF"""
    with open(image_path, "rb") as f:
        files = {"image": f}
        data = {"model_id": model_id}
        response = requests.post(f"{BASE_URL}/api/recognize", files=files, data=data)
    
    if response.status_code == 200:
        result = response.json()
        return result["sgf"]
    else:
        raise Exception(f"识别失败: {response.text}")

if __name__ == "__main__":
    models = get_models()
    print(f"可用模型: {[m['name'] for m in models]}")
    
    if models:
        model_id = models[0]["id"]
        sgf = recognize_board("/path/to/board.jpg", model_id)
        print(f"SGF: {sgf}")
```

### Node.js 完整示例

```javascript
const FormData = require('form-data');
const fs = require('fs');
const axios = require('axios');

const BASE_URL = 'http://localhost:8000';

async function getModels() {
  const response = await axios.get(`${BASE_URL}/api/models`);
  return response.data;
}

async function recognizeBoard(imagePath, modelId) {
  const form = new FormData();
  form.append('image', fs.createReadStream(imagePath));
  form.append('model_id', modelId);

  const response = await axios.post(`${BASE_URL}/api/recognize`, form, {
    headers: form.getHeaders()
  });

  return response.data.sgf;
}

(async () => {
  const models = await getModels();
  console.log('可用模型:', models.map(m => m.name));

  if (models.length > 0) {
    const sgf = await recognizeBoard('/path/to/board.jpg', models[0].id);
    console.log('SGF:', sgf);
  }
})();
```

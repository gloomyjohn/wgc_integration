# 接口文档


项目后端基础信息：

- HTTP 基础地址：`https://postconsonantal-tyrell-untactual.ngrok-free.dev`
- 返回统一封装：`Result`

统一响应格式：

```json
{
  "code": "200",
  "message": "success",
  "data": {}
}
```

说明：

- `code = "200"` 表示成功
- `code = "500"` 表示失败
- `data` 为具体业务数据

---

## 1. 推荐前端使用的核心接口

### 1.1 获取司机到乘客的推荐导航路径

- 方法：`POST`
- 路径：`/v1/pathRecommendations/getRec`
- 用途：根据司机位置和乘客位置，返回一条推荐路径

请求体：

```json
{
  "driverLocation": [103.8519, 1.2903],
  "passengerLocation": [103.8585, 1.2976]
}
```

字段说明：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `driverLocation` | `number[]` | 是 | 司机坐标，格式固定为 `[lng, lat]` |
| `passengerLocation` | `number[]` | 是 | 乘客坐标，格式固定为 `[lng, lat]` |

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "bluePath": [
      [103.8519, 1.2903],
      [103.8517392, 1.2896701],
      [103.8524294, 1.2909086],
      [103.8585, 1.2976]
    ],
    "blueSnappedStart": [103.8517392, 1.2896701],
    "redPaths": [],
    "redSnappedStarts": []
  }
}
```

返回字段说明：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `data.bluePath` | `number[][]` | 推荐导航折线，格式为 `[[lng, lat], [lng, lat], ...]` |
| `data.blueSnappedStart` | `number[]` | 司机吸附到路网后的起始点 |
| `data.redPaths` | `number[][][]` | 预留字段，当前为空 |
| `data.redSnappedStarts` | `number[][]` | 预留字段，当前为空 |

前端使用建议：

- 直接用 `data.bluePath` 绘制折线
- 坐标顺序必须按 `[lng, lat]` 解析
- `redPaths` 和 `redSnappedStarts` 当前可忽略

---

### 1.2 更新司机位置

- 方法：`POST`
- 路径：`/v1/drivers/location/update`
- 用途：向后端提交司机当前定位

请求体：

```json
{
  "driverId": 1,
  "latitude": 1.2903,
  "longitude": 103.8519
}
```

字段说明：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `driverId` | `number` | 是 | 司机 ID |
| `latitude` | `number` | 是 | 纬度 |
| `longitude` | `number` | 是 | 经度 |

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": "location update success"
}
```

---

### 1.3 请求候选乘客位置

- 方法：`POST`
- 路径：`/v1/drivers/requestPassenger`
- 用途：根据司机当前位置，请求后端返回附近若干个候选乘客点

请求体：

```json
{
  "driverId": 1,
  "latitude": 1.2903,
  "longitude": 103.8519
}
```

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": [
    {
      "nodeId": 1001,
      "lng": 103.8524,
      "lat": 1.2910
    },
    {
      "nodeId": 1002,
      "lng": 103.8531,
      "lat": 1.2922
    }
  ]
}
```

返回字段说明：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `nodeId` | `number` | 路网节点 ID |
| `lng` | `number` | 经度 |
| `lat` | `number` | 纬度 |

说明：

- 当前实现会在司机周围约 2km 范围内随机取若干真实路网点
- 可用于前端生成或展示候选乘客位置

---

### 1.4 请求竞争司机位置

- 方法：`POST`
- 路径：`/v1/drivers/requestRivalDrivers`
- 用途：根据主司机当前位置，生成若干附近竞争司机

请求体：

```json
{
  "driverId": 1,
  "latitude": 1.2903,
  "longitude": 103.8519
}
```

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": [
    {
      "id": 1,
      "latitude": 1.2921,
      "longitude": 103.8546,
      "nodeId": 2101
    },
    {
      "id": 2,
      "latitude": 1.2888,
      "longitude": 103.8497,
      "nodeId": 2102
    }
  ]
}
```

返回字段说明：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 前端展示用竞争司机序号 |
| `latitude` | `number` | 纬度 |
| `longitude` | `number` | 经度 |
| `nodeId` | `number` | 路网节点 ID |

说明：

- 当前实现会在司机周围约 3km 范围内随机取若干竞争司机点

---

## 2. 可选接口

这些接口可以给前端页面做列表或调试面板使用，但不是主流程必需。

### 2.1 获取司机列表

- 方法：`GET`
- 路径：`/v1/drivers/list`

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": [
    {
      "driverId": 1,
      "currentStatus": "IDLE"
    }
  ]
}
```

---

### 2.2 查询单个司机

- 方法：`POST`
- 路径：`/v1/drivers/query`

请求参数：

表单或对象中至少需要包含：

```json
{
  "driverId": 1
}
```

说明：

- 当前 controller 没有显式加 `@RequestBody`
- 如果前端使用该接口，建议先和后端确认提交方式

---

### 2.3 获取乘客请求列表

- 方法：`POST`
- 路径：`/v1/rideRequests/list`

说明：

- 当前可以作为调试接口查看请求列表
- 不建议直接作为正式前端主流程接口依赖

---

### 2.4 获取一个乘客请求

- 方法：`GET`
- 路径：`/v1/rideRequests/getOne`

说明：

- 当前实现更偏调试用途
- `GET` 却带 `@RequestBody`，不太规范
- 前端不建议依赖这个接口做正式功能

---

## 3. 当前不建议前端直接依赖的接口

下面这类接口虽然存在，但当前更偏后台数据管理或生成器代码，前端一般不需要直接接：

- `/v1/drivers/add`
- `/v1/drivers/delete`
- `/v1/drivers/update`
- `/v1/pathRecommendations/add`
- `/v1/pathRecommendations/delete`
- `/v1/pathRecommendations/update`
- `/v1/pathRecommendations/list`
- `/v1/nodes/*`
- `/v1/driverStatusLocations/*`
- `/v1/forecastRuns/*`
- `/v1/forecastSnapshots/*`
- `/v1/roadSegments/*`
- `/v1/systemParameters/*`
- `/v1/trips/*`
- `/v1/spatialRefSys/*`
- `/v1/geographyColumns/*`
- `/v1/geometryColumns/*`

如果前端后续确实要接这些接口，建议先单独确认字段和用途。

---

## 4. 前端联调建议

推荐优先接这 4 个：

1. `POST /v1/drivers/location/update`
2. `POST /v1/drivers/requestPassenger`
3. `POST /v1/drivers/requestRivalDrivers`
4. `POST /v1/pathRecommendations/getRec`


---

## 5. 特别注意

### 5.1 坐标顺序

路径推荐接口：

- `driverLocation` / `passengerLocation` 使用 `[lng, lat]`

司机位置更新接口：

- 使用独立字段 `latitude`、`longitude`

前端不要把这两种格式混掉。

### 5.2 路径绘制建议

对于 `/v1/pathRecommendations/getRec`：

- 直接绘制 `data.bluePath`
- 起点吸附可使用 `data.blueSnappedStart`
- 当前只需要处理蓝线即可

### 5.3 通用错误处理

当前很多接口失败时返回：

```json
{
  "code": "500",
  "message": "fail",
  "data": "具体错误信息"
}
```

前端建议统一判断：

- `code === "200"` 视为成功
- 否则视为失败并提示 `message` 或 `data`


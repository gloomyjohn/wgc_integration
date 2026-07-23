# 多对多匹配后端方案

## 1. 目标

本方案面向“多司机-多乘客”实时匹配场景，目标是：

- 支持几十到几百规模的司机和乘客并发匹配
- 避免把当前单对单图搜索直接扩展成全量多对多搜索
- 将匹配与寻路解耦，降低整体计算压力
- 保留 flow field 思想，但将其用于引导、候选裁剪和空驶调度，而不是直接替代主匹配算法

核心原则：

- Python 负责多对多匹配
- Java 负责路径规划和业务编排
- PostgreSQL/PostGIS 负责状态存储和空间查询
- flow field 主要用于匹配前引导和候选优化

## 2. 总体架构

### 2.1 服务分工

#### Python Matching Engine

职责：

- 接收当前空闲司机和待匹配乘客的坐标快照
- 基于 `kNN + greedy` 算法完成多对多匹配
- 输出 `driver_id -> passenger_id` 的匹配结果

特点：

- 只做匹配，不做完整路线计算
- 只处理稀疏候选图，不做全量司机乘客对枚举
- 可以复用现有 `wgc-python/matching_algorithm.py` 的实现思路

#### Java Orchestrator / Backend

职责：

- 周期性发起匹配批次
- 从数据库读取空闲司机与待匹配乘客
- 生成或读取 flow field / 热点摘要
- 调用 Python 匹配服务
- 对匹配结果做事务校验并落库
- 为已匹配对异步生成接驾路径

特点：

- 是系统主控层
- 负责一致性和业务状态变更
- 不在匹配阶段做全量图搜索

#### Java Route Planner

职责：

- 对最终匹配成功的司机-乘客对生成接驾路线
- 继续使用现有 `FlowFieldServiceImpl.buildRouteToPassenger()` 的图搜索与 flow-aware 路径规划逻辑

特点：

- 只服务于最终少量已匹配对
- 不参与全量候选对的筛选

#### PostgreSQL + PostGIS

职责：

- 存储司机状态、乘客请求、匹配结果、路线结果
- 提供空间范围筛选、最近路段吸附、状态查询

特点：

- 是系统唯一真实状态源
- 负责空间查询和事务一致性

## 3. 批处理流程

推荐按固定时间窗口运行匹配批次，例如每 `0.5s ~ 2s` 一轮。

### 3.1 一轮匹配的完整流程

1. Java 读取当前活跃快照
2. 只筛选 `idle drivers` 和 `waiting passengers`
3. Java 生成当前 flow field / 热点摘要
4. Java 调用 Python `/match`
5. Python 进行 `kNN + greedy` 匹配
6. Python 返回匹配结果
7. Java 在事务中重新校验司机和乘客状态
8. Java 提交匹配关系、更新订单和司机状态
9. Java 异步为已匹配对生成接驾路径
10. 未匹配司机继续进入下一轮调度

### 3.2 为什么要分层

不建议在匹配阶段直接对所有司机乘客对调用路径规划。

原因：

- 复杂度过高
- 路径搜索比空间近邻查询昂贵得多
- 大量候选对最终根本不会成为真实匹配结果

正确做法是：

- 先匹配
- 再对少量匹配成功对进行路径生成

## 4. 匹配算法设计

## 4.1 候选生成：kNN

第一层使用 `kNN` 生成稀疏候选图：

- 在乘客位置上建立 KD-tree
- 对每个司机查询 `top-k` 最近乘客
- 可选增加 `max_dist` 距离阈值

推荐参数：

- `k = 10 ~ 20`
- `max_dist = 1.5km ~ 3km`

这样可以把问题从“全量二部图”转成“稀疏二部图”。

### 4.2 匹配求解：Greedy

在 `top-k` 候选图上做贪心分配：

- 候选更少的司机优先处理
- 在每个司机的距离有序候选列表中，选择第一个未被占用的乘客
- 每个乘客最多只分配给一个司机

优点：

- 简单
- 快
- 易于部署
- 与现有 Python 代码一致

### 4.3 为什么不做全量最优匹配

全量最优算法如匈牙利算法、最小费用最大流在规模上去后成本更高。

在实时打车场景下，更实际的策略是：

- 用 `kNN` 快速裁剪候选空间
- 用 `greedy` 在稀疏图上做近实时分配

这比“追求全局绝对最优”更符合实时性要求。

## 5. flow field 的定位

## 5.1 不作为主匹配算法

在本方案中，flow field 不直接替代 `kNN + greedy`。

主匹配算法仍然是：

- `kNN` 负责候选生成
- `greedy` 负责最终分配

flow field 的角色是“引导和优化”，不是“主求解器”。

### 5.2 flow field 的三个主要用途

#### 1. 空驶司机引导

对于未匹配的空闲司机：

- 不随机巡游
- 而是沿需求热点方向移动

目的：

- 让供给逐步向需求密集区靠拢
- 提高下一轮匹配成功率

#### 2. 候选裁剪

当司机的候选乘客较多时，可以利用 flow field 进行二次筛选：

- 优先保留位于需求主方向上的乘客
- 优先保留处于同热点走廊内的乘客
- 降低明显逆向或偏离热点区域的候选优先级

#### 3. 候选重排序

在 `top-k` 候选中，不一定只按欧氏距离排序。

可以使用轻量级综合分数：

`score = a * distance + b * eta_proxy - c * flow_alignment - d * wait_bonus`

其中：

- `distance`：司机到乘客的空间距离
- `eta_proxy`：接驾时间的轻量估计
- `flow_alignment`：司机所在局部流向与乘客方向的一致性
- `wait_bonus`：乘客等待时长加权

初期建议将 flow field 作为软加分项，而不是硬过滤条件。

## 5.3 与 TSX 文件中的 flow field 的区别

本方案中的 flow field：

- 用于匹配前引导
- 用于候选裁剪与重排序
- 用于空驶司机重定位

`RealtimeTaxiSimulatorProbabilityAware.tsx` 中的 flow field：

- 用于仿真中的实时移动
- 用于路口选边
- 用于局部导航行为和可视化展示

因此二者区别是：

- 本方案在“匹配层”使用 flow field
- TSX 文件在“导航层”使用 flow field

## 6. 路线规划层

### 6.1 路线生成时机

路线规划只在“匹配成功之后”进行。

不建议：

- 对所有候选对都计算路径

建议：

- 只对最终匹配成功对调用 Java 路径规划器

### 6.2 路线生成方式

继续使用现有 Java 路径规划：

- 司机位置吸附到最近路段
- 乘客位置吸附到最近路段
- 基于 flow-aware 图搜索生成接驾路线

这样可以保留现有 `FlowFieldServiceImpl` 的价值，同时避免其被用于全量多对多搜索。

## 7. 数据库设计方向

### 7.1 司机状态

建议司机状态表包含：

- `driver_id`
- `status`
- `current_geom`
- `snapped_segment_id`
- `snapped_node_id`
- `last_update_time`
- `idle_since`
- `cell_id / geohash`

### 7.2 乘客请求

建议乘客请求表包含：

- `request_id`
- `status`
- `pickup_geom`
- `request_time`
- `snapped_segment_id`
- `cell_id / geohash`

### 7.3 匹配结果 / 调度任务

建议增加匹配结果表或任务表：

- `match_batch_id`
- `driver_id`
- `request_id`
- `matched_at`
- `route_status`
- `route_id / recommendation_id`

## 8. 索引与空间查询

既然使用 PostgreSQL/PostGIS，建议重点确认以下索引：

- `GIST(current_geom)`：司机空间查询
- `GIST(pickup_geom)`：乘客空间查询
- `GIST(road_segments.geometry)`：最近路段吸附
- `BTREE(status, last_update_time)`：司机状态筛选
- `BTREE(status, request_time)`：乘客状态筛选

作用：

- 空间索引用于范围过滤和近邻查询
- 状态索引用于每轮批次快速读取活跃对象

## 9. 缓存策略

为保证吞吐量，建议在 Java 侧做缓存：

- 路网邻接结构缓存
- 路段几何缓存
- 最近路段吸附结果缓存
- 当前热点 / flow field 摘要缓存

### 9.1 吸附缓存建议

司机：

- 当位置变化很小时，不重新做最近路段查询

乘客：

- 上车点通常固定，更适合缓存吸附结果

这样可以显著降低数据库空间查询频率。

## 10. 事务一致性

多对多匹配中，最重要的问题之一是避免重复分配。

推荐机制：

1. Python 只返回计算结果
2. Java 落库前重新校验状态
3. 事务中确保：
   - 司机仍然是 `idle`
   - 乘客仍然是 `waiting`
4. 校验通过后再更新匹配结果

推荐使用：

- 条件更新
- 或 `FOR UPDATE SKIP LOCKED`

原则：

- Python 负责算
- Java 负责判和写
- 数据库负责一致性

## 11. 性能分析

### 11.1 不推荐的方式

不推荐做法：

- 对所有司机乘客组合调用一次 Java 图搜索

复杂度近似：

`O(D * P * route_search)`

在司机和乘客数量上去后会非常重。

### 11.2 推荐方式

推荐做法：

- 候选生成：`kNN`
- 分配：`greedy`
- 路线生成：只对匹配成功对执行

复杂度可近似理解为：

- 候选生成：`O((D + P) log P)`
- 匹配：`O(D * k)`
- 路线生成：`O(matched_pairs * route_search)`

相比全量多对多图搜索，这个复杂度更可控。

## 12. 推荐实施顺序

### 阶段一

- 直接复用 Python `kNN + greedy`
- Java 只对最终匹配对生成路线
- flow field 只用于空驶司机重定位

### 阶段二

- 在候选重排序中加入热点与流向一致性
- 将 flow field 作为软打分项引入

### 阶段三

- 在 `top-k` 内加入轻量 ETA proxy
- 对候选做更精细排序

### 阶段四

- 按网格、区域、Geohash 分桶并行匹配
- 支撑更大规模的司机乘客快照

## 13. 最终方案总结

本方案的最终结构是：

- Python 用 `kNN + greedy` 做多对多匹配
- Java 用现有 flow-aware 图搜索为已匹配对生成接驾路线
- flow field 主要用于热点引导、候选裁剪和空驶重定位
- PostgreSQL/PostGIS 提供空间索引、状态查询和事务一致性

一句话概括：

先用轻量算法快速决定“谁接谁”，再用图搜索精确计算“怎么接”。

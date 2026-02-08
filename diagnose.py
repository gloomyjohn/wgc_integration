#!/usr/bin/env python3
"""
WGC 系统诊断工具
检查集成系统的各个服务是否正常运行
"""

import requests
import json
import time
from datetime import datetime
from typing import Dict, Tuple
import sys

# 配置
GATEWAY_URL = "http://localhost:9000"
PYTHON_ENGINE_URL = "http://localhost:8000"
JAVA_BACKEND_URL = "http://localhost:8080"

class Colors:
    """终端颜色"""
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def print_header(text):
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{text.center(60)}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}\n")

def print_success(text):
    print(f"{Colors.GREEN}✓ {text}{Colors.RESET}")

def print_error(text):
    print(f"{Colors.RED}✗ {text}{Colors.RESET}")

def print_warning(text):
    print(f"{Colors.YELLOW}⚠ {text}{Colors.RESET}")

def print_info(text):
    print(f"{Colors.BLUE}ℹ {text}{Colors.RESET}")

def check_service_health(url: str, name: str, health_path: str = "/health") -> Tuple[bool, str]:
    """检查单个服务的健康状态"""
    try:
        print_info(f"检查 {name}...")
        response = requests.get(f"{url}{health_path}", timeout=3)
        
        if response.status_code == 200:
            data = response.json()
            print_success(f"{name} 运行正常 (状态: {response.status_code})")
            return True, json.dumps(data, indent=2, ensure_ascii=False)
        else:
            print_error(f"{name} 返回异常状态: {response.status_code}")
            return False, str(response.text)
    except requests.exceptions.ConnectError:
        print_error(f"{name} 无法连接 (连接被拒绝)")
        return False, "Connection refused"
    except requests.exceptions.Timeout:
        print_error(f"{name} 响应超时")
        return False, "Timeout"
    except requests.exceptions.RequestException as e:
        print_error(f"{name} 请求失败: {str(e)}")
        return False, str(e)
    except Exception as e:
        print_error(f"{name} 检查失败: {str(e)}")
        return False, str(e)

def test_gateway_routing() -> bool:
    """测试网关路由功能"""
    print_info("测试网关路由...")
    
    # 测试转发到 Python 引擎
    try:
        response = requests.get(f"{GATEWAY_URL}/api/match/health", timeout=3)
        if response.status_code == 200:
            print_success("网关 → Python 引擎 路由正常")
        else:
            print_error(f"网关 → Python 引擎 路由异常: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"网关 → Python 引擎 路由测试失败: {str(e)}")
        return False
    
    # 测试转发到 Java 后端
    try:
        response = requests.get(f"{GATEWAY_URL}/api/v1/actuator/health", timeout=3)
        if response.status_code == 200:
            print_success("网关 → Java 后端 路由正常")
        else:
            print_error(f"网关 → Java 后端 路由异常: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"网关 → Java 后端 路由测试失败: {str(e)}")
        return False
    
    return True

def test_api_functionality() -> bool:
    """测试 API 功能"""
    print_info("测试 API 功能...")
    
    # 测试匹配 API
    try:
        payload = {
            "drivers": {
                "driver_1": [1.35, 103.8],
                "driver_2": [1.36, 103.81]
            },
            "passengers": {
                "passenger_1": [1.351, 103.801],
                "passenger_2": [1.352, 103.802]
            },
            "k": 5,
            "max_dist": 10.0
        }
        
        response = requests.post(
            f"{GATEWAY_URL}/api/match/match",
            json=payload,
            timeout=5
        )
        
        if response.status_code == 200:
            result = response.json()
            matched = sum(1 for v in result.get("matches", {}).values() if v is not None)
            total_drivers = len(result.get("matches", {}))
            print_success(f"匹配 API 正常 (匹配 {matched}/{total_drivers} 个司机)")
            return True
        else:
            print_error(f"匹配 API 返回异常: {response.status_code}")
            print_info(f"响应: {response.text[:200]}")
            return False
    except Exception as e:
        print_error(f"匹配 API 测试失败: {str(e)}")
        return False

def generate_report(results: Dict) -> str:
    """生成诊断报告"""
    report = f"""
{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}
{Colors.BOLD}WGC 系统集成诊断报告{Colors.RESET}
{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}

生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

📊 检查结果概览:
─────────────────────────────────────────────────────────

1. API 网关 (Gateway):
   状态: {Colors.GREEN if results['gateway'] else Colors.RED}{'✓ 正常' if results['gateway'] else '✗ 异常'}{Colors.RESET}
   地址: http://localhost:9000
   作用: 转发所有请求到后端微服务

2. Python 匹配引擎:
   状态: {Colors.GREEN if results['python_engine'] else Colors.RED}{'✓ 正常' if results['python_engine'] else '✗ 异常'}{Colors.RESET}
   地址: http://localhost:8000
   作用: K-NN 匹配算法服务

3. Java 后端:
   状态: {Colors.GREEN if results['java_backend'] else Colors.RED}{'✓ 正常' if results['java_backend'] else '✗ 异常'}{Colors.RESET}
   地址: http://localhost:8080
   作用: 业务逻辑和数据存储

4. 网关路由:
   状态: {Colors.GREEN if results['routing'] else Colors.RED}{'✓ 正常' if results['routing'] else '✗ 异常'}{Colors.RESET}
   作用: 请求转发是否正常工作

5. API 功能:
   状态: {Colors.GREEN if results['api'] else Colors.RED}{'✓ 正常' if results['api'] else '✗ 异常'}{Colors.RESET}
   作用: 端到端功能测试

─────────────────────────────────────────────────────────

🎯 总体状态: {'✓ 系统运行正常' if all(results.values()) else '✗ 系统存在问题'}

"""
    
    if not all(results.values()):
        report += f"""
⚠️  故障排查建议:

1. 检查 Docker 容器状态:
   docker-compose ps

2. 查看详细日志:
   docker-compose logs -f

3. 重启服务:
   docker-compose restart

4. 完整重构（如果上述方法无效）:
   docker-compose down
   docker-compose up -d

"""
    
    report += f"""
{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}
"""
    
    return report

def main():
    print_header("WGC 系统集成诊断工具")
    
    results = {
        "gateway": False,
        "python_engine": False,
        "java_backend": False,
        "routing": False,
        "api": False
    }
    
    # 第1步: 检查网关
    print_header("第 1 步: 检查 API 网关")
    status, output = check_service_health(GATEWAY_URL, "API 网关", "/health")
    results["gateway"] = status
    if status:
        print_info(f"详细信息:\n{output}")
    
    time.sleep(1)
    
    # 第2步: 检查 Python 引擎
    print_header("第 2 步: 检查 Python 匹配引擎")
    status, output = check_service_health(PYTHON_ENGINE_URL, "Python 引擎", "/health")
    results["python_engine"] = status
    if status:
        print_info(f"详细信息:\n{output}")
    
    time.sleep(1)
    
    # 第3步: 检查 Java 后端
    print_header("第 3 步: 检查 Java 后端")
    status, output = check_service_health(JAVA_BACKEND_URL, "Java 后端", "/actuator/health")
    results["java_backend"] = status
    if status:
        print_info(f"详细信息:\n{output}")
    
    time.sleep(1)
    
    # 第4步: 测试网关路由
    if results["gateway"]:
        print_header("第 4 步: 测试网关路由功能")
        results["routing"] = test_gateway_routing()
    else:
        print_warning("网关未运行，跳过路由测试")
    
    time.sleep(1)
    
    # 第5步: 测试 API 功能
    if results["routing"]:
        print_header("第 5 步: 测试 API 功能")
        results["api"] = test_api_functionality()
    else:
        print_warning("路由测试失败，跳过 API 功能测试")
    
    # 生成报告
    report = generate_report(results)
    print(report)
    
    # 返回状态码
    sys.exit(0 if all(results.values()) else 1)

if __name__ == "__main__":
    main()

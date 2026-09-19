# v2rayJW - 智能优选 CDN 节点代理客户端

**v2rayJW** 是基于官方干净基线 [v2rayNG v2.3.8](https://github.com/2dust/v2rayNG) 深度二次开发与定制优化的 Android 代理客户端。本项目保留了原版 100% 原生稳定 VPN 服务与底层 Xray Core 架构，新增了自动化全量测速、智能 CDN 物理 IP 优选、网络感知动态命名及模态进度管理等核心优化功能。

---

## 🔥 核心特色功能

### 1. ⚡ 全量节点真连接测速与 Top 8 筛选
- **全订阅覆盖**：通过 `decodeAllServerList()` 自动检索用户导入的所有订阅分组与本地节点。
- **Xray 原生真连接**：采用与原版完全一致的 Xray 核心握手测速 (`measureOutboundDelay`)，准确测量真实 HTTP 204 连通延迟。
- **IPv4 / IPv6 双栈遴选**：自动挑选连通延迟最低的前 8 个最快节点（包含 4 个 IPv4 节点 + 4 个 IPv6 节点）。

### 2. 🛡️ 智能 CDN 物理 IP 握手验证与 IP 替换
- **物理 IP 握手探针**：在替换 Cloudflare 等 CDN 节点 IP 前，使用 Xray 核心进行二次 handshake 验证。
- **严格 SNI / Host 域名校验**：严格区分 CDN 域名节点与直连 VPS 节点。绝不将纯 IP 误设为 TLS SNI，彻底消除 `x509: cannot validate certificate...` 证书校验错误。
- **直连 VPS 安全保护**：对于无 CDN 域名的直连 VPS 节点，自动保留其原生有效 IP，确保节点 100% 通畅（无 `-1ms` 失败）。

### 3. 📶 网络感知动态命名与【本地优选】独立分组
- **动态网络命名**：识别当前网络环境，连接 Wi-Fi 时自动命名为 `WLAN-01` ~ `WLAN-08`，使用移动数据时自动命名为 `Mobile-01` ~ `Mobile-08`。
- **独立订阅组**：将优选后的 8 个最优节点自动归类并集中管理在独立的 **“本地优选”** 订阅分组中。

### 4. 📊 模态进度对话框 (Progress Dialog)
- 摒弃易消失的 Toast 提示，采用 Compose `AlertDialog` 实时展示全量检索、延迟扫描、CDN 验证与节点优选的步骤与进度百分比。

### 5. 🔌 100% 原版原生连接稳定性
- 默认开启 Xray 原生内置 TUN 引擎（`libgojni.so`），完美修复 `libhev-socks5-tunnel.so` 缺失引起的闪退与启动/终止按钮死锁问题，确保右下角 FAB 按钮与 VPN 状态栏图标运行稳定。

---

## 🛠️ 项目构建与 APK 说明

### 1. 编译命令
在项目根目录下执行以下命令即可构建 Playstore Debug APK：
```bash
.\gradlew.bat assemblePlaystoreDebug
```

### 2. APK 输出目录
编译成功后，APK 文件位于：
`v2rayNG/V2rayNG/app/build/outputs/apk/playstore/debug/`

### 3. 设备选包指南
| 文件名 | 适用设备 | 说明 |
| :--- | :--- | :--- |
| **`v2rayNG_2.3.8_arm64-v8a.apk`** | **小米 13 手机、现代主流 64 位 Android 手机与平板** | **首选推荐**（体积约 40MB，性能与省电最优） |
| **`v2rayNG_2.3.8_universal.apk`** | **任何 Android 设备 / 不确定芯片架构的设备** | 通用万能包（体积约 85MB，包含所有架构指令集） |
| `v2rayNG_2.3.8_x86_64.apk` | 电脑上的 Android 模拟器 | 模拟器专用 |

---

## 📄 开源协议与致谢
- 本项目继承 [v2rayNG](https://github.com/2dust/v2rayNG) 的 GPL-3.0 开源协议。
- 感谢 [Xray-core](https://github.com/XTLS/Xray-core) 项目团队提供强大的底层代理内核。

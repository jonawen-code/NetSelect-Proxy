import socket
import time
import concurrent.futures
import urllib.request
import json
import os

# Common Cloudflare IPv4 CIDR blocks
CLOUDFLARE_IP_RANGES = [
    "104.16.0.0/14",
    "104.20.0.0/14",
    "172.64.0.0/13",
    "162.158.0.0/15",
    "198.41.128.0/17",
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22"
]

# Sample curated fast IP list for quick verification
SAMPLE_FAST_IPS = [
    "104.16.249.249", "104.17.150.150", "162.159.200.1", "172.67.180.180",
    "104.18.2.2", "104.19.3.3", "104.20.4.4", "172.64.1.1",
    "162.158.10.10", "198.41.129.1"
]

def tcp_ping(ip, port=443, timeout=1.5):
    """Measures TCP handshake latency (RTT) in milliseconds."""
    start = time.time()
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        sock.connect((ip, port))
        sock.close()
        rtt = (time.time() - start) * 1000.0
        return ip, rtt
    except Exception:
        return ip, None

def test_ip_batch(ip_list, port=443, max_workers=20):
    """Performs concurrent TCP ping tests across a list of IPs."""
    print(f"[*] Starting TCP ping test across {len(ip_list)} IPs...")
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = {executor.submit(tcp_ping, ip, port): ip for ip in ip_list}
        for future in concurrent.futures.as_completed(futures):
            ip, rtt = future.result()
            if rtt is not None:
                results.append((ip, rtt))
    
    # Sort by lowest latency
    results.sort(key=lambda x: x[1])
    return results

def generate_vless_config(base_vless_url, target_ip):
    """
    Replaces the connection address in a vless:// URL with target_ip,
    while strictly preserving host / sni / path / uuid for TLS handshake.
    """
    # Parse vless://uuid@domain:port?type=ws&security=tlssni=domain#name
    if not base_vless_url.startswith("vless://"):
        return None
    
    body = base_vless_url[8:]
    uuid_part, rest = body.split("@", 1)
    addr_port, params_part = rest.split("?", 1) if "?" in rest else (rest, "")
    
    old_addr, port = addr_port.split(":", 1) if ":" in addr_port else (addr_port, "443")
    
    # Reconstruct with target_ip replacing old_addr
    new_vless = f"vless://{uuid_part}@{target_ip}:{port}"
    if params_part:
        new_vless += f"?{params_part}"
    
    return new_vless

if __name__ == "__main__":
    print("=== IP Auto-Selection Engine Test ===")
    fast_results = test_ip_batch(SAMPLE_FAST_IPS)
    
    print("\n[+] Top Selected IPs by Latency:")
    for ip, rtt in fast_results[:5]:
        print(f"  -> IP: {ip:<16} RTT: {rtt:.2f} ms")
        
    if fast_results:
        best_ip = fast_results[0][0]
        sample_node = "vless://12345678-1234-1234-1234-123456789012@original.domain.com:443?type=ws&security=tls&sni=original.domain.com&host=original.domain.com#MyNode"
        optimized_node = generate_vless_config(sample_node, best_ip)
        print("\n[+] Sample Node BEFORE Optimization:\n", sample_node)
        print("\n[+] Sample Node AFTER Optimization:\n", optimized_node)

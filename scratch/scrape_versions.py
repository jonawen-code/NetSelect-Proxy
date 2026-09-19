import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

url = "https://apkpure.net/9monsters-gay-chat-dating/jp.co.applibros.alligatorxx/versions"
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        matches = re.findall(r'href="([^"]+/download/[^"]+)"', html)
        print("Version download links:", matches[:10])
except Exception as e:
    print("Error:", e)

import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

url = "https://9monsters.en.uptodown.com/android/versions"
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        matches = re.findall(r'data-version="([^"]+)"', html)
        print("Uptodown versions:", matches)
        links = re.findall(r'href="(https://9monsters\.en\.uptodown\.com/android/download/[^"]+)"', html)
        print("Version links:", links)
except Exception as e:
    print("Error:", e)

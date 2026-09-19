import urllib.request
import os

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

url = "https://d.apkpure.net/b/APK/jp.co.applibros.alligatorxx?version=latest"
target = os.path.join(os.getcwd(), "scratch", "9Monsters_latest.apk")

print("Downloading 9Monsters from APKPure:", url)
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp, open(target, 'wb') as f:
        data = resp.read()
        f.write(data)
        print(f"Success! Downloaded {len(data)} bytes to {target}")
except Exception as e:
    print("Error downloading 9Monsters:", e)

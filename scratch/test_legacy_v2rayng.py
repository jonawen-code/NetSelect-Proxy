import urllib.request
import os
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

scratch_dir = os.path.join(os.getcwd(), "scratch")
adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device_id = "1661502059"

test_urls = [
    "https://github.com/2dust/v2rayNG/releases/download/1.0.0/v2rayNG_1.0.0.apk",
    "https://github.com/2dust/v2rayNG/releases/download/1.0.0/v2rayNG_1.0.0_armeabi-v7a.apk",
    "https://github.com/2dust/v2rayNG/releases/download/1.1.0/v2rayNG_1.1.0_armeabi-v7a.apk",
    "https://github.com/2dust/v2rayNG/releases/download/1.2.0/v2rayNG_1.2.0_armeabi-v7a.apk",
    "https://github.com/2dust/v2rayNG/releases/download/0.4.1/v2rayNG_0.4.1.apk",
    "https://github.com/2dust/v2rayNG/releases/download/0.5.0/v2rayNG_0.5.0.apk",
]

for url in test_urls:
    filename = url.split('/')[-2] + "_" + url.split('/')[-1]
    target_file = os.path.join(scratch_dir, filename)
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp, open(target_file, 'wb') as f:
            data = resp.read()
            f.write(data)
        print(f"Downloaded {filename} ({len(data)} bytes). Testing ADB install...")
        cmd = [adb_path, "-s", device_id, "install", "-r", "-d", target_file]
        res = subprocess.run(cmd, capture_output=True, text=True)
        print("ADB Output:", res.stdout, res.stderr)
        if "Success" in res.stdout:
            print(f"\n🎉🎉🎉 SUCCESS! Installed {filename} on Android 6.0 device!")
            break
    except Exception as e:
        print(f"Failed {url}: {e}")

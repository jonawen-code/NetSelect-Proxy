import urllib.request
import os
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

scratch_dir = os.path.join(os.getcwd(), "scratch")
adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device_id = "1661502059"

# We know 1.0.0 installed successfully! Let's test 1.3.0, 1.2.0, 1.1.0
test_urls = [
    "https://github.com/2dust/v2rayNG/releases/download/1.3.0/v2rayNG_1.3.0.apk",
    "https://github.com/2dust/v2rayNG/releases/download/1.2.0/v2rayNG_1.2.0.apk",
    "https://github.com/2dust/v2rayNG/releases/download/1.1.0/v2rayNG_1.1.0.apk",
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
        print("ADB Result:", res.stdout, res.stderr)
        if "Success" in res.stdout:
            print(f"\n🎉🎉🎉 SUCCESS! Upgraded to v2rayNG {filename} on Android 6.0 device!")
            break
    except Exception as e:
        print(f"Failed {url}: {e}")

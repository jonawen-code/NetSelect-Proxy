import urllib.request
import os
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

scratch_dir = os.path.join(os.getcwd(), "scratch")
adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device_id = "1661502059"

versions = [
    "1.8.5", "1.8.4", "1.8.0", "1.7.38", "1.7.30", "1.7.20", "1.7.0",
    "1.6.30", "1.6.20", "1.6.0", "1.5.20", "1.5.0", "1.4.20", "1.4.0",
    "1.3.0", "1.2.0", "1.1.0", "1.0.0"
]

patterns = [
    "v2rayNG_{v}.apk",
    "v2rayNG_{v}_arm64-v8a.apk",
    "v2rayNG_{v}_universal.apk",
    "v2rayNG_{v}-fdroid_arm64-v8a.apk"
]

for v in versions:
    for p in patterns:
        filename = p.format(v=v)
        url = f"https://github.com/2dust/v2rayNG/releases/download/{v}/{filename}"
        target_file = os.path.join(scratch_dir, f"test_{filename}")
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
                print(f"\n🎉🎉🎉 SUCCESS! Installed v2rayNG {filename} on Android 6.0 device!")
                os._exit(0)
        except Exception as e:
            pass
print("Finished testing all patterns")

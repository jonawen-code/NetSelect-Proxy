import urllib.request
import re
import os
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

scratch_dir = os.path.join(os.getcwd(), "scratch")
adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device_id = "1661502059"

# Candidate tags to try for Android 6.0 compatibility
candidate_tags = ["1.8.5", "1.7.29", "1.7.20", "1.6.30", "1.5.15", "1.4.15"]

for tag in candidate_tags:
    print(f"\n--- Trying v2rayNG version {tag} ---")
    url = f"https://github.com/2dust/v2rayNG/releases/tag/{tag}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            matches = re.findall(r'href="(/2dust/v2rayNG/releases/download/[^"]+\.apk)"', html)
            print(f"Found {len(matches)} APK links for tag {tag}")
            
            for apk_rel in matches:
                # prefer arm64-v8a or universal or armeabi-v7a
                if 'x86' in apk_rel and 'arm' not in apk_rel:
                    continue
                
                dl_url = "https://github.com" + apk_rel
                filename = f"v2rayNG_{tag}_" + os.path.basename(apk_rel)
                target_file = os.path.join(scratch_dir, filename)
                
                print(f"Downloading {dl_url} -> {target_file}")
                req_dl = urllib.request.Request(dl_url, headers=headers)
                with urllib.request.urlopen(req_dl) as resp_dl, open(target_file, 'wb') as f:
                    f.write(resp_dl.read())
                
                print(f"Downloaded {os.path.getsize(target_file)} bytes. Installing to {device_id}...")
                cmd = [adb_path, "-s", device_id, "install", "-r", "-d", target_file]
                res = subprocess.run(cmd, capture_output=True, text=True)
                print("ADB Result:", res.stdout, res.stderr)
                
                if "Success" in res.stdout:
                    print(f"\n🎉🎉🎉 SUCCESS! Installed v2rayNG {tag} ({filename}) on Android 6.0 device!")
                    os._exit(0)
    except Exception as e:
        print(f"Error checking {tag}:", e)

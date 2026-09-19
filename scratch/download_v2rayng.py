import urllib.request
import re
import os
import zipfile
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

scratch_dir = os.path.join(os.getcwd(), "scratch")

# Try scraping GitHub HTML releases
print("Scraping GitHub Releases HTML...")
url = "https://github.com/2dust/v2rayNG/releases"
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        matches = re.findall(r'href="(/2dust/v2rayNG/releases/download/[^"]+\.apk)"', html)
        print("Found APK download URLs:", matches)
        if matches:
            dl_url = "https://github.com" + matches[0]
            filename = os.path.basename(matches[0])
            target_file = os.path.join(scratch_dir, filename)
            print(f"Downloading {dl_url} -> {target_file}")
            req_dl = urllib.request.Request(dl_url, headers=headers)
            with urllib.request.urlopen(req_dl) as resp_dl, open(target_file, 'wb') as f:
                f.write(resp_dl.read())
            print(f"Downloaded {os.path.getsize(target_file)} bytes")
            
            # Install to 3rd phone 1661502059
            adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
            cmd = [adb_path, "-s", "1661502059", "install", "-r", "-d", target_file]
            print("ADB Command:", cmd)
            res = subprocess.run(cmd, capture_output=True, text=True)
            print("ADB Result:", res.stdout, res.stderr)
            exit(0)
except Exception as e:
    print("GitHub HTML scrape error:", e)

# Fallback: APKPure
print("Trying APKPure fallback...")
url_apkpure = "https://d.apkpure.net/b/APK/com.v2ray.ang?version=latest"
target_file = os.path.join(scratch_dir, "v2rayNG_apkpure.apk")
try:
    req_dl = urllib.request.Request(url_apkpure, headers=headers)
    with urllib.request.urlopen(req_dl) as resp_dl, open(target_file, 'wb') as f:
        f.write(resp_dl.read())
    print(f"Downloaded {os.path.getsize(target_file)} bytes from APKPure")

    # check if zip
    if zipfile.is_zipfile(target_file):
        extract_dir = os.path.join(scratch_dir, "v2ray_extracted")
        os.makedirs(extract_dir, exist_ok=True)
        with zipfile.ZipFile(target_file, 'r') as z:
            z.extractall(extract_dir)
        apks = [os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.apk')]
        cmd = [adb_path, "-s", "1661502059", "install-multiple", "-r", "-d"] + apks
    else:
        cmd = [adb_path, "-s", "1661502059", "install", "-r", "-d", target_file]
    
    res = subprocess.run(cmd, capture_output=True, text=True)
    print("ADB Result:", res.stdout, res.stderr)
except Exception as e:
    print("APKPure download error:", e)

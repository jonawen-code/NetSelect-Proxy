import urllib.request
import os
import zipfile
import subprocess

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

url = "https://d.apkpure.net/b/APK/jp.co.applibros.alligatorxx?version=4.9.11"
scratch_dir = os.path.join(os.getcwd(), "scratch")
target = os.path.join(scratch_dir, "9Monsters_4.9.11.apk")

print("Downloading 9Monsters 4.9.11 from APKPure:", url)
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp, open(target, 'wb') as f:
        data = resp.read()
        f.write(data)
        print(f"Downloaded {len(data)} bytes")
    
    # Check if it's a zip/split apk
    extract_dir = os.path.join(scratch_dir, "9m_4911_extracted")
    os.makedirs(extract_dir, exist_ok=True)
    
    if zipfile.is_zipfile(target):
        print("Archive is a ZIP bundle, extracting...")
        with zipfile.ZipFile(target, 'r') as z:
            for name in z.namelist():
                if name.endswith('.apk'):
                    z.extract(name, extract_dir)
                    print("Extracted:", name)
        apk_files = [os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.apk')]
    else:
        apk_files = [target]

    print("Installing APKs via ADB:", apk_files)
    adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    if len(apk_files) == 1:
        cmd = [adb_path, "install", "-r", "-d", apk_files[0]]
    else:
        cmd = [adb_path, "install-multiple", "-r", "-d"] + apk_files
    
    res = subprocess.run(cmd, capture_output=True, text=True)
    print("ADB Install Result:", res.stdout, res.stderr)

except Exception as e:
    print("Error:", e)

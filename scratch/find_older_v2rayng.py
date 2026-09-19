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

apk_links = []

# Scrape pages 5 to 25 to find older 1.x releases
for page in range(5, 25):
    url = f"https://github.com/2dust/v2rayNG/releases?page={page}"
    print(f"Scraping page {page}...")
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            links = re.findall(r'href="(/2dust/v2rayNG/releases/download/[^"]+\.apk)"', html)
            print(f"Page {page} found {len(links)} links")
            apk_links.extend(links)
    except Exception as e:
        print(f"Error page {page}:", e)

unique_links = list(dict.fromkeys(apk_links))
print(f"Total unique older APK links found: {len(unique_links)}")

for rel_path in unique_links:
    if 'x86' in rel_path and 'arm' not in rel_path:
        continue
    
    dl_url = "https://github.com" + rel_path
    filename = rel_path.replace('/', '_').strip('_')
    target_file = os.path.join(scratch_dir, filename)
    
    print(f"\nTrying: {dl_url}")
    req_dl = urllib.request.Request(dl_url, headers=headers)
    try:
        with urllib.request.urlopen(req_dl) as resp_dl, open(target_file, 'wb') as f:
            f.write(resp_dl.read())
        print(f"Downloaded {os.path.getsize(target_file)} bytes")
        
        cmd = [adb_path, "-s", device_id, "install", "-r", "-d", target_file]
        res = subprocess.run(cmd, capture_output=True, text=True)
        print("ADB Result:", res.stdout, res.stderr)
        
        if "Success" in res.stdout:
            print(f"\n🎉🎉🎉 SUCCESS! Successfully installed {filename} on Android 6.0 device!")
            break
    except Exception as e:
        print("Error during download/install:", e)

import zipfile
import os
import subprocess

scratch_dir = os.path.join(os.getcwd(), "scratch")
apk_zip = os.path.join(scratch_dir, "9Monsters_latest.apk")
extract_dir = os.path.join(scratch_dir, "9m_extracted")

os.makedirs(extract_dir, exist_ok=True)

with zipfile.ZipFile(apk_zip, 'r') as z:
    for name in z.namelist():
        if name.endswith('.apk'):
            z.extract(name, extract_dir)
            print("Extracted:", name)

apk_files = [os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.apk')]
print("Installing APKs:", apk_files)

adb_path = r"C:\Users\jonaw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
cmd = [adb_path, "install-multiple", "-r"] + apk_files
res = subprocess.run(cmd, capture_output=True, text=True)
print("ADB Result:", res.stdout, res.stderr)

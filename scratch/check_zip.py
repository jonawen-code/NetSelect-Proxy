import zipfile
import os

apk_path = os.path.join(os.getcwd(), "scratch", "9Monsters_latest.apk")
with zipfile.ZipFile(apk_path, 'r') as z:
    files = z.namelist()
    print("Files in archive:", files[:20])

import subprocess
import sys

try:
    result = subprocess.run(["adb", "logcat", "-d"], capture_output=True, text=True, errors="replace")
    lines = result.stdout.split('\n')
    filtered = [line for line in lines if "ExoPlayer" in line or "Exception" in line or "streamer" in line.lower()]
    print("\n".join(filtered[-150:]))
except Exception as e:
    print(f"Failed: {e}")

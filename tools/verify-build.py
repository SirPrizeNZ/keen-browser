#!/usr/bin/env python3
import os
import sys
import subprocess
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID_HOME = os.environ.get("ANDROID_HOME", "/usr/local/share/android-commandlinetools")
AAPT = os.path.join(ANDROID_HOME, "build-tools/36.0.0/aapt")
APKSIGNER = os.path.join(ANDROID_HOME, "build-tools/36.0.0/apksigner")

def run_cmd(cmd, check=True):
    print(f"Running: {' '.join(cmd)}")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if check and res.returncode != 0:
        print(f"Command failed with exit code {res.returncode}")
        print(f"STDOUT:\n{res.stdout}")
        print(f"STDERR:\n{res.stderr}")
        sys.exit(1)
    return res

def main():
    if len(sys.argv) < 3:
        print("Usage: verify-build.py <path-to-apk> <architecture-32-or-64>")
        sys.exit(1)
        
    apk_path = os.path.abspath(sys.argv[1])
    arch_type = sys.argv[2]
    
    if not os.path.exists(apk_path):
        print(f"Error: APK not found at {apk_path}")
        sys.exit(1)
        
    print(f"=== Starting verification for {os.path.basename(apk_path)} (Arch: {arch_type}) ===")
    
    # 1. Cryptographic Signatures Verification
    print("Verifying cryptographic signature...")
    run_cmd([APKSIGNER, "verify", "--verbose", apk_path])
    print("Signature verified successfully.")
    
    # 2. AAPT Manifest Diagnostics
    print("Running aapt manifest diagnostics...")
    badging = run_cmd([AAPT, "dump", "badging", apk_path]).stdout
    
    expected_native_code = "armeabi-v7a" if arch_type == "32" else "arm64-v8a"
    if f"native-code: '{expected_native_code}'" not in badging:
        print(f"Error: Native code targeting '{expected_native_code}' was not found in aapt badging!")
        print([line for line in badging.split('\n') if "native-code" in line])
        sys.exit(1)
    print(f"Native code explicitly targets {expected_native_code}.")
    
    # 3. Decompile Finished APK for structural verification
    tmp_verify_dir = os.path.join(ROOT, "build", f"verify-decompile-{arch_type}")
    if os.path.exists(tmp_verify_dir):
        shutil.rmtree(tmp_verify_dir)
        
    print("Decompiling APK to verification directory...")
    run_cmd(["apktool", "d", "-f", "-r", apk_path, "-o", tmp_verify_dir])
    
    # 4. Verify Native Library Mapping
    print("Verifying native library mapping...")
    lib_dir = os.path.join(tmp_verify_dir, "lib", "armeabi-v7a" if arch_type == "32" else "arm64-v8a")
    if not os.path.exists(lib_dir):
        print(f"Error: Native library directory not found at {lib_dir}!")
        sys.exit(1)
        
    essential_libs = ["libchrome.so", "libbrave.so"]
    # Check if libchrome.so exists
    chrome_so = os.path.join(lib_dir, "libchrome.so")
    if not os.path.exists(chrome_so):
        print(f"Error: libchrome.so not found in {lib_dir}!")
        sys.exit(1)
    print("Native library mapping verified successfully.")
    
    # 5. Perform Automated Smali Diff Checking
    # Verify that the injected hooks are intact in the smali files.
    print("Verifying injected hooks...")
    # Check jn3.smali
    jn3_path = os.path.join(tmp_verify_dir, "smali", "jn3.smali")
    with open(jn3_path, "r") as f:
        jn3_content = f.read()
    if "Lcom/brave/tv/TvCursorController;->handle" not in jn3_content:
        print("Error: TvCursorController hook not found in jn3.smali!")
        sys.exit(1)
        
    # Check k72.smali
    k72_path = os.path.join(tmp_verify_dir, "smali_classes2", "k72.smali")
    with open(k72_path, "r") as f:
        k72_content = f.read()
    if "Lcom/brave/tv/TvCursorController;->attachPopup" not in k72_content:
        print("Error: TvCursorController popup hook not found in k72.smali!")
        sys.exit(1)
        
    # Check gg6.smali
    gg6_path = os.path.join(tmp_verify_dir, "smali", "gg6.smali")
    with open(gg6_path, "r") as f:
        gg6_content = f.read()
    if "const/4 v0, 0x0" not in gg6_content or "return v0" not in gg6_content:
        print("Error: First-run bypass hook not found in gg6.smali!")
        sys.exit(1)
        
    # Check TabWebContentsDelegateAndroidImpl.smali
    delegate_path = os.path.join(tmp_verify_dir, "smali_classes2", "org", "chromium", "chrome", "browser", "tab", "TabWebContentsDelegateAndroidImpl.smali")
    with open(delegate_path, "r") as f:
        delegate_content = f.read()
    if "Lcom/brave/tv/TvPopupBlocker;->shouldAllowNewContents" not in delegate_content:
        print("Error: TvPopupBlocker hook not found in TabWebContentsDelegateAndroidImpl.smali!")
        sys.exit(1)

    print("Injected hooks verified successfully.")
    print("=== Verification PASSED! ===")
    
    # Cleanup verification folder
    shutil.rmtree(tmp_verify_dir)

if __name__ == "__main__":
    main()

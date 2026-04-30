#!/data/data/com.termux/files/usr/bin/bash

echo "Capturing AppManager startup logs..."
echo "1. Clearing logcat..."
logcat -c

echo "2. Please open AppManager app NOW"
echo "3. Wait for the app list to load..."
echo "4. Press ENTER when done"
read

echo "5. Capturing logs..."
logcat -d | grep -E "(AppManager|MainActivity|Main|ViewModelProvider|getAllApplicationInfo|getInstalledPackages)" > appmanager_startup.log

echo "Done! Logs saved to: appmanager_startup.log"
echo ""
echo "Lines captured:"
wc -l appmanager_startup.log

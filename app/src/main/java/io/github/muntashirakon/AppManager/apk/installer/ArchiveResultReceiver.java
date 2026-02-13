package io.github.muntashirakon.AppManager.apk.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.ArchivedAppDao;
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;

public class ArchiveResultReceiver extends BroadcastReceiver {

    public static final String ACTION_ARCHIVE_RESULT = "io.github.muntashirakon.AppManager.ACTION_ARCHIVE_RESULT";
    private static final String TAG = "ArchiveResultReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_ARCHIVE_RESULT.equals(intent.getAction())) {
            final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            final String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);

            if (packageName == null) {
                Log.e(TAG, "Package name is null in archive result");
                return;
            }

            ArchivedAppDao archivedAppDao = AppsDb.getInstance().archivedAppDao();

            switch (status) {
                case PackageInstaller.STATUS_SUCCESS:
                    Log.d(TAG, "App archived successfully: " + packageName);
                    // The app was already added to the archived list in BatchOpsManager
                    // Here we could potentially update its status or remove it from a pending list
                    break;
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Log.d(TAG, "User action required for archiving: " + packageName);
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    Log.e(TAG, "App archiving failed for " + packageName + ": " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                    // Remove from archived list if it was added prematurely
                    archivedAppDao.deleteByPackageNameSync(packageName);
                    break;
                default:
                    Log.e(TAG, "Unknown archiving status for " + packageName + ": " + status);
                    archivedAppDao.deleteByPackageNameSync(packageName);
            }
        }
    }
}

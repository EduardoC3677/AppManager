// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class PackageInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String packageName = data.getSchemeSpecificPart();
                if (packageName != null) {
                    ThreadUtils.postOnBackgroundThread(() -> {
                        AppsDb.getInstance().archivedAppDao().deleteByPackageName(packageName);
                    });
                }
            }
        }
    }
}

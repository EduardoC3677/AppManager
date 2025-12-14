// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.Manifest;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class InstallerPreferencesViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> _canInstallPackages = new MutableLiveData<>();
    public LiveData<Boolean> canInstallPackages = _canInstallPackages;

    private final MutableLiveData<Boolean> _isSystemOrRootOrShell = new MutableLiveData<>();
    public LiveData<Boolean> isSystemOrRootOrShell = _isSystemOrRootOrShell;

    private final MutableLiveData<Boolean> _canModifyAppComponentStates = new MutableLiveData<>();
    public LiveData<Boolean> canModifyAppComponentStates = _canModifyAppComponentStates;

    public InstallerPreferencesViewModel(@NonNull Application application) {
        super(application);
    }

    public void loadCanInstallPackagesPermission() {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean hasPermission = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES);
            _canInstallPackages.postValue(hasPermission);
        });
    }

    public void loadSystemOrRootOrShellStatus() {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean status = SelfPermissions.isSystemOrRootOrShell();
            _isSystemOrRootOrShell.postValue(status);
        });
    }

    public void loadCanModifyAppComponentStates() {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean status = SelfPermissions.canModifyAppComponentStates(UserHandleHidden.myUserId(), null, true);
            _canModifyAppComponentStates.postValue(status);
        });
    }
}

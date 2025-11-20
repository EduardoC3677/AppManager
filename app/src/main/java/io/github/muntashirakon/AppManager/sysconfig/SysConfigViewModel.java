// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.utils.AppExecutor;

public class SysConfigViewModel extends AndroidViewModel {
    private final MutableLiveData<List<SysConfigInfo>> mSysConfigInfoListLiveData = new MutableLiveData<>();
    private final ExecutorService mExecutor = AppExecutor.getExecutor();

    public SysConfigViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public LiveData<List<SysConfigInfo>> getSysConfigInfoListLiveData() {
        return mSysConfigInfoListLiveData;
    }

    @AnyThread
    public void loadSysConfigInfo(@SysConfigType String sysConfigType) {
        mExecutor.submit(() -> {
            List<SysConfigInfo> sysConfigInfoList = SysConfigWrapper.getSysConfigs(sysConfigType);
            Collections.sort(sysConfigInfoList, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
            mSysConfigInfoListLiveData.postValue(sysConfigInfoList);
        });
    }
}

// RemoteCommandService.java
package io.github.muntashirakon.AppManager.utils;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Bundle;

import rikka.shizuku.ShizukuService;

public class RemoteCommandService extends ShizukuService {

    @Override
    public IBinder onBind(Intent intent) {
        return new IRemoteCommandService.Stub() {
            @Override
            public int runCommand(String command) throws RemoteException {
                try {
                    Process process = new ProcessBuilder("sh", "-c", command).start();
                    return process.waitFor();
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            }

            @Override
            public Bundle executeJavaCode(String className, String methodName, Bundle args) throws RemoteException {
                try {
                    Class<?> clazz = Class.forName(className);
                    // Assuming the method is static and takes a Bundle as argument
                    Method method = clazz.getMethod(methodName, Bundle.class);
                    Bundle result = (Bundle) method.invoke(null, args);
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RemoteException(e.getMessage());
                }
            }
        };
    }
}

// IRemoteCommandService.aidl
package io.github.muntashirakon.AppManager.utils;

// Result bundle for command execution
// Keys: "stdout" (String), "stderr" (String), "exitCode" (int)
interface IRemoteCommandService {
    Bundle runCommand(String command);
    Bundle executeJavaCode(String className, String methodName, in Bundle args);
}
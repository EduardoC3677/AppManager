// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.content.pm.PackageManager;
import android.os.UserHandle;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(Context.class)
public class ContextHidden {
    public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        return HiddenUtil.throwUOE();
    }
}

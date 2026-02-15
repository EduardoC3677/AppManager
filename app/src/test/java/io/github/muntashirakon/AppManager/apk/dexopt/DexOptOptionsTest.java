// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DexOptOptionsTest {
    @Test
    public void testParcelable() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.setPackages(new String[]{"android.package"});
        Parcel parcel = Parcel.obtain();
        dexOptOptions.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DexOptOptions dexOptOptions2 = DexOptOptions.CREATOR.createFromParcel(parcel);
        assertArrayEquals(dexOptOptions.getPackages(), dexOptOptions2.getPackages());
        assertEquals(dexOptOptions.getCompilerFilter(), dexOptOptions2.getCompilerFilter());
        assertEquals(dexOptOptions.getCompileLayouts(), dexOptOptions2.getCompileLayouts());
        assertEquals(dexOptOptions.getClearProfileData(), dexOptOptions2.getClearProfileData());
        assertEquals(dexOptOptions.getCheckProfiles(), dexOptOptions2.getCheckProfiles());
        assertEquals(dexOptOptions.getBootComplete(), dexOptOptions2.getBootComplete());
        assertEquals(dexOptOptions.getForceCompilation(), dexOptOptions2.getForceCompilation());
        assertEquals(dexOptOptions.getForceDexOpt(), dexOptOptions2.getForceDexOpt());
    }
}

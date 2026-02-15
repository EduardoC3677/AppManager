// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions;

@RunWith(RobolectricTestRunner.class)
public class BatchDexOptOptionsTest {
    @Test
    public void testParcelable() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.setPackages(new String[]{"android.package"});
        BatchDexOptOptions options = new BatchDexOptOptions(dexOptOptions);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchDexOptOptions options2 = BatchDexOptOptions.CREATOR.createFromParcel(parcel);
        DexOptOptions dexOptOptions2 = options2.getDexOptOptions();
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
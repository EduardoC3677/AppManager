// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import androidx.annotation.NonNull;

public class BatchArchiveOptions implements IBatchOpOptions {
    private final int mode;

    public BatchArchiveOptions(int mode) {
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }
}

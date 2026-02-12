// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;

public class BatchArchiveOptions implements IBatchOpOptions {
    public static final String TAG = BatchArchiveOptions.class.getSimpleName();

    private final int mMode;

    public BatchArchiveOptions(int mode) {
        mMode = mode;
    }

    public int getMode() {
        return mMode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected BatchArchiveOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mMode = jsonObject.getInt("mode");
    }

    public static final JsonDeserializer.Creator<BatchArchiveOptions> DESERIALIZER
            = BatchArchiveOptions::new;

    protected BatchArchiveOptions(@NonNull Parcel in) {
        mMode = in.readInt();
    }

    public static final Creator<BatchArchiveOptions> CREATOR = new Creator<BatchArchiveOptions>() {
        @NonNull
        @Override
        public BatchArchiveOptions createFromParcel(@NonNull Parcel in) {
            return new BatchArchiveOptions(in);
        }

        @NonNull
        @Override
        public BatchArchiveOptions[] newArray(int size) {
            return new BatchArchiveOptions[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mMode);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("mode", mMode);
        return jsonObject;
    }
}

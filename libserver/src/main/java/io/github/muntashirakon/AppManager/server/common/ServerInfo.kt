// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable

// Copyright 2017 Zheng Li
class ServerInfo : Parcelable {
    @JvmField
    var protocolVersion: String? = DataTransmission.PROTOCOL_VERSION

    @JvmField
    var startArgs: String? = null

    @JvmField
    var startTime: Long = 0

    @JvmField
    var startRealTime: Long = 0

    @JvmField
    var rxBytes: Long = 0 // Received

    @JvmField
    var txBytes: Long = 0 // Sent

    @JvmField
    var successCount: Long = 0

    @JvmField
    var errorCount: Long = 0

    constructor()

    protected constructor(`in`: Parcel) {
        protocolVersion = `in`.readString()
        startArgs = `in`.readString()
        startTime = `in`.readLong()
        startRealTime = `in`.readLong()
        rxBytes = `in`.readLong()
        txBytes = `in`.readLong()
        successCount = `in`.readLong()
        errorCount = `in`.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(protocolVersion)
        dest.writeString(startArgs)
        dest.writeLong(startTime)
        dest.writeLong(startRealTime)
        dest.writeLong(rxBytes)
        dest.writeLong(txBytes)
        dest.writeLong(successCount)
        dest.writeLong(errorCount)
    }

    override fun toString(): String {
        return "ServerInfo{" +
                "protocolVersion='" + protocolVersion + ''' +
                ", startArgs='" + startArgs + ''' +
                ", startTime=" + startTime +
                ", startRealTime=" + startRealTime +
                ", rxBytes=" + rxBytes +
                ", txBytes=" + txBytes +
                ", successCount=" + successCount +
                ", errorCount=" + errorCount +
                '}'
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ServerInfo> = object : Parcelable.Creator<ServerInfo> {
            override fun createFromParcel(source: Parcel): ServerInfo {
                return ServerInfo(source)
            }

            override fun newArray(size: Int): Array<ServerInfo?> {
                return arrayOfNulls(size)
            }
        }
    }
}

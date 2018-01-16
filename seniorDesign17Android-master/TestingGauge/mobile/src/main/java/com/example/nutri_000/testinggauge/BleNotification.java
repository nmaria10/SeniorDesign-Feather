package com.example.nutri_000.testinggauge;

import android.bluetooth.BluetoothGatt;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by neuronifier on 9/6/2017.
 */

public class BleNotification implements Parcelable {
    float value;
    String gatt;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(value);
        dest.writeString(gatt);
    }
    public static final Parcelable.Creator<BleNotification> CREATOR
            = new Parcelable.Creator<BleNotification>() {
        public BleNotification createFromParcel(Parcel in) {
            return new BleNotification(in);
        }

        public BleNotification[] newArray(int size) {
            return new BleNotification[size];
        }
    };
    private BleNotification(Parcel in) {
        value = in.readFloat();
        gatt = in.readString();
    }
    public BleNotification(float value, String gatt){
        this.value = value;
        this.gatt = gatt;
    }
}

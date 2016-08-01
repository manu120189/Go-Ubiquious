package com.example.android.sunshine.app.sync;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

public class SunshineSyncService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<DataApi.DataItemResult> {

    private static final String TAG = "WeatherWatchface";
    public static final String WATCH_FACE_URL = "/simple_watch_face_config";
    public static final String WEATHER_KEY = "WEATHER_KEY";
    public static final String WEATHER_ICON_KEY = "weatherIcon";
    private GoogleApiClient googleApiClient;

    private static final Object sSyncAdapterLock = new Object();
    private static SunshineSyncAdapter sSunshineSyncAdapter = null;

    @Override
    public void onCreate() {
        Log.d("SunshineSyncService", "onCreate - SunshineSyncService");

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
        boolean wearAvailable = googleApiClient.hasConnectedApi(Wearable.API);
        Log.i(TAG, "Is wear available: " + wearAvailable);
        Log.i(TAG, "Is google connected?: " + googleApiClient.isConnected());
        googleApiClient.connect();

        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                sSunshineSyncAdapter = new SunshineSyncAdapter(SunshineSyncService.this, true);
            }
        }
    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");
        Log.e(TAG, connectionResult.toString());
    }

//    @Override
//    protected void onStop() {
//        if (googleApiClient != null && googleApiClient.isConnected()) {
//            googleApiClient.disconnect();
//        }
//        super.onStop();
//    }

    @Override
    public void onResult(DataApi.DataItemResult dataItemResult) {

    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    public void sendWearableRequest(String key, String iconKey, int drawable, String weather){
        try{
            Log.d(TAG,"Parameters DataItem: url: " + WATCH_FACE_URL + ", key:" + key + ", iconKey: " + iconKey + ", drawable: " + drawable + ", weather: " + weather);
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawable);
            Asset asset = createAssetFromBitmap(bitmap);
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(WATCH_FACE_URL);
            putDataMapReq.getDataMap().putString(key, weather);
            putDataMapReq.getDataMap().putAsset(iconKey, asset);
            putDataMapReq.getDataMap().putLong("Time", System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            putDataReq.setUrgent();
            Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
            Log.i(TAG, "Sending: " + putDataReq);
        }catch (Exception e){
            System.out.println("Error");
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    public void sendConfigUpdateMessage(View view) {
        sendWearableRequest(WEATHER_KEY, WEATHER_ICON_KEY, R.drawable.art_light_rain, "19,16");
    }
    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }
}
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String TAG = "WeatherWatchFace";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    public static final String WEATHER_WATCH_FACE_CONFIG = "/simple_watch_face_config";
    public static final String WEATHER_KEY = "WEATHER_KEY";
    public static final String WEATHER_ICON_KEY = "weatherIcon";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WeatherWatchFaceService.Engine> mWeakReference;

        public EngineHandler(WeatherWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG, "handleMessage: " + msg);
            WeatherWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{

        public static final String PATH_WITH_FEATURE = "/weather_app/current_weather";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mWeatherTextPaint;
        Paint mDateTextPaint;
        Paint mLinePaint;
        boolean mAmbient;
        Bitmap weatherIconBitmap;
        Time mTime;
        String mValue1 = "-";
        String mValue2 = "-";
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;
        float mTimeXOffset;
        float mDateXOffset;

        float mTimeYOffset;
        float mDateYOffset;

        private GoogleApiClient mGoogleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            Log.d(TAG, "Creating instance: " + mGoogleApiClient);

            Resources resources = WeatherWatchFaceService.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
            mDateYOffset = resources.getDimension(R.dimen.digital_date_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_text_date));

            mLinePaint = new Paint();
            mLinePaint = createTextPaint(resources.getColor(R.color.digital_text_date));

            mWeatherTextPaint = new Paint();
            mWeatherTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            releaseGoogleApiClient();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                Log.d(TAG, "onVisibilityChanged: " + visible);

                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

                //mLoadWeatherHandler.sendEmptyMessage(MSG_LOAD_WEATHER);
            } else {
                Log.d(TAG, "onVisibilityChanged: " + visible);

                unregisterReceiver();
                //mLoadWeatherHandler.removeMessages(MSG_LOAD_WEATHER);
                //cancelLoadWeatherTask();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseGoogleApiClient() {
            Log.d(TAG, "releaseGoogleApiClient: ");

            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(mGoogleApiClient, onDataChangedListener);
                mGoogleApiClient.disconnect();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);

        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mTimeXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_time_x_offset_round : R.dimen.digital_time_x_offset);

            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_x_offset_round : R.dimen.digital_date_x_offset);


            float textTimeSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);

            float textDateSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            float textWeatherSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_text_size_round : R.dimen.digital_weather_text_size);

            mTimeTextPaint.setTextSize(textTimeSize);
            mDateTextPaint.setTextSize(textDateSize);
            mWeatherTextPaint.setTextSize(textWeatherSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = WeatherWatchFaceService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLUE);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();


            DateFormat dateFormat = new SimpleDateFormat("E, MMM d yyyy");
            Calendar cal = Calendar.getInstance();
            String currentDate = dateFormat.format(cal.getTime()).toUpperCase().replace(".","");
            String timeText = String.format("%d:%02d", mTime.hour, mTime.minute);
            canvas.drawText(timeText, mTimeXOffset, mTimeYOffset, mTimeTextPaint);
            canvas.drawText(currentDate, mDateXOffset, mDateYOffset, mDateTextPaint);
            canvas.drawLine(112, 170, 162, 170, mLinePaint);
            canvas.drawText(mValue1 + " " + mValue2, 130, 220, mWeatherTextPaint);

            if (weatherIconBitmap != null){
                canvas.drawBitmap(weatherIconBitmap, null, new Rect(60,185,110,235), null);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "connected GoogleAPI");
            Wearable.DataApi.addListener(mGoogleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(onConnectedResultCallback);
        }

        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.d(TAG, "Data changed: " + dataEvents);
                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        processConfigurationFor(item);
                    }
                }

                dataEvents.release();
                //invalidate();
            }
        };
        private void processConfigurationFor(DataItem item) {
            Log.d(TAG, "processConfigurationFor: " + item);
            if (WEATHER_WATCH_FACE_CONFIG.equals(item.getUri().getPath())) {
                Log.d(TAG, WEATHER_WATCH_FACE_CONFIG + " called");
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                Log.d(TAG, "dataMap : " + dataMap);
                if (dataMap.containsKey(WEATHER_KEY)) {
                    String newWeather = dataMap.getString(WEATHER_KEY);
                    String[] split = newWeather.split(",");
                    if (split.length == 2){
                        mValue1 = split[0];
                        mValue2 = split[1];
                    }else {
                        mValue1 = "-";
                        mValue2 = "-";
                    }
                    final Asset weatherIcon = dataMap.getAsset(WEATHER_ICON_KEY);
                    if (weatherIcon != null){
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                weatherIconBitmap = loadBitmapFromAsset(weatherIcon);
                                invalidate();
                            }
                        }).start();
                        ;
                    }
                    invalidate();
                }
            }
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                Log.d(TAG, "onConnectedResultCallback:  " + dataItems);
                for (DataItem item : dataItems) {
                    processConfigurationFor(item);
                }
                dataItems.release();
                //invalidateIfNecessary();
            }
        };


        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Log.e(TAG, "suspended GoogleAPI");
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "connectionFailed GoogleAPI");
        }

    }
}

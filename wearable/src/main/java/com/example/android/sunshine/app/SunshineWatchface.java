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

package com.example.android.sunshine.app;

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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchface extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchface.Engine> mWeakReference;

        public EngineHandler(SunshineWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchface.Engine engine = mWeakReference.get();
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
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private GoogleApiClient mGoogleApiClient;

        //Wearable Paths
        public static final String WEAR_WEATHER_PATH = "/weather";
        public static final String WEAR_WEATHER_HIGH = "high_temperature";
        public static final String WEAR_WEATHER_LOW = "low_temperature";
        public static final String WEAR_WEATHER_CONDITION = "weather_condition";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mNormalTextPaint;
        Paint mBoldTextPaint;
        Paint mNormalTextGreyPaint;
        Paint mOpenAppGreyTextPaint;
        Paint mBoldTempPaint;
        Paint mGreyTempPaint;
        Paint antiAliasPaint;

        Paint mRectPaint;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;


        boolean mAmbient;
        Time mTime;
        int currentHour;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffsetHours;
        float mXOffsetMinutes;;
        float mYOffset;
        float mYOffsetTime;

        String highTemperature = "1";
        String lowTemperature = "2";
        Bitmap interactiveWeatherIcon;
        Bitmap ambientWeatherIcon;

        boolean isRound;

        float posYTime;
        float posXTimeHourUnderTen;
        float posXTimeMinutesUnderTen;
        float posXTimeHourOverTen;
        float posXTimeMinutesOverTen;
        float posYDate;
        float posXDateDay;
        float posXDateFull;
        float posXIcon;
        float posYIcon;
        float posYHighLow;
        float posXHigh;
        float posXLow;
        float posYOpenAppMessage;
        float posXOpenAppMessage;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
//            invalidate();
            super.onPeekCardPositionUpdate(rect);
            invalidate();

        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchface.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());



            Resources resources = SunshineWatchface.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetTime = resources.getDimension(R.dimen.digital_y_offset_time);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mNormalTextPaint = new Paint();
            mNormalTextPaint = createNormalTextPaint(resources.getColor(R.color.digital_text));

            mBoldTextPaint = new Paint();
            mBoldTextPaint = createBoldTextPaint(resources.getColor(R.color.digital_text));

            mNormalTextGreyPaint = new Paint();
            mNormalTextGreyPaint = createNormalTextPaint(resources.getColor(R.color.digital_text_grey));

            mRectPaint = new Paint();
            mRectPaint.setColor(resources.getColor(R.color.digital_text_grey));

            mBoldTempPaint = new Paint();
            mBoldTempPaint = createBoldTextPaint(resources.getColor(R.color.digital_text));

            mGreyTempPaint = new Paint();
            mGreyTempPaint = createNormalTextPaint(resources.getColor(R.color.digital_text_grey));

            mOpenAppGreyTextPaint = new Paint();
            mOpenAppGreyTextPaint = createNormalTextPaint(resources.getColor(R.color.digital_text_grey));

            antiAliasPaint = new Paint();
            antiAliasPaint.setAntiAlias(false);

            mTime = new Time();

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);

            mDateFormat = DateFormat.getMediumDateFormat(SunshineWatchface.this);
            mDateFormat.setCalendar(mCalendar);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createNormalTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBoldTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchface.this.getResources();
            isRound = insets.isRound();
            mXOffsetHours = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round_hours : R.dimen.digital_x_offset_hours);

            mXOffsetMinutes = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round_minutes : R.dimen.digital_x_offset_minutes);


            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);



            mNormalTextPaint.setTextSize(textSize);
            mBoldTextPaint.setTextSize(textSize);
            mNormalTextGreyPaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mBoldTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mGreyTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mOpenAppGreyTextPaint.setTextSize(resources.getDimension(R.dimen.open_app_text_size));
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
                    mNormalTextPaint.setAntiAlias(!inAmbientMode);
                    mBoldTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

//        /**
//         * Captures tap event (and tap type) and toggles the background color if the user finishes
//         * a tap.
//         */
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            Resources resources = SunshineWatchface.this.getResources();
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    mTapCount++;
//                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.background : R.color.background2));
//                    Log.v("@@@@@@@Background", "This was the background changing log - just a test");
//                    break;
//            }
//            invalidate();
//        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            int width = canvas.getWidth();
            int height = canvas.getHeight();

            int widthDividedByTen = width /10;
            int heightDividedByTen = height /10;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchface.this);

            mTime.setToNow();
            currentHour = mTime.hour;

            if (!is24Hour) {
                if (currentHour > 12) {
                    currentHour -= 12;
                }
                if (currentHour == 0) {
                    currentHour = 12;
                }
            }

            String mTimeHourText = String.valueOf(currentHour);

            String mTimeMinuteText = String.format("%02d", mTime.minute);

            if (isRound){
                //Time for round faces
                posYTime = heightDividedByTen * 4f;

                posXTimeHourUnderTen = widthDividedByTen * 3.4f;
                posXTimeMinutesUnderTen = widthDividedByTen * 4.4f;

                posXTimeHourOverTen = widthDividedByTen * 2.7f;
                posXTimeMinutesOverTen = widthDividedByTen * 4.8f;

                //Date for round Faces
                posYDate = heightDividedByTen * 5.5f;

                posXDateDay = widthDividedByTen * 1.9f;
                posXDateFull = widthDividedByTen * 3.9f;

                //Weather Icon for round Faces
                posXIcon = widthDividedByTen * 1.5f;
                posYIcon = heightDividedByTen * 6.5f;

                //High Low for round Watch Faces
                posYHighLow = heightDividedByTen * 8f;

                posXHigh = widthDividedByTen * 4.3f;
                posXLow = widthDividedByTen * 6.8f;


                //Please Open App Text for round faces
                posYOpenAppMessage = heightDividedByTen * 8f;
                posXOpenAppMessage = widthDividedByTen * 1.3f;

            } else{

                //Time for Square Faces
                posYTime = heightDividedByTen * 4f;

                posXTimeHourUnderTen = widthDividedByTen * 3.3f;
                posXTimeMinutesUnderTen = widthDividedByTen * 4.5f;

                posXTimeHourOverTen = widthDividedByTen * 2.5f;
                posXTimeMinutesOverTen = widthDividedByTen * 5f;

                //Date for Square Faces
                posYDate = heightDividedByTen * 5.5f;

                posXDateDay = widthDividedByTen * 1.2f;
                posXDateFull = widthDividedByTen * 3.5f;

                //Weather Icon for Square Faces
                posXIcon = widthDividedByTen / 2f;
                posYIcon = heightDividedByTen * 6.5f;

                //High Low for Square Watch Faces
                posYHighLow = heightDividedByTen * 8.5f;

                posXHigh = widthDividedByTen * 4f;
                posXLow = widthDividedByTen * 7f;


                //Please Open App Text for square faces
                posYOpenAppMessage = heightDividedByTen * 8f;
                posXOpenAppMessage = widthDividedByTen / 2f;

            }

            if (currentHour < 10) {
                canvas.drawText(mTimeHourText, posXTimeHourUnderTen, posYTime, mBoldTextPaint);
                canvas.drawText(":" + mTimeMinuteText, posXTimeMinutesUnderTen, posYTime, mNormalTextPaint);
            } else {
                canvas.drawText(mTimeHourText, posXTimeHourOverTen, posYTime, mBoldTextPaint);
                canvas.drawText(":" + mTimeMinuteText, posXTimeMinutesOverTen, posYTime, mNormalTextPaint);
            }


            // Day of week
            canvas.drawText(
                    mDayOfWeekFormat.format(mDate).toUpperCase(),
                    posXDateDay, posYDate, mNormalTextGreyPaint);
            // Date
            canvas.drawText(
                    mDateFormat.format(mDate).toUpperCase(),
                    posXDateFull, posYDate, mNormalTextGreyPaint);

            //Line for style
            canvas.drawRect(widthDividedByTen * 2, heightDividedByTen * 6, widthDividedByTen * 8, heightDividedByTen * 6.1f, mRectPaint);



            //Dont show weather if a Peek card is showing
            if (getPeekCardPosition().centerY() == 0) {
                if (interactiveWeatherIcon != null) {

                    //Color and filled graphics for interactive
                    if (isInAmbientMode() == false) {
                        Bitmap mWeatherIconScaled = interactiveWeatherIcon.createScaledBitmap(interactiveWeatherIcon, 80, 80, false);
                        canvas.drawBitmap(mWeatherIconScaled, posXIcon, posYIcon, null);

                        //Grey and unfilled shapes for burn-in protection
                    } else if (isInAmbientMode() == true){
                        Bitmap mWeatherIconScaled = ambientWeatherIcon.createScaledBitmap(ambientWeatherIcon, 80, 80, false);
                        canvas.drawBitmap(mWeatherIconScaled, posXIcon, posYIcon, antiAliasPaint);
                    }
                }


                if (highTemperature.equals("1") && lowTemperature.equals("2")) {
                    canvas.drawText(getString(R.string.open_app), posXOpenAppMessage, posYOpenAppMessage, mOpenAppGreyTextPaint);
                } else {
                    canvas.drawText(highTemperature, posXHigh, posYHighLow, mBoldTempPaint);
                    canvas.drawText(lowTemperature, posXLow, posYHighLow, mGreyTempPaint);
                }
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

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.v("  onConnect   ", " We have been connected to the device");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo(WEAR_WEATHER_PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                        setWeatherFromDataMap(dataMap.getString(WEAR_WEATHER_HIGH), dataMap.getString(WEAR_WEATHER_LOW), dataMap.getInt(WEAR_WEATHER_CONDITION));

                        Log.v("LOG THIS", "onDataChanged has been called AND OUR Weather ID IS " + dataMap.getInt(WEAR_WEATHER_CONDITION));
                        invalidate();
                    }
                }
            }

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v("   connectionFailed    ", "our Connection to the device failed");

        }

        private void setWeatherFromDataMap(String high, String low, int weatherCondition){
            this.highTemperature = high;
            this.lowTemperature = low;
            interactiveWeatherIcon = BitmapFactory.decodeResource(getResources()
                    , Utility.getInteractiveIconResource(weatherCondition));
            ambientWeatherIcon = BitmapFactory.decodeResource(getResources()
                    , Utility.getAmbientIconResource(weatherCondition));


        }



    }
}
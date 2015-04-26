package com.arborisfolium.powermeter;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.todddavies.components.progressbar.ProgressWheel;

import org.eclipse.paho.client.mqttv3.MqttException;

public class MainActivity extends Activity implements PowerClient.ClientCallback {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MAX_DEMAND_PROGRESS = 360;
    private static final int MAX_SUMMATION_PROGRESS = 100;
    private static final int DEMAND_INTERVAL = 8000; // ms
    private static final int SUMMATION_INTERVAL = 300000; // ms
    private static final String LAST_SUMMATION_KEY = "last_summation";

    private PowerClient mClient;
    private ProgressWheel mDemand;
    private TextView mSummation;
    private ProgressBar mSummationAge;
    private ObjectAnimator mDemandAnimator;
    private ObjectAnimator mDemandColorAnimator;
    private ObjectAnimator mSummationAnimator;
    private ObjectAnimator mSummationColorAnimator;
    private RavenMessage mLastSummation = null;

    private int[] getColorsFor(final Resources resources, final int[] colors) {
        final int[] retval = new int[colors.length];
        for (int idx = 0; idx < colors.length; ++idx) {
            retval[idx] = resources.getColor(colors[idx]);
        }
        return retval;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Resources resources = getResources();
        final int[] ageColorResources = new int[] { R.color.fresh_color, R.color.older_color, R.color.stale_color };
        final int[] ageColors = getColorsFor(resources, ageColorResources);
        mDemand = (ProgressWheel)findViewById(R.id.demand);
        mDemand.setText(getResources().getString(R.string.no_data));
        mDemand.setProgress(MAX_DEMAND_PROGRESS);
        mDemand.setBarColor(ageColors[ageColors.length - 1]);
        mDemandAnimator = ObjectAnimator.ofInt(mDemand, "progress", 0, MAX_DEMAND_PROGRESS);
        mDemandAnimator.setDuration(DEMAND_INTERVAL);
        mDemandAnimator.setInterpolator(new LinearInterpolator());
        mDemandColorAnimator = ObjectAnimator.ofArgb(mDemand, "barColor", ageColors);
        mDemandColorAnimator.setDuration(DEMAND_INTERVAL * ageColors.length);
        mDemandColorAnimator.setInterpolator(new LinearInterpolator());

        mSummation = (TextView)findViewById(R.id.summation);
        mSummationAge = (ProgressBar)findViewById(R.id.summation_age);
        mSummationAnimator = ObjectAnimator.ofInt(mSummationAge, "progress", 0, MAX_SUMMATION_PROGRESS);
        mSummationAnimator.setDuration(SUMMATION_INTERVAL);
        mSummationAnimator.setInterpolator(new LinearInterpolator());
        mSummationColorAnimator = ObjectAnimator.ofArgb(mSummationAge.getProgressDrawable(), "tint", ageColors);
        mSummationColorAnimator.setDuration(SUMMATION_INTERVAL * ageColors.length);
        mSummationColorAnimator.setInterpolator(new LinearInterpolator());

        if (savedInstanceState != null && savedInstanceState.containsKey(LAST_SUMMATION_KEY)) {
            mLastSummation = (RavenMessage) savedInstanceState.getSerializable(LAST_SUMMATION_KEY);
            setSummation(mLastSummation.getValue());
            final long age = System.currentTimeMillis() - (mLastSummation.getTimestamp() * 1000);
            final double ageRatio = age / SUMMATION_INTERVAL;
            final int ageProgress = (int) Math.min(ageRatio * MAX_SUMMATION_PROGRESS, MAX_SUMMATION_PROGRESS);
            Log.d(TAG, "AGE ratio : " + ageRatio);

            mSummationAge.setProgress(ageProgress);
            final int ageColorIndex = (int) Math.min(ageRatio * ageColors.length, ageColors.length - 1);
            Log.d(TAG, "AGE progress : " + ageProgress);
            Log.d(TAG, "AGE colorIndex : " + ageColorIndex);

            mSummationAge.getProgressDrawable().setTint(ageColors[ageColorIndex]);
        } else {
            mSummationAge.setProgress(MAX_SUMMATION_PROGRESS);
            mSummationAge.getProgressDrawable().setTint(ageColors[ageColors.length - 1]);
        }

        try {
            mClient = new PowerClient(this, this);
        } catch (final MqttException err) {
            Log.e(TAG, "Error instantiating PowerClient", err);

        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != mLastSummation) {
            outState.putSerializable(LAST_SUMMATION_KEY, mLastSummation);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Log.d(TAG, "About to connect");
            mClient.connect();
        } catch (final MqttException err) {
            Log.e(TAG, "Error connecting", err);
            Toast.makeText(this, R.string.mqtt_err_connecting, Toast.LENGTH_LONG);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "About to disconnect");
        mClient.disconnect();
        mDemand.setText(getResources().getString(R.string.no_data));
        mSummation.setText(R.string.no_data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setDemand(final double kw) {
        mDemand.setText(kw + " " + getResources().getString(R.string.demand_units));
    }

    private void setSummation(final double kwh) {
        mSummation.setText(kwh + " " + getResources().getString(R.string.summation_units));
    }

    @Override
    public void messageArrived(final RavenMessage message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (message.getTopic()) {
                    case "home/energy/demand":
                        setDemand(message.getValue());
                        mDemandAnimator.start();
                        mDemandColorAnimator.start();
                        break;
                    case "home/energy/summation":
                        setSummation(message.getValue());
                        mSummationAnimator.start();
                        mSummationColorAnimator.start();
                        mLastSummation = message;
                        break;
                    default:
                        Log.w(TAG, "Unhandled topic in UI: " + message.getTopic());
                }
            }
        });
    }
}

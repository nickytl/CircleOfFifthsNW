package com.example.conor.accelerometersynth;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.io.PdAudio;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;


import static java.lang.Math.pow;


public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener{
    EditText amp;
    EditText freq;

    // used in low pass filter
    float ALPHA = 0.15f;

    Float azimuth;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private LocationManager locationManager;

    Sensor accelerometer;
    Sensor magnetometer;

    float mGravity[];
    float mGeomagnetic[];

    double latitude;
    double longitude;
    double altitude;
    double dummyRadians;

    float distanceInMeters;
    float bearingDummy;
    float dummyRadiansFloat;
    float direction;
    float azimuthFloat;
    float accelX;
    float accelY;
    float accelZ;
    float accelMagnitudeBase;
    float accelMagnitude;
    float directionDegrees;

    private PdUiDispatcher dispatcher;
    private void initPD() throws IOException
    {
        int sampleRate = AudioParameters.suggestSampleRate();
        PdAudio.initAudio(sampleRate,0,2,8,true);

        dispatcher = new PdUiDispatcher();
        PdBase.setReceiver(dispatcher);
    }
    private void initGUI(){
        Switch initSynthSwitch = (Switch) findViewById(R.id.switch1);

        initSynthSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
               // Log.i("INIT","SWITCH CHANGED" + String.valueOf(isChecked));
                float val = (isChecked) ?  1.0f : 0.0f;
                sendFloatPD("onOff", val);
            }
        });
    }

    public void sendPatchData(View v)
    {
        amp   = (EditText)findViewById(R.id.ampText);
        freq   = (EditText)findViewById(R.id.freqText);
        sendFloatPD("amp", Float.parseFloat(amp.getText().toString()));
        sendFloatPD("freq", Float.parseFloat(freq.getText().toString()));
    }

    public void sendFloatPD(String receiver, Float value)
    {
        PdBase.sendFloat(receiver, value);
    }

    public void sendBangPD(String receiver)
    {
        PdBase.sendBang(receiver);
    }


    private void loadPDPatch(String patchName) throws IOException
    {
        File dir = getFilesDir();
        try {
            IoUtils.extractZipResource(getResources().openRawResource(R.raw.synth), dir, true);
            File pdPatch = new File(dir, patchName);
            PdBase.openPatch(pdPatch.getAbsolutePath());
        }catch (IOException e)
        {

        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SeekBar slider = (SeekBar) findViewById(R.id.slider1);
        slider.setOnSeekBarChangeListener(sliderValueChange);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try{
        locationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 150, 10, this);
        }
        catch (SecurityException se){}

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        try{
            initPD();
            loadPDPatch("synth.pd"); // This is the name of the patch in the zip

        }catch(IOException e)
        {
            finish();
        }
        initGUI();
    }

    private SeekBar.OnSeekBarChangeListener sliderValueChange =
            new SeekBar.OnSeekBarChangeListener(){
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    sendFloatPD("rSliderValue",Float.parseFloat(String.valueOf(progress))); //I declared the receive event for the slider as rSliderValue
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }

    };

    @Override
    protected void onResume(){
        super.onResume();
        PdAudio.startAudio(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        PdAudio.stopAudio();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onLocationChanged(Location location) {
        // get GPS data
        longitude = location.getLongitude();
        latitude = location.getLatitude();
        altitude = location.getAltitude();
    }

    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        // calculate orientation
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];

            // Get magnitude of accelerometer - sqrt(x^2 + y^2 + z^2)
            accelMagnitude = (float) ((pow((pow(accelX, 2))+(pow(accelY, 2))+(pow(accelZ, 2)), (float) 0.5)) - 9.8);
            // Get abs value of accelerometer
            accelMagnitudeBase = Math.abs(accelMagnitude);

            // low pass filter
            mGravity = lowPass(event.values.clone(), mGravity);
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = lowPass(event.values.clone(), mGeomagnetic);

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimuth = orientation[0]; //orientation contains azimuth, pitch and roll
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }
}

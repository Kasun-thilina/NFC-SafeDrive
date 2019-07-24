package com.nfc.safedrive;

import android.Manifest;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VoiceInteractionService;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.Group;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import net.gotev.speech.Speech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, LocationListener, GpsStatus.Listener,RecognitionListener
{
    public static final String TAG = MainActivity.class.getSimpleName();
    private NfcAdapter mNfcAdapter;
    private boolean isEmergency=true; //Boolean variable used to activate emergency mode on specific scenarios
    private boolean isDialogshowing=false; //Boolean variable used to dismiss the emergency mode timer in specific scenarios
    private boolean isDriving,hasDriven,isDrivingDialogShown,run=false;//Boolean variable used to detect  the driving mode
    private boolean tagDetach=false;//Boolean variable used to detect the tag detach scenarios
    private boolean isCorrect=false;//Boolean variable used to detect the correct NFC tag with the specific code in it
    private boolean isConnected=false;//Boolean variable to check Raspberry PI Connection
    private int nfcError=0,dialogCounter=0;
    private String longitude="0.0",latitude="0.0";
    Dialog dialog;
    AlertDialog errorDialog;
    private GifImageView gifScanning;
    private ImageView nfcIcon,RSPIcon;
    private ImageView gpsIcon;
    TextView txtSearching,txtSpeed;

    /**URL For Raspberry PI Should go here*/
    final URL APIURL=new URL(" https://demo7381782.mockable.io/");
    /**
     * For GPS
     */
    SpannableString s ;
    private SharedPreferences  sharedPreferences;
    private LocationManager mLocationManager;
    private static Data data;
    double speed;
    private TextView currentSpeed;
    TextView drivingMode;
    private Data.OnGpsServiceUpdate onGpsServiceUpdate;
    private boolean firstfix;
    /**For Speech recognition*/
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    boolean useNFC,showSpeed ;

    public MainActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        reqPermission();
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);
        navigation.setSelectedItemId(R.id.navigation_home);
        loadFragments(new HomeFragment());
        initNFC();
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
        /**
         * GPS
         */
        data = new Data(onGpsServiceUpdate);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        /**END
         * Begining of Speech recognition service
         * */
        // start speech recogniser
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},1);
        }
        reqPermission();
            resetSpeechRecognizer();
            setRecogniserIntent();
            speech.startListening(recognizerIntent);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        useNFC= (preferences.getBoolean("pref_nfc",true));
        showSpeed= (preferences.getBoolean("pref_speed",true));
        Speech.init(this, getPackageName());
    }
    /***
     * Raspberry Pi connection
     */
    public void sendRequest() {
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    HttpsURLConnection myConnection =
                                            (HttpsURLConnection) APIURL.openConnection();
                                    myConnection.setRequestProperty("Content-Type", "text/plain");
                                    if (myConnection.getResponseCode() == 200) {
                                        // Success
                                        InputStream responseBody = myConnection.getInputStream();
                                        InputStreamReader responseBodyReader =
                                                new InputStreamReader(responseBody, "UTF-8");
                                        BufferedReader r = new BufferedReader(new InputStreamReader(responseBody));
                                        StringBuilder total = new StringBuilder();
                                        for (String line; (line = r.readLine()) != null; ) {
                                            total.append(line);
                                        }
                                        String result = total.toString();
                                        Log.d(TAG, "JSON ResponseBody :" + result);

                                        if (result.equalsIgnoreCase("True"))
                                        {
                                            isConnected=true;
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    uiTransitions(true);
                                                }
                                            });
                                        }
                                       /* else
                                        {
                                            isConnected=false;
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    uiTransitions(false);
                                                    if (!run) {
                                                        isEmergency = true;
                                                        run=false;
                                                        activateEmergency();
                                                    }
                                                    Timer timer = new Timer();
                                                    timer.schedule(new TimerTask() {
                                                        @Override
                                                        public void run() {
                                                            //This is where we tell it what to do when it hits 2 mins
                                                            run=true;
                                                        }
                                                    }, 120000);
                                                }
                                            });
                                        }*/

                                    } else {
                                        Log.d(TAG, "**JSON Request Failed : Connection Error**");
                                        isConnected=false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                uiTransitions(false);
                                            }
                                        });
                                    }

                                    myConnection.disconnect();
                                } catch (IOException  e) {
                                    e.printStackTrace();
                                    Log.d(TAG, "JSON Request Failed(exception)");
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            uiTransitions(false);
                                        }
                                    });
                                }

                            }
                        });
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, 10000); //execute in every 10000 ms
    }


    /**
     *Bottom Navigation Bar
     */
    private boolean loadFragments(android.app.Fragment fragment)
    {
        if (fragment!=null) {
            getFragmentManager().beginTransaction().replace(R.id.fragmentContainer,fragment).commitAllowingStateLoss();

            return true;
        }
        return false;
    }
    /**Changing the Fragment on user choice*/
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        android.app.Fragment fragment=null;

        switch(menuItem.getItemId()){
            case R.id.navigation_home:
                fragment=new HomeFragment();
                break;
            case R.id.navigation_settings:
                fragment=new SettingsFragment();
                break;
        }
        return loadFragments(fragment);
    }

    /**
     * End
     */

    /**
     * NFC Reader Initialization
     */
    private void initNFC(){
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }
    /**
     * Detecting NFC tag and returning to the home screen at any time
     */
    @Override
    protected void onNewIntent(Intent intent) {
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setSelectedItemId(R.id.navigation_home);
        loadFragments(new HomeFragment());
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Log.d(TAG, "onNewIntent: "+intent.getAction());
        if(tag != null) {
            Toast.makeText(this, getString(R.string.message_tag_detected), Toast.LENGTH_SHORT).show();
            final Ndef ndef = Ndef.get(tag);
            onNfcDetected(ndef);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiTransitions(false);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter techDetected = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter[] nfcIntentFilter = new IntentFilter[]{techDetected,tagDetected,ndefDetected};
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        if(mNfcAdapter!= null)
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, nfcIntentFilter, null);
        /**
         * GPS
         */
        firstfix = true;
        if (!data.isRunning()){
            Gson gson = new Gson();
            String json = sharedPreferences.getString("data", "");
            data = gson.fromJson(json, Data.class);
        }
        if (data == null){
            data = new Data(onGpsServiceUpdate);
        }else{
            data.setOnGpsServiceUpdate(onGpsServiceUpdate);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
        else {
            if (mLocationManager.getAllProviders().indexOf(LocationManager.GPS_PROVIDER) >= 0) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
            } else {
                Log.w("MainActivity", "No GPS location provider found. GPS data display will not be available.");
            }

            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showErrorDialog("gps");
            }
            try {
                mNfcAdapter.isEnabled();
                if (useNFC) {
                    if (!mNfcAdapter.isEnabled()) {
                        showErrorDialog("nfc");
                    }
                }
            } catch (NullPointerException e) {
                if (useNFC) {
                    showErrorDialog("nonfc");
                    nfcError++;
                }
            }
            mLocationManager.addGpsStatusListener(this);
        }
        /***/
        /**
         * Speech recognition , Starting the Speech service again onResume of the app
         */
        if (recognizerIntent!=null) {
            resetSpeechRecognizer();
            speech.startListening(recognizerIntent);
        }
    }
    /**This Method envokes when a NFC tag is detected by the device*/
    public void onNfcDetected(Ndef ndef){
        reqPermission();
        new ProcessNFCTask().execute(ndef);
    }
    /**This Method envoke on device GPS location change*/
    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            speed = location.getSpeed() * 3.6;
            longitude=Double.toString(location.getLongitude());
            latitude=Double.toString(location.getLatitude());
            String units="km/h";
            s= new SpannableString(String.format(Locale.ENGLISH, "%.0f %s", speed, units));
            s.setSpan(new RelativeSizeSpan(0.45f), s.length()-units.length()-1, s.length(), 0);
            updateUI();
        }
    }
    /**This Method is Updating the Speed value and driving mode values when needed */
    private void updateUI(){
        final AlertDialog.Builder dialogIsDriving = new AlertDialog.Builder(this);
        dialogIsDriving.setCancelable(true);
        txtSpeed=findViewById(R.id.txtSpeed);
        drivingMode=findViewById(R.id.txtDriving);
        currentSpeed = findViewById(R.id.valSpeed);
        //drivingMode.setText(R.string.msg_notDriving);
       /* if (showSpeed) {
            drivingMode.setTextColor(Color.parseColor("#a5d6a7"));
        }*/
       // Log.d(TAG, "activeNFC: "+correctTAG);
          if (currentSpeed!=null) {
              String valSpeed=s.toString();
              String strSpeed="Speed: ";
              SpannableString speedValue=  new SpannableString(valSpeed);
              SpannableString speedText=  new SpannableString(strSpeed);
              speedText.setSpan(new RelativeSizeSpan(1.35f), 0,5, 0); // set size
              if (speed > 1) {
                speedValue.setSpan(new ForegroundColorSpan(Color.parseColor("#00bfa5")),0,6,0);// set color
                //txtSpeed.setText();
                if (!(gifScanning.getVisibility()==View.VISIBLE)) {
                    gpsIcon.setVisibility(View.VISIBLE);
                }
                drivingMode.setText(R.string.msg_driving);
                drivingMode.setTextColor(Color.parseColor("#4caf50"));
                isDriving = true;
                hasDriven=true;
            } else {
                speedValue.setSpan(new ForegroundColorSpan(Color.parseColor("#ef5350")),0,6,0);// set color
                drivingMode.setText(R.string.msg_notDriving);
                  drivingMode.setTextColor(Color.parseColor("#a5d6a7"));
                gpsIcon.setVisibility(View.INVISIBLE);
                isDriving=false;

                dialogIsDriving.setMessage("Are You Still Driving?");
                  dialogIsDriving.setPositiveButton(
                          "Yes",
                          new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int id) {
                                  dialog.dismiss();

                              }
                          });
                  dialogIsDriving.setNegativeButton(
                          "No",
                          new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int id) {
                                  SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                  boolean useNFC = (preferences.getBoolean("pref_nfc",true));
                                  if (useNFC){
                                      // Your switch is on
                                  } else {
                                      isEmergency=true;
                                      activateEmergency();
                                      //uiTransitions(true);
                                  }
                              }
                          });
                    if (hasDriven & (!isDrivingDialogShown)) {
                        errorDialog = dialogIsDriving.create();
                        errorDialog.show();
                        Speech.getInstance().say("Are You Still Driving?");
                        isDrivingDialogShown=true;
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                //This is where we tell it what to do when it hits 60 seconds
                                hasDriven=false;
                                isDrivingDialogShown=false;
                                Log.d(TAG, "*****Delayed" );
                            }
                        }, 60000);
                    }

            }
            /**To Show Speed Uncomment This Line*/
              if (showSpeed) {
                  currentSpeed.setText(TextUtils.concat(speedText, speedValue));
              }
        }
    }
    /**This Method set the GPS Scanner animation and NFC detected , Navigation Mode icons*/
    private void uiTransitions(boolean isfound){
        try {
            txtSearching = findViewById(R.id.txtScanningForNFC);
            Animation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(50); //You can manage the time of the blink with this parameter
            anim.setStartOffset(800);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            txtSearching.startAnimation(anim);
            gifScanning = findViewById(R.id.gifScanner);
            nfcIcon = findViewById(R.id.activeNFC);
            gpsIcon = findViewById(R.id.activeGPS);
            RSPIcon = findViewById(R.id.RSPIcon);
            if (isfound) {
                gifScanning.setVisibility(View.INVISIBLE);
                if (useNFC) {
                    //nfcIcon.setVisibility(View.VISIBLE);
                    //txtSearching.setText("Attached to NFC Holder");
                } else {
                    txtSearching.setText("Connected to Raspberry PI");
                    if (isConnected) {
                        RSPIcon.setVisibility(View.VISIBLE);
                    } else {
                        RSPIcon.setVisibility(View.INVISIBLE);
                    }
                }
                txtSearching.clearAnimation();
                if (isDriving) {
                    gpsIcon.setVisibility(View.VISIBLE);
                } else {
                    gpsIcon.setVisibility(View.INVISIBLE);
                }
            } else {
                if (useNFC) {
                    //txtSearching.setText("Searching For NFC Holder");
                } else {
                    txtSearching.setText("Searching For Raspberry PI");
                    RSPIcon.setVisibility(View.INVISIBLE);
                }

                gifScanning.setVisibility(View.VISIBLE);
                //nfcIcon.setVisibility(View.INVISIBLE);
                gpsIcon.setVisibility(View.INVISIBLE);
            }
        }
        catch (NullPointerException e)
        {
            e.printStackTrace();
        }

    }
    /** Async Task(Runs in background thread) to detect the correct NFC tag and read it in a indefinite loop */
    public class ProcessNFCTask extends AsyncTask<Ndef, NdefMessage, Void> {
        @Override
        protected void onPreExecute() {
            if (isDialogshowing) {
                dialog.dismiss();
                isDialogshowing=false;
                isEmergency = false;
            }
        }
        protected Void doInBackground(Ndef... tag) {
            Ndef ndef=tag[0];
            try
            {
                ndef.connect();
                NdefMessage ndefMessage = ndef.getNdefMessage();
                ndef.close();
                String message = new String(ndefMessage.getRecords()[0].getPayload());
                Log.d(TAG, "readFromNFC Before Pass: " + message);
                //Toast.makeText(this, "Text" + message, Toast.LENGTH_LONG).show();
                /*************Value to be checked in the NFC Tag**** write it using a NFC Read Write App as Plain Text*************/
                if (message.equals("in")) {
                    tagDetach=false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            uiTransitions(true);
                        }
                    });
                    isCorrect=true;
                    //Toast.makeText(this.getApplicationContext(), R.string.message_nfc_holder_detected, Toast.LENGTH_LONG).show();
                    while (1 == 1) {
                        ndef.connect();
                        ndefMessage = ndef.getNdefMessage();
                        message = new String(ndefMessage.getRecords()[0].getPayload());
                        //Log.d(TAG, "readFromNFCPassed: " + message);
                        TimeUnit.SECONDS.sleep(1);
                        ndef.close();
                    }
                } else {
                    //Toast.makeText(this.getApplicationContext(), R.string.message_nfc_holder_error, Toast.LENGTH_LONG).show();
                    ndef.close();
                    isCorrect=false;
                }
                /**
                 * Raspberry PI
                 */

                /**end*/

            } catch (IOException | FormatException | InterruptedException  e ) {
                e.printStackTrace();
                tagDetach=true;
                isCorrect=false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uiTransitions(false);
                    }
                });
                //Toast.makeText(this.getApplicationContext(), R.string.message_nfc_holder_detached, Toast.LENGTH_LONG).show();
            }
            catch (NullPointerException e)
            {
                e.printStackTrace();
            }
            return null;
        }
        protected void onProgressUpdate(NdefMessage... progress) {
            updateUI();
            if (isCorrect) {
                uiTransitions(true);
            }
            else
            {
                uiTransitions(false);
            }
        }

        protected void onPostExecute(Void result) {
            if (tagDetach) {
                isEmergency=true;
                activateEmergency();
            } else {
                if (isDialogshowing) {
                    dialog.dismiss();
                    //Log.d(TAG, "dissmiss fro isdialogshowing true 2 " );
                    isDialogshowing = false;
                }
            }

        }
    }

    /**
     * Generating the Emergency Mode Window and the logic to read the nfc if canceled etc
     */
    private void activateEmergency()
    {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            final String dialNo = (pref.getString("key_dialNo", "123"));
            final String message = (pref.getString("key_smsMessage", "123"));
            final String finalMessage
                    =message+" My Current Location:https://www.google.com/maps/search/?api=1&query="+latitude+','+longitude;
            String dialName = pref.getString("key_name", "Kasun");
            Log.d(TAG, "Number " + dialNo);
            final SmsManager smsManager = SmsManager.getDefault();
            final Intent dialer = new Intent(Intent.ACTION_CALL);
            dialer.setData(Uri.parse("tel:" + dialNo));
            //startActivity(dialer);
            dialog = new Dialog(this);
            dialog.setContentView(R.layout.calling_dialog);
            final Button btnCancel = dialog.findViewById(R.id.btnCancel);
            final TextView counter = dialog.findViewById(R.id.counter);

            dialog.setCanceledOnTouchOutside(false);
            if ((!isDriving) & (isConnected)){
                dialog.show();
                isDialogshowing = true;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 10; i > 0; i--) {
                        try {
                            Thread.sleep(1000);

                            final int val = i;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    counter.setText(String.valueOf(val));
                                    Log.d(TAG, "count " + val);
                                    btnCancel.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            dialog.dismiss();
                                            isEmergency = false;
                                            isDialogshowing = false;
                                        }
                                    });

                                }
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if ((!isEmergency) & isDialogshowing)
                    {
                        //Toast.makeText(getApplicationContext(), "Call Not Completed Since Not Driving", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        isDialogshowing=false;
                        Log.d(TAG, "dissmiss fro isemergeny false " );
                    }
                    if (isEmergency & isDialogshowing) {
                        dialog.dismiss();
                        isDialogshowing=false;
                        startActivity(dialer);
                        /**Sleeping the background thread for a while because call gets disconnected when sending the message at the same time in a call*/
                        try {
                            Thread.sleep(50000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        smsManager.sendTextMessage(dialNo, null, finalMessage, null, null);
                    }
                }
            }).start();
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(),ex.getMessage().toString(),
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }
        speech.startListening(recognizerIntent);
    }

    /**Requesting the needed permissions from the user*/
    private void reqPermission()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE},1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},1);
        }
        else
        {
            return;
        }
    }

    /**
     * GPS codes
     */

    @Override
    protected void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(this);
        mLocationManager.removeGpsStatusListener(this);
        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(data);
        prefsEditor.putString("data", json);
        prefsEditor.commit();

        if (recognizerIntent!=null) {
            speech.stopListening();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        stopService(new Intent(getBaseContext(), GPSService.class));
        Speech.getInstance().shutdown();
        // prevent memory leaks when activity is destroyed
    }
    @Override
    protected void onStop() {
        Log.i(TAG, "stop");
        super.onStop();
        if (speech != null) {
            speech.destroy();
        }
    }

    @Override
    public void onGpsStatusChanged (int event) {
    }

    /**Showing error messages when *GPS disabled *NFC disabled and When NFC is not supported by the Device*/
    public void showErrorDialog(String errorType){
        AlertDialog.Builder errorMessage = new AlertDialog.Builder(this);
        errorMessage.setCancelable(true);

        if (errorType.equalsIgnoreCase("gps"))
        {
            errorMessage.setMessage(R.string.errorMsg_GPSDisabled);
            errorMessage.setPositiveButton(
                    "Open Location Settings",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    });
        }
        if (errorType.equalsIgnoreCase("nfc"))
        {
           // errorMessage.setMessage(R.string.errorMsg_NFCDisabled);
            /*errorMessage.setPositiveButton(
                    "Open NFC Settings",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                        }
                    });*/
        }
        if (errorType.equalsIgnoreCase("nonfc"))
        {
            if (nfcError<=2) {
                /*errorMessage.setMessage(R.string.errorMsg_NFCDNotFound);
                errorMessage.setPositiveButton(
                        "Use the App without NFC",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });*/
            }
        }
        errorMessage.setNegativeButton(
                "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog errorDialog = errorMessage.create();
        errorDialog.show();
    }
    public static Data getData() {
        return data;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}
    /***/


    /**
     *Speech recognition Codes
     */
    private void resetSpeechRecognizer() {
            if (speech != null)
                speech.destroy();
            speech = SpeechRecognizer.createSpeechRecognizer(this);

            Log.i(TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));

            if (SpeechRecognizer.isRecognitionAvailable(this))
                speech.setRecognitionListener(this);
            else
                finish();

    }
    private void setRecogniserIntent() {

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(TAG, "onEndOfSpeech");
        speech.stopListening();
    }

    /**Recognized Speech results comes in Here*/
    @Override
    public void onResults(Bundle results) {
        Log.i(TAG, "onResults");
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";
        for (String result : matches) {
            text += result + "\n";
            if (result.equalsIgnoreCase("Emergency")) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                final Intent dialer = new Intent(Intent.ACTION_CALL);
                final String dialNo = (pref.getString("key_dialNo", "123"));
                dialer.setData(Uri.parse("tel:"+dialNo));
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CALL_PHONE},1);
                }
                else {
                    startActivity(dialer);
                }
            }
            else if (result.equalsIgnoreCase("Yes"))
            {
                if (errorDialog.isShowing())
                {
                    errorDialog.dismiss();
                }
            }
            else if (result.equalsIgnoreCase("No"))
            {
                if (errorDialog.isShowing()) {
                    errorDialog.dismiss();
                    isEmergency = true;
                    activateEmergency();
                }
            }
            else if (result.equalsIgnoreCase("Cancel"))
            {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
            //returnedText.setText(text);
            speech.startListening(recognizerIntent);
        }
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.i(TAG, "FAILED " + errorMessage);
        resetSpeechRecognizer();
        speech.startListening(recognizerIntent);

    }


    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        //Log.i(TAG, "onReadyForSpeech");
    }
    @Override
    public void onBeginningOfSpeech() {
        Log.i(TAG, "onBeginningOfSpeech");
        sendRequest();

    }

    @Override
    public void onRmsChanged(float rmsdB) {
        //Log.i(TAG, "onRmsChanged: " + rmsdB);
    }

    public String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

}

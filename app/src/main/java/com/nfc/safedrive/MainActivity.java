package com.nfc.safedrive;

import android.Manifest;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
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
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, LocationListener, GpsStatus.Listener
{
    public static final String TAG = MainActivity.class.getSimpleName();
    private NfcAdapter mNfcAdapter;
    private boolean isEmergency=true;
    private boolean isDialogshowing=false;
    private boolean isDriving=false;
    private boolean tagDetach=false;
    private boolean isCorrect=false;
    Dialog dialog;
    private GifImageView gifScanning;
    private ImageView nfcIcon;
    private ImageView gpsIcon;

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
    /**END*/


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
        /**END*/
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
        super.onResume();
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
        if (mLocationManager.getAllProviders().indexOf(LocationManager.GPS_PROVIDER) >= 0) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
        } else {
            Log.w("MainActivity", "No GPS location provider found. GPS data display will not be available.");
        }

        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showErrorDialog("gps");
        }
        try {mNfcAdapter.isEnabled();
            if (!mNfcAdapter.isEnabled()) {
                showErrorDialog("nfc");
            }
        }
        catch (NullPointerException e)
        {
            showErrorDialog("nonfc");
        }
        mLocationManager.addGpsStatusListener(this);
        /***/

    }
    public void onNfcDetected(Ndef ndef){
       // readFromNFC(ndef);
        new ProcessNFCTask().execute(ndef);
    }
    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            /*double speed=location.getSpeed() * 3.6;;
            while (1==1)
            {*/
            speed = location.getSpeed() * 3.6;
            String units="km/h";
            s= new SpannableString(String.format(Locale.ENGLISH, "%.0f %s", speed, units));
            s.setSpan(new RelativeSizeSpan(0.45f), s.length()-units.length()-1, s.length(), 0);
            updateUI();

        }
    }
    private void updateUI(){
        drivingMode=findViewById(R.id.txtDriving);
        currentSpeed = findViewById(R.id.valSpeed);
       // Log.d(TAG, "activeNFC: "+correctTAG);
          if (currentSpeed!=null) {
            currentSpeed.setText(s);
            if (speed > 10) {
                drivingMode.setText(R.string.msg_driving);
                isDriving = true;
            } else {
                drivingMode.setText(R.string.msg_notDriving);
                isDriving=false;
            }
        }
    }
    private void uiTransitions(boolean isfound){
       gifScanning=findViewById(R.id.gifScanner);
       nfcIcon=findViewById(R.id.activeNFC);
       gpsIcon=findViewById(R.id.activeGPS);
        if (isfound) {
            gifScanning.setVisibility(View.INVISIBLE);
            nfcIcon.setVisibility(View.VISIBLE);
            if (isDriving)
            {
                gpsIcon.setVisibility(View.VISIBLE);
            }
            else
            {
                gpsIcon.setVisibility(View.INVISIBLE);
            }
        }
        else
        {
            gifScanning.setVisibility(View.VISIBLE);
            nfcIcon.setVisibility(View.INVISIBLE);
            gpsIcon.setVisibility(View.INVISIBLE);
        }


    }

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
                // Log.d(TAG, "readFromNFC Before Pass: " + message);
                //Toast.makeText(this, "Text" + message, Toast.LENGTH_LONG).show();

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

            } catch (IOException | FormatException | InterruptedException e ) {
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
            return null;
        }
        protected void onProgressUpdate(NdefMessage... progress) {
            //gifScanning.setVisibility(View.INVISIBLE);
            //Toast.makeText(getApplicationContext(), R.string.message_nfc_holder_error, Toast.LENGTH_LONG).show();
            updateUI();
            if (isCorrect) {
                uiTransitions(true);
            }
            else
            {
                uiTransitions(false);
            }
            //uiTransitions(false);
        }

        protected void onPostExecute(Void result) {
            if (tagDetach) {
                isEmergency=true;
                activateEmergency();
            } else {
                if (isDialogshowing) {
                    dialog.dismiss();
                    Log.d(TAG, "dissmiss fro isdialogshowing true 2 " );
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

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            String dialNo = (pref.getString("key_dialNo", "0770000001"));
            String dialName = pref.getString("key_name", "Kasun");
            Log.d(TAG, "Number " + dialNo);
            final Intent dialer = new Intent(Intent.ACTION_CALL);
            dialer.setData(Uri.parse("tel:" + dialNo));
            //startActivity(dialer);
            dialog = new Dialog(this);
            dialog.setContentView(R.layout.calling_dialog);
            final Button btnCancel = dialog.findViewById(R.id.btnCancel);
            final TextView counter = dialog.findViewById(R.id.counter);

            dialog.setCanceledOnTouchOutside(false);
            if (isDriving) {
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
                        startActivity(dialer);
                        dialog.dismiss();
                        isDialogshowing=false;
                    }
                }
            }).start();

    }


    private void reqPermission()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE},1);
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
        else
        {
            return;
        }
    }

    /**
     * GPS
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
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        stopService(new Intent(getBaseContext(), GPSService.class));
    }


    @Override
    public void onGpsStatusChanged (int event) {
    }

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
            errorMessage.setMessage(R.string.errorMsg_NFCDisabled);
            errorMessage.setPositiveButton(
                    "Open NFC Settings",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                        }
                    });
        }
        if (errorType.equalsIgnoreCase("nonfc"))
        {
            errorMessage.setMessage(R.string.errorMsg_NFCDNotFound);
            errorMessage.setPositiveButton(
                    "Use the App without NFC",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
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
}

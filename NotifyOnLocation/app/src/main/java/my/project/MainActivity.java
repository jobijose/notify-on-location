package my.project;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivity";
    private static final int PLACE_PICKER_REQUEST = 1;
    private static final int CONTACTS_PICKER = 2;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final long GPS_TIME_INTERVAL = 10000;
    private static final float GPS_DISTANCE = 100f;
    private static final float NOTIFY_DISTANCE = 1000f;
    private static final int PERMISSION_SEND_SMS = 1;
    private static final int HANDLER_DELAY = 1000*10; // 10 sec delay
    private static final int SMS_FREQUENCY = 3;
    private static final int NOTIFICATION_ID = 10303;
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private static final int DEFAULT_ZOOM = 15;

    private EditText textPlacePicker;
    private EditText textContact;
    private GoogleMap mMap;
    private boolean mLocationPermissionGranted;
    private CameraPosition mCameraPosition;

    private GeoDataClient mGeoDataClient;
    private PlaceDetectionClient mPlaceDetectionClient;
    private FusedLocationProviderClient mFusedLocationProviderClient;

    private Location mLastKnownLocation;

    private LocationListener locListener = null;
    private LocationManager locMan = null;
    private Location destination = null;
    private Button startButton;
    private Handler handler;
    private NotifyFrequency notifyFrequency;
    private Intent serviceIntent;
    private NotificationManagerCompat notificationManager;
    private Notification appNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }
        setContentView(R.layout.activity_scrolling);
        textPlacePicker = findViewById(R.id.text_place_picker);
        textContact = findViewById(R.id.contact_number);
        startButton = findViewById(R.id.action_start);

        mGeoDataClient = Places.getGeoDataClient(this);

        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapView);
        mapFragment.getMapAsync(this);

        SharedPreferences config = getPreferences(Context.MODE_PRIVATE);
        String contactNum = config.getString(getString(R.string.contact_number), "");
        if(!"".equals(contactNum))
           textContact.setText(contactNum);
    }

    public void callPlacePicker(View view) {

        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        try {
            startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestcode, int resultcode, Intent intent) {

        switch (requestcode) {
            case PLACE_PICKER_REQUEST:
                placePickerActivityResult(resultcode, intent);
                break;
            case CONTACTS_PICKER:
                contactsActivityResult(resultcode, intent);
                break;
        }
    }

    public void getContacts(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, CONTACTS_PICKER);
    }

    public void placePickerActivityResult(int resultcode, Intent intent) {
        if (resultcode == RESULT_OK) {
            Place place = PlacePicker.getPlace(getApplicationContext(), intent);
            if (place != null) {
                textPlacePicker.setText(place.getName());
                destination = new Location("");
                destination.setLatitude(place.getLatLng().latitude);
                destination.setLongitude(place.getLatLng().longitude);

                Log.i(TAG, "Location : " + place.getLatLng().toString());
            }
        }
    }

    public void contactsActivityResult(int resultcode, Intent intent) {
        if (resultcode == Activity.RESULT_OK) {
            Uri contactData = intent.getData();
            try (Cursor cur = getContentResolver().query(contactData, null, null, null, null)) {
                if (cur == null) return;
                if (cur.moveToFirst()) {
                    int phoneIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String contactNmbr = cur.getString(phoneIndex);
                    textContact.setText(contactNmbr);
                    SharedPreferences config = getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = config.edit();
                    editor.putString(getString(R.string.contact_number), contactNmbr);
                    editor.commit();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getLocationPermission();

        updateLocationUI();

        getDeviceLocation();
    }

    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    private void getDeviceLocation() {

        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null)
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(mLastKnownLocation.getLatitude(),
                                                mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Exception: " + e.getMessage());
        }
    }
    public void sendNotification() {
        String messageToSend = getString(R.string.notification_text_message);
        if(textContact != null) {
            Log.i(TAG, textContact.getText().toString());
            String number = textContact.getText().toString();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_SEND_SMS);
            } else {
                SmsManager.getDefault().sendTextMessage(number, null, messageToSend, null, null);
            }
        }
    }
    public void startAction(View view) {
        if(textContact.getText() == null || "".equals(textContact.getText().toString())){
            Toast.makeText(getApplicationContext(), "Please select the valid contact number", Toast.LENGTH_LONG).show();
            return;
        }
        if(textPlacePicker.getText() == null || "".equals(textPlacePicker.getText().toString())) {
            Toast.makeText(getApplicationContext(), "Please select the valid place", Toast.LENGTH_LONG).show();
            return;
        }
        handler = new Handler();
        notifyFrequency = new NotifyFrequency(handler);
        handler.postDelayed(notifyFrequency, 0);
        startButton.setEnabled(false);
        // Start it as a background service
        serviceIntent = new Intent(MainActivity.this, BackgroundService.class);
        startService(serviceIntent);
        notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, appNotification);
        Toast.makeText(getApplicationContext(), getString(R.string.navigation_enabled), Toast.LENGTH_SHORT).show();
    }
    public void stopAction(View view){
        stopAction();
        Toast.makeText(getApplicationContext(), getString(R.string.navigation_disabled), Toast.LENGTH_SHORT).show();
    }
    public void stopAction() {
        if(handler != null && notifyFrequency != null)
            handler.removeCallbacks(notifyFrequency);
        handler = null;
        if(locMan != null)
            locMan.removeUpdates(locListener);
        locMan = null;
        destination = null;
        if(serviceIntent != null)
            stopService(serviceIntent);
        if(notificationManager != null)
            notificationManager.cancel(NOTIFICATION_ID);
        startButton.setEnabled(true);
    }
    private Location obtainLocation() {
        if(locMan == null)
            locMan  = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(locListener == null)
            locListener = new LocationListener();
        Location gpslocation = null;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG,"Location not enabled");
        } else {
            gpslocation = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                GPS_TIME_INTERVAL, GPS_DISTANCE, locListener);
        return gpslocation;
    }

    @Override
    protected void onStart() {
        super.onStart();
        createAppNotification();
    }

    private void createAppNotification() {

        Intent appIntent = new Intent(this, MainActivity.class);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent, 0);

        appNotification = new NotificationCompat.Builder(this, createNotificationChannel())
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(getString(R.string.app_title))
                .setContentText(getString(R.string.content_text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(true).build();
    }

    public String createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String channelId = "channel_messaging_1";
            NotificationChannel notificationChannel =
                    new NotificationChannel(channelId, getString(R.string.app_title), NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription(getString(R.string.app_name));
            notificationChannel.enableVibration(true);
            notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);

            return channelId;
        } else {
            return null;
        }
    }
    private class NotifyFrequency implements Runnable {
        private int smsFreq = 0;
        private Handler handler;
        public NotifyFrequency(Handler handler) {
            this.handler=handler;
        }

        @Override
        public void run() {
            if(handler != null) {
                Location myLocation = obtainLocation();
                if (myLocation != null && destination != null) {
                    double distance = myLocation.distanceTo(destination);
                    if (distance <= NOTIFY_DISTANCE) {
                        if (smsFreq < SMS_FREQUENCY) {
                            sendNotification();
                            smsFreq++;
                        }
                    }
                }
                handler.postDelayed(this, HANDLER_DELAY);
            }
        }
    }
}

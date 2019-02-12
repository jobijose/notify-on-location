package my.project;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class LocationListener implements android.location.LocationListener {
    private static final String TAG = "LocationListener";
    Location mLastLocation = null;

    public LocationListener() {
        mLastLocation = new Location(LocationManager.PASSIVE_PROVIDER);
    }
    @Override
    public void onLocationChanged(Location location) {
        mLastLocation.set(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i(TAG, "Location status changed "+provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i(TAG, "Provider changed "+provider);
    }
}

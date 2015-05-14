package com.amap.apis.locationservice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;

/**  
 */
public class LocationBackGroundService extends Service implements
		AMapLocationListener {

	public static final int START_LOCATE = 1001;

	public static final int STOP_LOCATE = 1002;

	public static final int ADD_GEOFENCE = 1003;

	public static final int REMOVE_GEOFENCE = 1004;
	
	public static final int ON_LOCATION=1005;
	
	public static final int ON_STOP=1006;
	

	public static final String PARAMETER_KEY = "parameter";

	public static final String TYPE_KEY = "providerType";

	public static final String INTERVAL_KEY = "timeinterval";

	public static final String DISTANCE_KEY = "distancekey";

	public static final String LOCATION_KEY = "locationkey";

	public static final String GPS_KEY = "gpskey";

	public static final String LATITUDE_KEY = "latitudekey";

	public static final String LONGITUDE_KEY = "longitudekey";

	public static final String GEOFENCE_DISTANCE_KEY = "geofencedistancekey";

	public static final String DURATION_KEY = "durationkey";

	public static final String GEOFENCEID_KEY = "geofenceidkey";

	public static final String GEOFENCE_BROADCAST_ACTION = "geofencebroadcast:";

	public static final String GEOFENCE_ADDSTATUS_KEY = "geofenceaddstatus";

	private ConcurrentHashMap<String, PendingIntent> mGeofences = new ConcurrentHashMap<String, PendingIntent>();

	//private Messenger mClientMessenger;

	private ArrayList<Messenger> mClientMessengers = new ArrayList<Messenger>();

	private Messenger mGeofenceClientMessenger;
	
	private boolean mIsFirstStart=true;
	
 

	private Handler mHandler = new Handler() {
		public void handleMessage(Message message) {
			switch (message.what) {
			case START_LOCATE:
				Bundle bundle = message.getData();
				String locationType = bundle.getString(TYPE_KEY);
				long timeInterval = bundle.getLong(INTERVAL_KEY);
				float distance = bundle.getFloat(DISTANCE_KEY);
				boolean isGPS = bundle.getBoolean(GPS_KEY);
				Messenger clientMessenger = message.replyTo;
				if (!mClientMessengers.contains(clientMessenger)) {
					mClientMessengers.add(clientMessenger);
				}
				startLocate(locationType, timeInterval, distance, isGPS);
				break;
			case STOP_LOCATE:
				Messenger stopClientMessenger = message.replyTo;
				
				mClientMessengers.remove(stopClientMessenger);		
				stopLocate(stopClientMessenger);
				
				break;
			case ADD_GEOFENCE:
				Bundle geoFenceBundle = message.getData();
				double latitude = geoFenceBundle.getDouble(LATITUDE_KEY);
				double longitude = geoFenceBundle.getDouble(LONGITUDE_KEY);
				float geoFenceDistance = geoFenceBundle
						.getFloat(GEOFENCE_DISTANCE_KEY);
				long duration = geoFenceBundle.getLong(DURATION_KEY);
				String geofenceID = geoFenceBundle.getString(GEOFENCEID_KEY);
				mGeofenceClientMessenger = message.replyTo;
				addGeoFence(latitude, longitude, geoFenceDistance, duration,
						geofenceID);

				break;
			case REMOVE_GEOFENCE:
				Bundle removeBundle = message.getData();
				String[] geofenceIDs = removeBundle
						.getStringArray(GEOFENCEID_KEY);
				mGeofenceClientMessenger = message.replyTo;
				removeGeofence(geofenceIDs);
				break;

			}
		}
	};

	Messenger mMessenger = new Messenger(mHandler);

	private void startLocate(String locationType, long interval,
			float distance, boolean isGPS) {
		if(mIsFirstStart)
		{
		LocationManagerProxy.getInstance(getApplicationContext()).setGpsEnable(
				isGPS);
		
		LocationManagerProxy.getInstance(getApplicationContext())
				.requestLocationData(locationType, interval, 0, this);
		mIsFirstStart=false;
		}
		
	}

	private void stopLocate(Messenger messenger) {
		Log.i("yiyi.qi", "-------stopLocate-------"+mClientMessengers.size());
		if (mClientMessengers.size() == 0) {
			LocationManagerProxy.getInstance(getApplicationContext())
					.removeUpdates(this);
			 
//				Message msg=new Message();
//				msg.what=ON_STOP;
//				try {
//					messenger.send(msg);
//				} catch (RemoteException e) {
//					e.printStackTrace();  
//				}
				
		 
		}
	}

	private void addGeoFence(double latitude, double longitude, float distance,
			long duration, String geofenceID) {

		if (mGeofences.containsKey(geofenceID)) {
			Message message = new Message();
			message.what = ADD_GEOFENCE;
			Bundle data = new Bundle();
			data.putString(GEOFENCEID_KEY, geofenceID);
			data.putBoolean(GEOFENCE_ADDSTATUS_KEY, false);
			message.setData(data);
			try {
				mGeofenceClientMessenger.send(message);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			return;
		}

		Intent intent = new Intent(GEOFENCE_BROADCAST_ACTION + geofenceID);

		PendingIntent peddingIntent = PendingIntent.getBroadcast(
				getApplicationContext(), 0, intent, 0);
		LocationManagerProxy.getInstance(getApplicationContext())
				.addGeoFenceAlert(latitude, longitude, distance, duration,
						peddingIntent);
		mGeofences.put(geofenceID, peddingIntent);

		Message message = new Message();
		message.what = ADD_GEOFENCE;
		Bundle data = new Bundle();
		data.putString(GEOFENCEID_KEY, geofenceID);
		data.putBoolean(GEOFENCE_ADDSTATUS_KEY, true);
		message.setData(data);
		try {
			mGeofenceClientMessenger.send(message);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}

	private void removeGeofence(String[] geofenceIDs) {
		List<String> removeIDs = new ArrayList<String>();
		for (String geoFenceID : geofenceIDs) {
			PendingIntent geofencePendding = mGeofences.get(geoFenceID);
			if (geofencePendding != null) {
				LocationManagerProxy.getInstance(getApplicationContext())
						.removeGeoFenceAlert(geofencePendding);
				removeIDs.add(geoFenceID);
			}
		}
		Message message = new Message();
		message.what = REMOVE_GEOFENCE;
		Bundle data = new Bundle();
		data.putStringArray(GEOFENCEID_KEY,
				removeIDs.toArray(new String[removeIDs.size()]));
		message.setData(data);
		try {
			mGeofenceClientMessenger.send(message);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		 
		 
		return mMessenger.getBinder();
	

	
	}

	public boolean onUnBind(Intent intent) {
 
	 
		return false;
	}

	@Override
	public void onLocationChanged(Location location) {

	}

	@Override
	public void onProviderDisabled(String provider) {

	}

	@Override
	public void onProviderEnabled(String provider) {

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {

	}

	@Override
	public void onLocationChanged(AMapLocation amapLocation) {
		if (amapLocation != null
				&& amapLocation.getAMapException().getErrorCode() == 0) {
			GDLocation gdLocation = new GDLocation();
			gdLocation.setAMapLocaion(amapLocation);	 
				Message message = new Message();
				message.what=ON_LOCATION;
				Bundle bundle = new Bundle();
				bundle.putParcelable(LOCATION_KEY, gdLocation);
				message.setData(bundle);
				try {
					for(Messenger clientMessenger:mClientMessengers){
						clientMessenger.send(message);
					}
					
					Log.i("yiyi.qi", "size is "+mClientMessengers.size());
				
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			 
			// todo回调
		} else {
			Log.i("yiyi.qi", "it has exception"
					+ amapLocation.getAMapException().getErrorCode());
		}
	}

	public void onDestroy() {
		super.onDestroy();
		Log.i("yiyi.qi", "-------service destroy-------");
		LocationManagerProxy.getInstance(getApplicationContext()).destroy();

	}

}

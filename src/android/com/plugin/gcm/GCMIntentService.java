package com.plugin.gcm;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
            // Get application name
            String appName = getAppName(this);

            Log.d(TAG, "onMessage - extras: " +  extras);

            // Checking if push is from Parse
            // Because they send the payload within data
            if(extras.getString("data") != null && extras.getString("data").length() != 0) {
                try
                {
                    JSONObject json = new JSONObject(extras.getString("data"));

                    Log.e(TAG, "onMessage - JSON data: " + json);

                    // check common case: if the Message Type is Plain Text
                    // "alert" could be also part of a JSON Message Type too
                    if(json.has("alert")) {
                        extras.putString("message", json.optString("alert"));
                    } else if(json.has("message")) {
                        extras.putString("message", json.optString("message"));
                    }

                    // If no title is provided use the application name
                    if(json.has("title")) {
                        extras.putString("title", json.optString("title"));
                    } else {
                        extras.putString("title", appName);
                    }

                    // if set, allow multiple notifications
                    if(json.has("notId")) {
                        extras.putString("notId", json.optString("notId"));
                    }

                    // get message count if it exist
                    if(json.has("msgcnt") && json.optString("msgcnt") != "") {
                        extras.putString("msgcnt", json.optString("msgcnt"));
                    }
                }
                catch( JSONException e)
                {
                    Log.e(TAG, "onMessage: JSON exception from Parse payload");
                }
            }

			// if we are in the foreground, just surface the payload, else post it to the statusbar
            if (PushPlugin.isInForeground()) {
				extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
			}
			else {
				extras.putBoolean("foreground", false);

                // Send a notification if there is a message
                if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                    createNotification(context, extras, appName);
                }
            }
        }
	}

	public void createNotification(Context context, Bundle extras, String appName)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setSmallIcon(context.getApplicationInfo().icon)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setTicker(extras.getString("title"))
				.setContentIntent(contentIntent)
				.setAutoCancel(true);

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}

		int notId = 0;

		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}

		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}

	private static String getAppName(Context context)
	{
		CharSequence appName =
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());

		return (String)appName;
	}

	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

}

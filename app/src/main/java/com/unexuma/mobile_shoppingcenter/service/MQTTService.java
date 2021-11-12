package com.unexuma.mobile_shoppingcenter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.IOException;
import java.util.Random;


import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.unexuma.mobile_shoppingcenter.MainActivity;
import com.unexuma.mobile_shoppingcenter.R;
import com.unexuma.mobile_shoppingcenter.notification.NotificationClass;
import com.unexuma.mobile_shoppingcenter.notification.NotificationDatabase;
import com.unexuma.mobile_shoppingcenter.resource.UserResource;
import com.unexuma.mobile_shoppingcenter.response.UserResponse;


public class MQTTService extends Service {

    private static final String TAG = "MqttMessageService";
    private MqttClient mqttClient;
    private static MqttAndroidClient mqttAndroidClient;
    public static Boolean subscribed = false;

    //Client ID
    private AdvertisingIdClient.Info mInfo;

    Gson gson = new GsonBuilder().create();

	public MQTTService() {
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mInfo = null;
        new GetAdvertisingID().execute();

    }

    public static MqttAndroidClient getClient(){
        return mqttAndroidClient;
    }


    private void configureMQTT(){
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());

        mqttClient = new MqttClient();


        mqttAndroidClient = mqttClient.getMqttClient(getApplicationContext(), MQTTConfiguration.MQTT_BROKER_URL, mInfo.getId());

        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                if (!subscribed) {
                    subscribeTopic(getApplicationContext(), "ShoppingCenterMobile");
                    Log.d(TAG, "Subscribed to request");

                    //Change state on MainActivity TextView
                    Intent intentDevice = new Intent();
                    intentDevice.putExtra("state",true);
                    intentDevice.setAction("STATE");
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentDevice);

                }
            }

            @Override
            public void connectionLost(Throwable throwable) {
                Log.d(TAG, "Service connection lost");
                subscribeTopic(getApplicationContext(), "ShoppingCenterMobile");

                //Change state on MainActivity TextView
                Intent intentDevice = new Intent();
                intentDevice.putExtra("state",false);
                intentDevice.setAction("STATE");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentDevice);

            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                Log.d(TAG, " - Message!!");

                // Parse message
                String msg = new String(mqttMessage.getPayload());
                JSONObject json = new JSONObject(msg);

                Log.i("Msg received by MQTT: ", json.toString());
                executeAPI(json);

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }

    private void executeAPI(JSONObject data) throws JSONException {

			switch (data.getString("resource")){

			case "User":
                try {
                    UserResponse userResponse = gson.fromJson(String.valueOf(data), UserResponse.class);
                     Exception error = new UserResource(getApplicationContext()).executeMethod(userResponse);

                     if(error!=null) {
                        Log.e("Error", error.toString());
                        break;
                     }

                    //TODO Choose what type of notification to show (toast or notification in the bar)
                    showNotification("Resource Execution: "+ userResponse.getResource()," Method: "+ userResponse.getMethod());
                   // showToastInIntentService("Resource Execution: "+ statusresponse.getResource() + " | Method: "+ statusresponse.getMethod());

                    //Intent, Broadcast, Insert Notification to send and show info to MainActivity to show in ListView

                    Intent intentStatus = new Intent();
                    intentStatus.setAction("NOW");

                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentStatus);
                    new AsyncInsertNotification().execute(new NotificationClass(userResponse.getResource(),userResponse.getMethod()));
                    //////

                } catch (Exception e) {
                    Log.e("Err StatusResponse", e.getMessage());
                }
				break;
        	}
    }


        private class GetAdvertisingID extends AsyncTask<Void, Void, AdvertisingIdClient.Info> {

        @Override
        protected AdvertisingIdClient.Info doInBackground(Void... voids) {
            AdvertisingIdClient.Info info = null;
            try {
                info = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            }
            return info;
        }

        @Override
        protected void onPostExecute(AdvertisingIdClient.Info info) {
            mInfo = info;

            configureMQTT();
        }
    }

    private void showToastInIntentService(final String sText) {
        final Context MyContext = this;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast toast1 = Toast.makeText(MyContext, sText, Toast.LENGTH_LONG);
                toast1.show();
            }
        });
    };

     private void showNotification(String title, String body) {

        //Intent to open APP when click in the notification.
        Intent resultIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String NOTIFICATION_CHANNEL_ID="1";

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel notificationChannel= new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Notification", NotificationManager.IMPORTANCE_DEFAULT);

            notificationChannel.setDescription ("Android Server");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.BLUE);
            notificationManager.createNotificationChannel(notificationChannel);
        }

       NotificationCompat.Builder notificationBuilder= new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setAutoCancel(true).setAutoCancel(true).setDefaults(Notification.DEFAULT_ALL).setContentIntent(resultPendingIntent).setWhen(System.currentTimeMillis()).setSmallIcon(android.R.drawable.alert_light_frame).setContentTitle(title).setContentText(body);
        notificationManager.notify((new Random().nextInt()),notificationBuilder.build());

    }

    class AsyncInsertNotification extends AsyncTask<NotificationClass,Void,Void> {

        @Override
        protected Void doInBackground(NotificationClass... notifications) {
            NotificationDatabase db= NotificationDatabase.getInstance(getApplicationContext());
            db.notificationDAO().insertNotification(notifications[0]);

            return null;
        }
    }

    private void startMyOwnForeground() {


        String NOTIFICATION_CHANNEL_ID = "org.openapitools.server";
        String channelName = "Background Service";
        NotificationChannel chan = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        }else{




        NotificationCompat.Builder notificationBuilder = new
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(this.getString(R.string.app_name))
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);}
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void subscribeTopic(Context ctx, String topic) {
        if (!topic.isEmpty()) {
            try {
                mqttClient.subscribe(mqttAndroidClient, topic, 1);

                Toast.makeText(ctx, "Subscribed to: " + topic, Toast.LENGTH_SHORT).show();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }


}
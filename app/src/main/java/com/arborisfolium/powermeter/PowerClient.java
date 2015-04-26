package com.arborisfolium.powermeter;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.provider.Settings;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import javax.net.ssl.SSLSocketFactory;

public class PowerClient implements MqttCallback {
    public static final String SERVER_URI = "ssl://10.21.39.10";
    public static final String CLIENT_ID_BASE = "com.arborisfolium.powermeter.";
    public static final String TOPIC_FILTER = "home/energy/#";
    public static final String TAG = PowerClient.class.getSimpleName();

    private Context mContext;
    private MqttClient mClient;
    private ClientCallback mCallback;

    public PowerClient(final Context context, final ClientCallback callback) throws MqttException {
        mContext = context;
        final String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        final MemoryPersistence persistence = new MemoryPersistence();
        mClient = new MqttClient(SERVER_URI, CLIENT_ID_BASE + androidId, persistence);

        mCallback = callback;
    }

    public void connect() throws MqttException {
        mClient.setCallback(this);
        Log.d(TAG, "Connecting to server");
        final SSLSocketFactory insecureSocketFactory = SSLCertificateSocketFactory.getInsecure(0, null);
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(insecureSocketFactory);
        mClient.connect(options);
        Log.d(TAG, "Subscribing to " + TOPIC_FILTER);
        mClient.subscribe(TOPIC_FILTER);
        Toast.makeText(mContext, R.string.mqtt_connected, Toast.LENGTH_SHORT);
    }

    public void disconnect() {
        try {
            mClient.disconnect();
        } catch (final MqttException err) {
            Log.e(TAG, "Error disconnecting", err);
        }
    }

    public interface ClientCallback {
        public void messageArrived(final RavenMessage message);
    }

    @Override
    public void connectionLost(final Throwable cause) {
        Log.w(TAG, "Connection lost", cause);
        Toast.makeText(mContext, R.string.mqtt_lost, Toast.LENGTH_LONG);
    }

    @Override
    public void messageArrived(final String topic, final MqttMessage mqttMessage) throws Exception {
        final InputStream inStream = new ByteArrayInputStream(mqttMessage.getPayload());
        JsonReader reader = new JsonReader(new InputStreamReader(inStream, Charset.defaultCharset()));
        final RavenMessage msg = parseBody(reader, topic);
        mCallback.messageArrived(msg);
    }

    private RavenMessage parseBody(final JsonReader reader, final String topic) throws IOException {
        RavenMessage message = new RavenMessage();
        message.setTopic(topic);
        reader.beginObject();
        while (reader.hasNext()) {
            final String name = reader.nextName();
            switch (name) {
                case "ts":
                    message.setTimestamp(reader.nextLong());
                    break;
                case "val":
                    message.setValue(reader.nextDouble());
                    break;
                default:
                    Log.w(TAG, "Unhandled JSON element: " + name + " : " + reader.nextString());
            }
        }
        reader.endObject();
        return message;
    }

    @Override
    public void deliveryComplete(final IMqttDeliveryToken token) {
    }
}

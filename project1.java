import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        // Load config from external file
        Properties config = new Properties();
        try {
            config.load(Main.class.getResourceAsStream("config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String mqttBrokerAddress = config.getProperty("mqtt.broker.address");
        String mqttTopicVolume = config.getProperty("mqtt.topic.volume");
        String mqttTopicMessage = config.getProperty("mqtt.topic.message");
        String udpServerAddress = config.getProperty("udp.server.address");
        int udpServerPort = Integer.parseInt(config.getProperty("udp.server.port"));

        MqttClient mqttClient;
        try {
            mqttClient = new MqttClient(mqttBrokerAddress, MqttClient.generateClientId(), new MemoryPersistence());
            mqttClient.connect();
            mqttClient.subscribe(mqttTopicVolume, (topic, message) -> {
                int volume = Integer.parseInt(new String(message.getPayload()));
                adjustVolume(volume);
                System.out.println("Adjusted volume to " + volume + "%");
            });
            mqttClient.subscribe(mqttTopicMessage, (topic, message) -> {
                sendMessageViaUDP(new String(message.getPayload()), udpServerAddress, udpServerPort);
            System.out.println("Sent message via UDP: " + new String(message.getPayload()));
        });
    } catch(MqttException e) {
        e.printStackTrace();
    }
}

    private static void adjustVolume(int volume) {
    // Implement volume adjustment logic here
}

    private static void sendMessageViaUDP(String message, String serverAddress, int serverPort) {
    try (DatagramSocket socket = new DatagramSocket()) {
        byte[] sendData = message.getBytes("KOI8");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(serverAddress), serverPort);
        socket.send(sendPacket);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
}

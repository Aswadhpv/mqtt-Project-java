import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;

public class Main {

    private static final String CONFIG_FILE = "config.json";

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static FileHandler fileHandler;

    static {
        try {
            fileHandler = new FileHandler("application.log");
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JSONObject loadConfig() {
        JSONParser parser = new JSONParser();

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            return (JSONObject) parser.parse(reader);
        } catch (IOException | ParseException e) {
            logger.log(Level.SEVERE, "Error loading config", e);
            return null;
        }
    }

    private static void adjustVolume(int volume, String alsaDevice) {
        try {
            // Adjust volume using ALSA
            String[] command = {"amixer", "-D", alsaDevice, "sset", "Master", volume + "%"};
            Process process = new ProcessBuilder(command).start();
            process.waitFor();
            logger.info("Volume adjusted to " + volume + "%");
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error adjusting volume", e);
        }
    }

    private static void sendMessageViaUDP(String message, String serverAddress, int serverPort) {
        try {
            // Send message via UDP
            byte[] sendData = message.getBytes("KOI8");
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(serverAddress), serverPort);
            socket.send(sendPacket);
            socket.close();
            logger.info("Message sent via UDP: " + message);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending UDP message", e);
        }
    }

    public static void main(String[] args) {
        JSONObject config = loadConfig();

        if (config == null) {
            logger.severe("Failed to load config.");
            return;
        }

        String mqttBrokerAddress = (String) config.getJSONObject("mqtt").get("broker_address");
        String mqttTopicVolume = (String) config.getJSONObject("mqtt").get("topic_volume");
        String mqttTopicMessage = (String) config.getJSONObject("mqtt").get("topic_message");
        String udpServerAddress = (String) config.getJSONObject("udp").get("server_address");
        int udpServerPort = Integer.parseInt((String) config.getJSONObject("udp").get("server_port"));
        String alsaDevice = (String) config.getJSONObject("alsa").get("device");

        // MQTT Setup
        MqttClient mqttClient;
        try {
            mqttClient = new MqttClient(mqttBrokerAddress, MqttClient.generateClientId(), new MemoryPersistence());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    logger.warning("Connection to MQTT Broker lost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload(), "UTF-8");

                    if (topic.equals(mqttTopicVolume)) {
                        int volume = Integer.parseInt(payload);
                        adjustVolume(volume, alsaDevice);
                    } else if (topic.equals(mqttTopicMessage)) {
                        sendMessageViaUDP(payload, udpServerAddress, udpServerPort);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used
                }
            });

            mqttClient.connect();
            mqttClient.subscribe(mqttTopicVolume);
            mqttClient.subscribe(mqttTopicMessage);
        } catch (MqttException | IOException e) {
            logger.log(Level.SEVERE, "Error connecting to MQTT Broker", e);
        }
    }
}

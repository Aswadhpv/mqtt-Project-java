import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Project1 {

    private static final String CONFIG_FILE = "config.json";
    private static final Logger logger = Logger.getLogger(Project1.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        PropertyConfigurator.configure("log4j.xml");

        JSONObject config = loadConfig();

        if (config == null) {
            logger.error("Failed to load config.");
            return;
        }

        MqttClient mqttClient = createMqttClient(config);

        if (mqttClient == null) {
            logger.error("Failed to create MQTT client.");
            return;
        }

        MessageSender messageSender = new MessageSender(config.getJSONObject("udp"));
        AudioAdjuster audioAdjuster = new AudioAdjuster(config.getJSONObject("alsa"));

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger.warn("Connection to MQTT Broker lost");
                // Try to reconnect
                tryReconnect(mqttClient, config);
            }

           @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
                if (topic.equals(config.getJSONObject("mqtt").getString("topic_volume"))) {
                    adjustVolume(Integer.parseInt(payload), config);
                } else if (topic.equals(config.getJSONObject("mqtt").getString("topic_message"))) {
                    sendMessage(payload, config);
                }
            }


            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used
            }
        });

        try {
            mqttClient.connect();
            mqttClient.subscribe(config.getJSONObject("mqtt").getString("topic_volume"));
            mqttClient.subscribe(config.getJSONObject("mqtt").getString("topic_message"));
        } catch (MqttException e) {
            logger.error("Error connecting to MQTT Broker", e);
        }

        // Periodically check MQTT connection
        scheduler.scheduleAtFixedRate(() -> checkMqttConnection(mqttClient), 0, 30, TimeUnit.SECONDS);
    }

    private static JSONObject loadConfig() {
        JSONParser parser = new JSONParser();

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            return (JSONObject) parser.parse(reader);
        } catch (IOException | ParseException e) {
            logger.error("Error loading config", e);
            return null;
        }
    }

    private static MqttClient createMqttClient(JSONObject config) {
        String brokerAddress = config.getJSONObject("mqtt").getString("broker_address");
        String clientId = MqttClient.generateClientId();
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            return new MqttClient(brokerAddress, clientId, persistence);
        } catch (MqttException e) {
            logger.error("Error creating MQTT client", e);
            return null;
        }
    }

    private static void tryReconnect(MqttClient mqttClient, JSONObject config) {
        while (!mqttClient.isConnected()) {
            try {
                mqttClient.connect();
                mqttClient.subscribe(config.getJSONObject("mqtt").getString("topic_volume"));
                mqttClient.subscribe(config.getJSONObject("mqtt").getString("topic_message"));
            } catch (MqttException e) {
                logger.warn("Failed to reconnect to MQTT Broker. Retrying in 5 seconds...");
                try {
                    Thread.sleep(5000); // Retry after 5 seconds
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    private static void checkMqttConnection(MqttClient mqttClient) {
        if (!mqttClient.isConnected()) {
            try {
                mqttClient.reconnect();
            } catch (MqttException e) {
                logger.error("Failed to reconnect to MQTT Broker", e);
            }
        }
    }
    
    private static class AudioAdjuster {
        private final JSONObject alsaConfig;

        public AudioAdjuster(JSONObject alsaConfig) {
            this.alsaConfig = alsaConfig;
        }

        public void adjustVolume(int volume) {
            String alsaDevice = alsaConfig.getString("device");
            try {
                // Adjust volume using ALSA
                String[] command = {"amixer", "-D", alsaDevice, "sset", "Master", volume + "%"};
                Process process = new ProcessBuilder(command).start();
                process.waitFor();
                logger.info("Volume adjusted to " + volume + "%");
            } catch (IOException | InterruptedException e) {
                logger.error("Error adjusting volume", e);
            }
        }
    }

    private static class MessageSender {
        private final JSONObject udpConfig;

        public MessageSender(JSONObject udpConfig) {
            this.udpConfig = udpConfig;
        }

        public void sendMessage(String message) {
            String serverAddress = udpConfig.getString("server_address");
            int serverPort = udpConfig.getInt("server_port");
            try {
                // Send message via UDP
                byte[] sendData = message.getBytes("KOI8");
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(serverAddress), serverPort);
                socket.send(sendPacket);
                socket.close();
                logger.info("Message sent via UDP: " + message);
            } catch (IOException e) {
                logger.error("Error sending UDP message", e);
            }
        }
    }
}

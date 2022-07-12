package de.themoep.connectorplugin.connector;
/*
 * ConnectorPlugin
 * Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.ConnectorPlugin;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

public class MqttConnection {

    private final ConnectorPlugin plugin;
    private MqttClient client;

    public MqttConnection(ConnectorPlugin plugin, String brokerURI, String clientID, String username, String password, int keepAlive, BiConsumer<String, Message> onMessage) {
        this.plugin = plugin;

        MqttConnectionOptions conOpts = new MqttConnectionOptions();

        conOpts.setCleanStart(true);

        if (clientID == null || clientID.isEmpty()) {
            clientID = plugin.getName() + "-" + plugin.getServerName() + "-" + Thread.currentThread().getId();
        }

        if (username != null) {
            conOpts.setUserName(username);
        }

        if (password != null) {
            conOpts.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }

        conOpts.setKeepAliveInterval(keepAlive);
        conOpts.setAutomaticReconnect(true);

        try {
            client = new MqttClient(brokerURI, clientID);
            client.connect(conOpts);

            client.subscribe(new MqttSubscription[]{new MqttSubscription(plugin.getMessageChannel())}, new IMqttMessageListener[] {(topic, message) -> {
                if (!topic.equals(plugin.getMessageChannel())) {
                    return;
                }
                if (message.getPayload().length == 0) {
                    plugin.logWarning("Received a message with 0 bytes on " + topic + " MQTT topic? ");
                    return;
                }

                ByteArrayDataInput in = ByteStreams.newDataInput(message.getPayload());
                String group = in.readUTF();

                String target = in.readUTF();

                int messageLength = in.readInt();
                byte[] messageData = new byte[messageLength];
                in.readFully(messageData);

                try {
                    onMessage.accept(target, Message.fromByteArray(group, messageData));
                } catch (IllegalArgumentException e) {
                    plugin.logError("Error while decoding message on " + topic + " MQTT topic! ", e);
                } catch (VersionMismatchException e) {
                    plugin.logWarning(e.getMessage() + ". Ignoring message!");
                }
            }});
        } catch (MqttException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void sendMessage(String senderName, Message message) {
        byte[] messageData = message.writeToByteArray();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message.getGroup());
        out.writeUTF(senderName != null ? senderName : "");
        out.writeInt(messageData.length);
        out.write(messageData);
        byte[] dataToSend = out.toByteArray();

        plugin.runAsync(() -> {
            try {
                client.publish(plugin.getMessageChannel(), dataToSend, 1, false);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
    }

    public void close() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}

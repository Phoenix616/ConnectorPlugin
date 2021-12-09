package de.themoep.connectorplugin.connector;/*
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

public class Message {
    private static final int VERSION = 2;
    private final MessageTarget target;
    private final String sendingServer;
    private final String sendingPlugin;
    private final String action;
    private final byte[] data;

    public Message(MessageTarget target, String sendingServer, String sendingPlugin, String action, byte[] data) {
        this.target = target;
        this.sendingServer = sendingServer;
        this.sendingPlugin = sendingPlugin;
        this.action = action;
        this.data = data;
    }

    public MessageTarget getTarget() {
        return target;
    }

    public String getSendingServer() {
        return sendingServer;
    }

    public String getSendingPlugin() {
        return sendingPlugin;
    }

    public String getAction() {
        return action;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] writeToByteArray() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeInt(VERSION);
        out.writeUTF(target.name());
        out.writeUTF(sendingServer);
        out.writeUTF(sendingPlugin);
        out.writeUTF(action);
        out.writeInt(data.length);
        out.write(data);
        return out.toByteArray();
    }

    public static Message fromByteArray(byte[] messageData) throws VersionMismatchException {
        ByteArrayDataInput in = ByteStreams.newDataInput(messageData);
        int messageVersion = in.readInt();
        if (messageVersion < VERSION) {
            throw new VersionMismatchException(messageVersion, VERSION, "Received message from an outdated version (" + messageVersion + ", this only supports " + VERSION + ")! Please update the sending plugin!");
        } else if (messageVersion > VERSION) {
            throw new VersionMismatchException(messageVersion, VERSION, "Received message with a newer version (" + messageVersion + ", this only supports " + VERSION + ")! Please update this plugin!");
        }
        MessageTarget target = MessageTarget.valueOf(in.readUTF());
        String senderServer = in.readUTF();
        String senderPlugin = in.readUTF();
        String action = in.readUTF();
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(target, senderServer, senderPlugin, action, data);
    }

}

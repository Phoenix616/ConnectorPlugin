package de.themoep.connectorplugin;

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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.connectorplugin.connector.MessageTarget;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.themoep.connectorplugin.connector.Connector.PROXY_ID_PREFIX;

public abstract class ProxyBridgeCommon<P extends ConnectorPlugin<R>, R> extends BridgeCommon<P, R> {

    public ProxyBridgeCommon(P plugin) {
        super(plugin);
    }

    @Override
    protected void sendResponseData(String target, byte[] out) {
        sendData(
                Action.RESPONSE,
                target.startsWith(PROXY_ID_PREFIX) ? MessageTarget.PROXY : MessageTarget.SERVER,
                target,
                out);
    }

    /**
     * Run a console command on all other proxies
     * @param command   The command to run
     * @param consumer  Optional Consumer (or multiple) for the messages triggered by the command
     * @return A future for whether the command was run successfully
     */
    public CompletableFuture<Boolean> runProxyConsoleCommand(String command, Consumer<String>... consumer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        long id = RANDOM.nextLong();
        out.writeUTF(plugin.getServerName());
        out.writeLong(id);
        out.writeUTF(command);
        responses.put(id, new ResponseHandler.Boolean(future));
        if (consumer != null && consumer.length > 0) {
            consumers.put(id, consumer);
        }
        sendData(Action.CONSOLE_COMMAND, MessageTarget.OTHER_PROXIES, out.toByteArray());
        return future;
    }
}

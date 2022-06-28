package de.themoep.connectorplugin;
/*
 * ConnectorPlugin
 * Copyright (C) 2022 Max Lee aka Phoenix616 (max@themoep.de)
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BridgedSuggestions<S> {

    public default List<String> suggest(S sender, String label, String[] args) {
        return Collections.emptyList();
    }

    public default CompletableFuture<List<String>> suggestAsync(S sender, String label, String[] args) {
        return CompletableFuture.completedFuture(suggest(sender, label, args));
    }
}

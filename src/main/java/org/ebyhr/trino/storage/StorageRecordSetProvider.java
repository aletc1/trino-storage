/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ebyhr.trino.storage;

import com.google.common.collect.Iterables;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.InMemoryRecordSet;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.type.Type;
import org.ebyhr.trino.storage.operator.FilePlugin;
import org.ebyhr.trino.storage.operator.PluginFactory;

import javax.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class StorageRecordSetProvider
        implements ConnectorRecordSetProvider
{
    private final StorageClient storageClient;
    private final String connectorId;

    @Inject
    public StorageRecordSetProvider(StorageClient storageClient, StorageConnectorId connectorId)
    {
        this.storageClient = requireNonNull(storageClient, "storageClient is null");
        this.connectorId = requireNonNull(connectorId, "connectorId is null").toString();
    }

    @Override
    public RecordSet getRecordSet(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorSplit split,
            ConnectorTableHandle table,
            List<? extends ColumnHandle> columns)
    {
        requireNonNull(split, "split is null");
        StorageSplit storageSplit = (StorageSplit) split;
        checkArgument(storageSplit.getConnectorId().equals(connectorId), "split is not for this connector");

        String schemaName = storageSplit.getSchemaName();
        String tableName = storageSplit.getTableName();

        FilePlugin plugin = PluginFactory.create(schemaName);
        InputStream inputStream;
        try {
            inputStream = storageClient.getInputStream(session, tableName);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        StorageTable storageTable = new StorageTable(tableName, plugin.getFields(inputStream));

        Stream<List<?>> stream = plugin.getIterator(inputStream);
        Iterable<List<?>> rows = stream::iterator;

        List<StorageColumnHandle> handles = columns
                .stream()
                .map(c -> (StorageColumnHandle) c)
                .collect(toList());
        List<Integer> columnIndexes = handles
                .stream()
                .map(column -> {
                    int index = 0;
                    for (ColumnMetadata columnMetadata : storageTable.getColumnsMetadata()) {
                        if (columnMetadata.getName().equalsIgnoreCase(column.getColumnName())) {
                            return index;
                        }
                        index++;
                    }
                    throw new IllegalStateException("Unknown column: " + column.getColumnName());
                })
                .collect(toList());

        //noinspection StaticPseudoFunctionalStyleMethod
        Iterable<List<?>> mappedRows = Iterables.transform(rows, row -> columnIndexes
                .stream()
                .map(row::get)
                .collect(toList()));

        List<Type> mappedTypes = handles
                .stream()
                .map(StorageColumnHandle::getColumnType)
                .collect(toList());
        return new InMemoryRecordSet(mappedTypes, mappedRows);
    }
}

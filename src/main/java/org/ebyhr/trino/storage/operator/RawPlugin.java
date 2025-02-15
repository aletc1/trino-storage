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
package org.ebyhr.trino.storage.operator;

import org.ebyhr.trino.storage.StorageColumn;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class RawPlugin
        implements FilePlugin
{
    @Override
    public List<StorageColumn> getFields(String path, Function<String, InputStream> streamProvider)
    {
        return List.of(new StorageColumn("data", VARCHAR));
    }

    @Override
    public Stream<List<?>> getRecordsIterator(String path, Function<String, InputStream> streamProvider)
    {
        InputStream inputStream = streamProvider.apply(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String blob = reader.lines().map(Objects::toString).collect(Collectors.joining("\n"));
        return Stream.of(List.of(blob));
    }
}

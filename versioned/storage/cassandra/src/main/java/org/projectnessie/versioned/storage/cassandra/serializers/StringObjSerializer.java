/*
 * Copyright (C) 2023 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.versioned.storage.cassandra.serializers;

import static org.projectnessie.versioned.storage.cassandra.CassandraConstants.INSERT_OBJ_PREFIX;
import static org.projectnessie.versioned.storage.cassandra.CassandraConstants.INSERT_OBJ_VALUES;
import static org.projectnessie.versioned.storage.cassandra.CassandraConstants.STORE_OBJ_SUFFIX;
import static org.projectnessie.versioned.storage.common.objtypes.StringObj.stringData;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.projectnessie.versioned.storage.cassandra.CassandraSerde;
import org.projectnessie.versioned.storage.cassandra.CqlColumn;
import org.projectnessie.versioned.storage.cassandra.CqlColumnType;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.objtypes.Compression;
import org.projectnessie.versioned.storage.common.objtypes.StringObj;
import org.projectnessie.versioned.storage.common.persist.ObjId;

public class StringObjSerializer implements ObjSerializer<StringObj> {

  public static final ObjSerializer<StringObj> INSTANCE = new StringObjSerializer();

  private static final CqlColumn COL_STRING_CONTENT_TYPE =
      new CqlColumn("s_content_type", CqlColumnType.NAME);
  private static final CqlColumn COL_STRING_COMPRESSION =
      new CqlColumn("s_compression", CqlColumnType.NAME);
  private static final CqlColumn COL_STRING_FILENAME =
      new CqlColumn("s_filename", CqlColumnType.NAME);
  private static final CqlColumn COL_STRING_PREDECESSORS =
      new CqlColumn("s_predecessors", CqlColumnType.OBJ_ID_LIST);
  private static final CqlColumn COL_STRING_TEXT = new CqlColumn("s_text", CqlColumnType.VARBINARY);

  private static final Set<CqlColumn> COLS =
      ImmutableSet.of(
          COL_STRING_CONTENT_TYPE,
          COL_STRING_COMPRESSION,
          COL_STRING_FILENAME,
          COL_STRING_PREDECESSORS,
          COL_STRING_TEXT);

  private static final String INSERT_CQL =
      INSERT_OBJ_PREFIX
          + COLS.stream().map(CqlColumn::name).collect(Collectors.joining(","))
          + INSERT_OBJ_VALUES
          + COLS.stream().map(c -> ":" + c.name()).collect(Collectors.joining(","))
          + ")";

  private static final String STORE_CQL = INSERT_CQL + STORE_OBJ_SUFFIX;

  private StringObjSerializer() {}

  @Override
  public Set<CqlColumn> columns() {
    return COLS;
  }

  @Override
  public String insertCql(boolean upsert) {
    return upsert ? INSERT_CQL : STORE_CQL;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override
  public void serialize(
      StringObj obj,
      BoundStatementBuilder stmt,
      int incrementalIndexLimit,
      int maxSerializedIndexSize)
      throws ObjTooLargeException {
    stmt.setString(COL_STRING_CONTENT_TYPE.name(), obj.contentType());
    stmt.setString(COL_STRING_COMPRESSION.name(), obj.compression().name());
    stmt.setString(COL_STRING_FILENAME.name(), obj.filename());
    stmt.setList(
        COL_STRING_PREDECESSORS.name(),
        CassandraSerde.serializeObjIds(obj.predecessors()),
        String.class);
    stmt.setByteBuffer(COL_STRING_TEXT.name(), obj.text().asReadOnlyByteBuffer());
  }

  @Override
  public StringObj deserialize(Row row, ObjId id) {
    return stringData(
        id,
        row.getString(COL_STRING_CONTENT_TYPE.name()),
        Compression.valueOf(row.getString(COL_STRING_COMPRESSION.name())),
        row.getString(COL_STRING_FILENAME.name()),
        CassandraSerde.deserializeObjIds(row, COL_STRING_PREDECESSORS.name()),
        CassandraSerde.deserializeBytes(row, COL_STRING_TEXT.name()));
  }
}

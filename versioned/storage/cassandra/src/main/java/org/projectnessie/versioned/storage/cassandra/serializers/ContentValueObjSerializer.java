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
import static org.projectnessie.versioned.storage.common.objtypes.ContentValueObj.contentValue;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.storage.cassandra.CassandraSerde;
import org.projectnessie.versioned.storage.cassandra.CqlColumn;
import org.projectnessie.versioned.storage.cassandra.CqlColumnType;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.objtypes.ContentValueObj;
import org.projectnessie.versioned.storage.common.persist.ObjId;

public class ContentValueObjSerializer implements ObjSerializer<ContentValueObj> {

  public static final ObjSerializer<ContentValueObj> INSTANCE = new ContentValueObjSerializer();

  private static final CqlColumn COL_VALUE_CONTENT_ID =
      new CqlColumn("v_content_id", CqlColumnType.NAME);
  private static final CqlColumn COL_VALUE_PAYLOAD = new CqlColumn("v_payload", CqlColumnType.INT);
  private static final CqlColumn COL_VALUE_DATA = new CqlColumn("v_data", CqlColumnType.VARBINARY);

  private static final Set<CqlColumn> COLS =
      ImmutableSet.of(COL_VALUE_CONTENT_ID, COL_VALUE_PAYLOAD, COL_VALUE_DATA);

  private static final String INSERT_CQL =
      INSERT_OBJ_PREFIX
          + COLS.stream().map(CqlColumn::name).collect(Collectors.joining(","))
          + INSERT_OBJ_VALUES
          + COLS.stream().map(c -> ":" + c.name()).collect(Collectors.joining(","))
          + ")";

  private static final String STORE_CQL = INSERT_CQL + STORE_OBJ_SUFFIX;

  private ContentValueObjSerializer() {}

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
      ContentValueObj obj,
      BoundStatementBuilder stmt,
      int incrementalIndexLimit,
      int maxSerializedIndexSize)
      throws ObjTooLargeException {
    stmt.setString(COL_VALUE_CONTENT_ID.name(), obj.contentId());
    stmt.setInt(COL_VALUE_PAYLOAD.name(), obj.payload());
    stmt.setByteBuffer(COL_VALUE_DATA.name(), obj.data().asReadOnlyByteBuffer());
  }

  @Override
  public ContentValueObj deserialize(Row row, ObjId id) {
    ByteString value = CassandraSerde.deserializeBytes(row, COL_VALUE_DATA.name());
    if (value != null) {
      return contentValue(
          id,
          row.getString(COL_VALUE_CONTENT_ID.name()),
          row.getInt(COL_VALUE_PAYLOAD.name()),
          value);
    }
    throw new IllegalStateException("Data value of obj " + id + " of type VALUE is null");
  }
}

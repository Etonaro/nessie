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
package org.projectnessie.versioned.storage.jdbc.serializers;

import static org.projectnessie.versioned.storage.jdbc.JdbcSerde.serializeBytes;

import com.google.common.collect.ImmutableMap;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.jdbc.DatabaseSpecific;
import org.projectnessie.versioned.storage.jdbc.JdbcColumnType;
import org.projectnessie.versioned.storage.serialize.SmileSerialization;

public class CustomObjSerializer implements ObjSerializer<Obj> {

  public static final ObjSerializer<?> INSTANCE = new CustomObjSerializer();

  private static final String COL_CUSTOM_CLASS = "x_class";
  private static final String COL_CUSTOM_DATA = "x_data";

  private static final Map<String, JdbcColumnType> COLS =
      ImmutableMap.of(
          COL_CUSTOM_CLASS, JdbcColumnType.NAME,
          COL_CUSTOM_DATA, JdbcColumnType.VARBINARY);

  private CustomObjSerializer() {}

  @Override
  public Map<String, JdbcColumnType> columns() {
    return COLS;
  }

  @Override
  public void serialize(
      PreparedStatement ps,
      Obj obj,
      int incrementalIndexLimit,
      int maxSerializedIndexSize,
      Function<String, Integer> nameToIdx,
      DatabaseSpecific databaseSpecific)
      throws SQLException, ObjTooLargeException {
    ps.setString(nameToIdx.apply(COL_CUSTOM_CLASS), obj.type().targetClass().getName());
    serializeBytes(
        ps,
        nameToIdx.apply(COL_CUSTOM_DATA),
        ByteString.copyFrom(SmileSerialization.serializeObj(obj)),
        databaseSpecific);
  }

  @Override
  public Obj deserialize(ResultSet rs, ObjId id) throws SQLException {
    return SmileSerialization.deserializeObj(
        id, rs.getBytes(COL_CUSTOM_DATA), rs.getString(COL_CUSTOM_CLASS));
  }
}

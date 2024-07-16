/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.session.it;

import org.apache.iotdb.isession.ISession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.it.env.EnvFactory;
import org.apache.iotdb.it.framework.IoTDBTestRunner;
import org.apache.iotdb.itbase.category.ClusterIT;
import org.apache.iotdb.itbase.category.LocalStandaloneIT;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.Session;

import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.ChunkMetadata;
import org.apache.tsfile.read.common.RowRecord;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.record.Tablet.ColumnType;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.iotdb.itbase.env.BaseEnv.TABLE_SQL_DIALECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(IoTDBTestRunner.class)
public class IoTDBSessionRelationalIT {

  @Before
  public void setUp() throws Exception {
    EnvFactory.getEnv().initClusterEnvironment();
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("CREATE DATABASE db1");
    }
  }

  @After
  public void tearDown() throws Exception {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("DROP DATABASE db1");
    }
    EnvFactory.getEnv().cleanClusterEnvironment();
  }

  // for manual debugging
  public static void main(String[] args)
      throws IoTDBConnectionException, StatementExecutionException {
    insertRelationalTabletPerformanceTest();
  }

  private static void insertRelationalTabletPerformanceTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session =
        new Session.Builder().host("127.0.0.1").port(6667).sqlDialect(TABLE_SQL_DIALECT).build()) {
      session.open();
      session.executeNonQueryStatement("USE \"db1\"");
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);

      long timestamp = 0;
      Tablet tablet = new Tablet("table1", schemaList, columnTypes, 15);

      for (long row = 0; row < 15; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void insertRelationalSqlTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      long timestamp;

      for (long row = 0; row < 15; row++) {
        session.executeNonQueryStatement(
            String.format(
                "INSERT INTO table1 (id1, attr1, m1) VALUES ('%s', '%s', %f)",
                "id:" + row, "attr:" + row, row * 1.0));
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        session.executeNonQueryStatement(
            String.format(
                "INSERT INTO table1 (id1, attr1, m1) VALUES ('%s', '%s', %f)",
                "id:" + row, "attr:" + row, row * 1.0));
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
      }

      // sql cannot create column
      assertThrows(
          StatementExecutionException.class,
          () ->
              session.executeNonQueryStatement(
                  String.format(
                      "INSERT INTO table1 (id1, id2, attr1, m1) VALUES ('%s', '%s', '%s', %f)",
                      "id:" + 100, "id:" + 100, "attr:" + 100, 100 * 1.0)));
    }
    Map<String, ChunkMetadata> chunkMetadataMap = new HashMap<>();
    List<ChunkMetadata> valueChunkMetadataList = new ArrayList<>();
    chunkMetadataMap.computeIfPresent(
        "",
        (k, v) -> {
          valueChunkMetadataList.add(v);
          return v;
        });
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void partialInsertRelationalRowTest()
      throws IoTDBConnectionException, StatementExecutionException {
    // disable auto-creation only for this test
    EnvFactory.getEnv().cleanClusterEnvironment();
    EnvFactory.getEnv().getConfig().getCommonConfig().setAutoCreateSchemaEnabled(false);
    EnvFactory.getEnv().initClusterEnvironment();
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("CREATE DATABASE \"db1\"");
      session.executeNonQueryStatement("USE \"db1\"");
      // the table is missing column "m2"
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      // the insertion contains "m2"
      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      schemaList.add(new MeasurementSchema("m2", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(
              ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT, ColumnType.MEASUREMENT);
      List<String> measurementIds =
          schemaList.stream()
              .map(IMeasurementSchema::getMeasurementId)
              .collect(Collectors.toList());
      List<TSDataType> dataTypes =
          schemaList.stream().map(IMeasurementSchema::getType).collect(Collectors.toList());

      long timestamp = 0;

      for (long row = 0; row < 15; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0, row * 1.0};
        try {
          session.insertRelationalRecord(
              "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
        } catch (StatementExecutionException e) {
          if (!e.getMessage()
              .equals(
                  "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
            throw e;
          }
        }
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0, row * 1.0};
        try {
          session.insertRelationalRecord(
              "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
        } catch (StatementExecutionException e) {
          if (!e.getMessage()
              .equals(
                  "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
            throw e;
          }
        }
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
        // "m2" should not be present
        assertEquals(4, rowRecord.getFields().size());
        timestamp++;
        //        System.out.println(rowRecord);
      }
    } finally {
      EnvFactory.getEnv().getConfig().getCommonConfig().setAutoCreateSchemaEnabled(true);
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void insertRelationalRowTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);
      List<String> measurementIds =
          schemaList.stream()
              .map(IMeasurementSchema::getMeasurementId)
              .collect(Collectors.toList());
      List<TSDataType> dataTypes =
          schemaList.stream().map(IMeasurementSchema::getType).collect(Collectors.toList());

      long timestamp = 0;

      for (long row = 0; row < 15; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void partialInsertRelationalTabletTest()
      throws IoTDBConnectionException, StatementExecutionException {
    // disable auto-creation only for this test
    EnvFactory.getEnv().cleanClusterEnvironment();
    EnvFactory.getEnv().getConfig().getCommonConfig().setAutoCreateSchemaEnabled(false);
    EnvFactory.getEnv().initClusterEnvironment();
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("CREATE DATABASE \"db1\"");
      session.executeNonQueryStatement("USE \"db1\"");
      // the table is missing column "m2"
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      // the insertion contains "m2"
      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      schemaList.add(new MeasurementSchema("m2", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(
              ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT, ColumnType.MEASUREMENT);

      long timestamp = 0;
      Tablet tablet = new Tablet("table1", schemaList, columnTypes, 15);

      for (long row = 0; row < 15; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        tablet.addValue("m2", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          try {
            session.insertRelationalTablet(tablet, true);
          } catch (StatementExecutionException e) {
            // a partial insertion should be reported
            if (!e.getMessage()
                .equals(
                    "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
              throw e;
            }
          }
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        try {
          session.insertRelationalTablet(tablet, true);
        } catch (StatementExecutionException e) {
          if (!e.getMessage()
              .equals(
                  "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
            throw e;
          }
        }
        tablet.reset();
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        tablet.addValue("m2", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          try {
            session.insertRelationalTablet(tablet, true);
          } catch (StatementExecutionException e) {
            if (!e.getMessage()
                .equals(
                    "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
              throw e;
            }
          }
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        try {
          session.insertRelationalTablet(tablet, true);
        } catch (StatementExecutionException e) {
          if (!e.getMessage()
              .equals(
                  "507: Fail to insert measurements [m2] caused by [Column m2 does not exists or fails to be created]")) {
            throw e;
          }
        }
        tablet.reset();
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
        // "m2" should not be present
        assertEquals(4, rowRecord.getFields().size());
        timestamp++;
        //        System.out.println(rowRecord);
      }
    } finally {
      EnvFactory.getEnv().getConfig().getCommonConfig().setAutoCreateSchemaEnabled(true);
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void insertRelationalTabletTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);

      long timestamp = 0;
      Tablet tablet = new Tablet("table1", schemaList, columnTypes, 15);

      for (long row = 0; row < 15; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void autoCreateTableTest() throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      // no table created here

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);
      List<String> measurementIds =
          schemaList.stream()
              .map(IMeasurementSchema::getMeasurementId)
              .collect(Collectors.toList());
      List<TSDataType> dataTypes =
          schemaList.stream().map(IMeasurementSchema::getType).collect(Collectors.toList());

      long timestamp = 0;

      for (long row = 0; row < 15; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        Object[] values = new Object[] {"id:" + row, "attr:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void autoCreateNonIdColumnTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      // only one column in this table, and others should be auto-created
      session.executeNonQueryStatement("CREATE TABLE table1 (id1 string id)");

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);

      long timestamp = 0;
      Tablet tablet = new Tablet("table1", schemaList, columnTypes, 15);

      for (long row = 0; row < 15; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id1", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(3).getDoubleV(), 0.0001);
        timestamp++;
        //        System.out.println(rowRecord);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void autoCreateIdColumnTest()
      throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("USE \"db1\"");
      // only one column in this table, and others should be auto-created
      session.executeNonQueryStatement("CREATE TABLE table1 (id1 string id)");

      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id2", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);

      long timestamp = 0;
      Tablet tablet = new Tablet("table1", schemaList, columnTypes, 15);

      for (long row = 0; row < 15; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id2", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        int rowIndex = tablet.rowSize++;
        tablet.addTimestamp(rowIndex, timestamp + row);
        tablet.addValue("id2", rowIndex, "id:" + row);
        tablet.addValue("attr1", rowIndex, "attr:" + row);
        tablet.addValue("m1", rowIndex, row * 1.0);
        if (tablet.rowSize == tablet.getMaxRowNumber()) {
          session.insertRelationalTablet(tablet, true);
          tablet.reset();
        }
      }

      if (tablet.rowSize != 0) {
        session.insertRelationalTablet(tablet);
        tablet.reset();
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        // id 1 should be null
        assertNull(rowRecord.getFields().get(1).getDataType());
        assertEquals("id:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals("attr:" + timestamp, rowRecord.getFields().get(3).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(4).getDoubleV(), 0.0001);
        timestamp++;
        //        System.out.println(rowRecord);
      }
    }
  }

  @Test
  @Category({LocalStandaloneIT.class, ClusterIT.class})
  public void autoAdjustIdTest() throws IoTDBConnectionException, StatementExecutionException {
    try (ISession session = EnvFactory.getEnv().getSessionConnection(TABLE_SQL_DIALECT)) {
      session.executeNonQueryStatement("DROP DATABASE IF EXISTS db1");
      session.executeNonQueryStatement("CREATE DATABASE db1");
      session.executeNonQueryStatement("USE \"db1\"");
      // the id order in the table is (id1, id2)
      session.executeNonQueryStatement(
          "CREATE TABLE table1 (id1 string id, id2 string id, attr1 string attribute, "
              + "m1 double "
              + "measurement)");

      // the id order in the row is (id2, id1)
      List<IMeasurementSchema> schemaList = new ArrayList<>();
      schemaList.add(new MeasurementSchema("id2", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("id1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("attr1", TSDataType.STRING));
      schemaList.add(new MeasurementSchema("m1", TSDataType.DOUBLE));
      final List<ColumnType> columnTypes =
          Arrays.asList(ColumnType.ID, ColumnType.ID, ColumnType.ATTRIBUTE, ColumnType.MEASUREMENT);
      List<String> measurementIds =
          schemaList.stream()
              .map(IMeasurementSchema::getMeasurementId)
              .collect(Collectors.toList());
      List<TSDataType> dataTypes =
          schemaList.stream().map(IMeasurementSchema::getType).collect(Collectors.toList());

      long timestamp = 0;

      for (long row = 0; row < 15; row++) {
        Object[] values = new Object[] {"id2:" + row, "id1:" + row, "attr1:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      session.executeNonQueryStatement("FLush");

      for (long row = 15; row < 30; row++) {
        Object[] values = new Object[] {"id2:" + row, "id1:" + row, "attr1:" + row, row * 1.0};
        session.insertRelationalRecord(
            "table1", timestamp + row, measurementIds, dataTypes, columnTypes, values);
      }

      SessionDataSet dataSet = session.executeQueryStatement("select * from table1 order by time");
      while (dataSet.hasNext()) {
        RowRecord rowRecord = dataSet.next();
        timestamp = rowRecord.getFields().get(0).getLongV();
        assertEquals("id1:" + timestamp, rowRecord.getFields().get(1).getBinaryV().toString());
        assertEquals("id2:" + timestamp, rowRecord.getFields().get(2).getBinaryV().toString());
        assertEquals("attr1:" + timestamp, rowRecord.getFields().get(3).getBinaryV().toString());
        assertEquals(timestamp * 1.0, rowRecord.getFields().get(4).getDoubleV(), 0.0001);
      }
    }
  }
}
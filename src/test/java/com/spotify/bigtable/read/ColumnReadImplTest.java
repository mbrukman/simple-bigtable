/*-
 * -\-\-
 * simple-bigtable
 * --
 * Copyright (C) 2016 - 2017 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

/*
 *
 *  * Copyright 2016 Spotify AB.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package com.spotify.bigtable.read;

import static com.spotify.bigtable.read.AbstractBigtableRead.toExactMatchRegex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.bigtable.v2.Column;
import com.google.bigtable.v2.Family;
import com.google.bigtable.v2.ReadRowsRequest;
import com.google.bigtable.v2.RowFilter;
import com.google.common.util.concurrent.Futures;
import com.spotify.bigtable.BigtableMock;
import com.spotify.bigtable.read.ReadCell.CellWithinCellsRead;
import com.spotify.bigtable.read.ReadCells.CellsWithinColumnRead;
import com.spotify.bigtable.read.ReadColumn.ColumnWithinFamilyRead;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ColumnReadImplTest {

  BigtableMock bigtableMock = BigtableMock.getMock();
  ColumnWithinFamilyRead.ReadImpl columnRead;

  @Before
  public void setUp() throws Exception {
    final TableRead.TableReadImpl tableRead = new TableRead.TableReadImpl(bigtableMock, "table");
    columnRead = (ColumnWithinFamilyRead.ReadImpl) tableRead.row("row").family("family").columnQualifier("qualifier");
  }

  private void verifyReadRequest(ReadRowsRequest.Builder readRequest) throws Exception {
    assertEquals(bigtableMock.getFullTableName("table"), readRequest.getTableName());
    assertEquals("row", readRequest.getRows().getRowKeys(0).toStringUtf8());
    assertEquals(1, readRequest.getRows().getRowKeysCount());
    assertEquals(0, readRequest.getRows().getRowRangesCount());
    assertEquals(1, readRequest.getRowsLimit());
    assertTrue(readRequest.getFilter().getChain().getFiltersCount() >= 3);
    assertEquals(RowFilter.getDefaultInstance(), readRequest.getFilter().getChain().getFilters(0));
    assertEquals(toExactMatchRegex("family"), readRequest.getFilter().getChain().getFilters(1).getFamilyNameRegexFilter());
    assertEquals(toExactMatchRegex("qualifier"), readRequest.getFilter().getChain().getFilters(2).getColumnQualifierRegexFilter().toStringUtf8());
  }

  @Test
  public void testGetClient() throws Exception {
    assertEquals(bigtableMock.getMockedDataClient(), columnRead.getClient());
  }

  @Test
  public void testParentDataTypeToDataType() throws Exception {
    assertEquals(Optional.empty(), columnRead.parentTypeToCurrentType().apply(Optional.empty()));
    assertEquals(Optional.empty(), columnRead.parentTypeToCurrentType().apply(Optional.of(Family.getDefaultInstance())));

    final Column column = Column.getDefaultInstance();
    final Family family = Family.newBuilder().addColumns(column).build();
    assertEquals(Optional.of(column), columnRead.parentTypeToCurrentType().apply(Optional.of(family)));
  }

  @Test
  public void testExecuteAsync() throws Exception {
    verifyReadRequest(columnRead.readRequest());
    when(bigtableMock.getMockedDataClient().readRowsAsync(any()))
            .thenReturn(Futures.immediateFuture(Collections.emptyList()));

    columnRead.executeAsync();

    verifyReadRequest(columnRead.readRequest()); // make sure execute did not change the read request
    verify(bigtableMock.getMockedDataClient()).readRowsAsync(columnRead.readRequest().build());
    verifyNoMoreInteractions(bigtableMock.getMockedDataClient());
  }

  @Test
  public void testLatestCell() throws Exception {
    final CellWithinCellsRead.ReadImpl read = (CellWithinCellsRead.ReadImpl) columnRead.latestCell();

    final ReadRowsRequest.Builder readRequest = read.readRequest();
    verifyReadRequest(readRequest);

    final RowFilter.Chain chain = readRequest.getFilter().getChain();
    assertEquals(4, chain.getFiltersCount());
    assertEquals(1, chain.getFilters(3).getCellsPerColumnLimitFilter());
    assertEquals(RowFilter.getDefaultInstance(), readRequest.getFilter().toBuilder().clearChain().build());
  }

  @Test
  public void testCells() throws Exception {
    final CellsWithinColumnRead.ReadImpl read = (CellsWithinColumnRead.ReadImpl) columnRead.cells();

    final ReadRowsRequest.Builder readRequest = read.readRequest();
    verifyReadRequest(readRequest);
  }
}

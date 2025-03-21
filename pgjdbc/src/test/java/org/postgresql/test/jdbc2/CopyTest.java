/*
 * Copyright (c) 2008, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.jdbc2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.copy.CopyOut;
import org.postgresql.copy.PGCopyOutputStream;
import org.postgresql.core.ServerVersion;
import org.postgresql.test.TestUtil;
import org.postgresql.util.ByteBufferByteStreamWriter;
import org.postgresql.util.PSQLState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * @author kato@iki.fi
 */
class CopyTest {
  private Connection con;
  private CopyManager copyAPI;
  private String copyParams;
  // 0's required to match DB output for numeric(5,2)
  private final String[] origData =
      {"First Row\t1\t1.10\n",
          "Second Row\t2\t-22.20\n",
          "\\N\t\\N\t\\N\n",
          "\t4\t444.40\n"};
  private final int dataRows = origData.length;

  private byte[] getData(String[] origData) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(buf);
    for (String anOrigData : origData) {
      ps.print(anOrigData);
    }
    return buf.toByteArray();
  }

  @BeforeEach
  void setUp() throws Exception {
    con = TestUtil.openDB();

    TestUtil.createTempTable(con, "copytest", "stringvalue text, intvalue int, numvalue numeric(5,2)");

    copyAPI = ((PGConnection) con).getCopyAPI();
    if (TestUtil.haveMinimumServerVersion(con, ServerVersion.v9_0)) {
      copyParams = "(FORMAT CSV, HEADER false)";
    } else {
      copyParams = "CSV";
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    TestUtil.closeDB(con);

    // one of the tests will render the existing connection broken,
    // so we need to drop the table on a fresh one.
    con = TestUtil.openDB();
    try {
      TestUtil.dropTable(con, "copytest");
    } finally {
      con.close();
    }
  }

  private int getCount() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM copytest");
    rs.next();
    int result = rs.getInt(1);
    rs.close();
    return result;
  }

  @Test
  void copyInByRow() throws SQLException {
    String sql = "COPY copytest FROM STDIN";
    CopyIn cp = copyAPI.copyIn(sql);
    for (String anOrigData : origData) {
      byte[] buf = anOrigData.getBytes();
      cp.writeToCopy(buf, 0, buf.length);
    }

    long count1 = cp.endCopy();
    long count2 = cp.getHandledRowCount();
    assertEquals(dataRows, count1);
    assertEquals(dataRows, count2);

    try {
      cp.cancelCopy();
    } catch (SQLException se) { // should fail with obsolete operation
      if (!PSQLState.OBJECT_NOT_IN_STATE.getState().equals(se.getSQLState())) {
        fail("should have thrown object not in state exception.");
      }
    }
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  @Test
  void copyInAsOutputStream() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    OutputStream os = new PGCopyOutputStream((PGConnection) con, sql, 1000);
    for (String anOrigData : origData) {
      byte[] buf = anOrigData.getBytes();
      os.write(buf);
    }
    os.close();
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  @Test
  void copyInAsOutputStreamClosesAfterEndCopy() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    PGCopyOutputStream os = new PGCopyOutputStream((PGConnection) con, sql, 1000);
    try {
      for (String anOrigData : origData) {
        byte[] buf = anOrigData.getBytes();
        os.write(buf);
      }
      os.endCopy();
    } finally {
      os.close();
    }
    assertFalse(os.isActive());
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  @Test
  void copyInAsOutputStreamFailsOnFlushAfterEndCopy() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    PGCopyOutputStream os = new PGCopyOutputStream((PGConnection) con, sql, 1000);
    try {
      for (String anOrigData : origData) {
        byte[] buf = anOrigData.getBytes();
        os.write(buf);
      }
      os.endCopy();
    } finally {
      os.close();
    }
    try {
      os.flush();
      fail("should have failed flushing an inactive copy stream.");
    } catch (IOException e) {
      // We expect "This copy stream is closed", however, the message is locale-dependent
      if (Locale.getDefault().getLanguage().equals(new Locale("en").getLanguage())
          && !e.toString().contains("This copy stream is closed.")) {
        fail("has failed not due to checkClosed(): " + e);
      }
    }
  }

  @Test
  void copyInFromInputStream() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    copyAPI.copyIn(sql, new ByteArrayInputStream(getData(origData)), 3);
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  @Test
  void copyInFromStreamFail() throws SQLException {
    String sql = "COPY copytest FROM STDIN";
    try {
      copyAPI.copyIn(sql, new InputStream() {
        @Override
        public int read() {
          throw new RuntimeException("COPYTEST");
        }
      }, 3);
    } catch (Exception e) {
      if (!e.toString().contains("COPYTEST")) {
        fail("should have failed trying to read from our bogus stream.");
      }
    }
    int rowCount = getCount();
    assertEquals(0, rowCount);
  }

  @Test
  void copyInFromReader() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    copyAPI.copyIn(sql, new StringReader(new String(getData(origData))), 3);
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  @Test
  void copyInFromByteStreamWriter() throws SQLException, IOException {
    String sql = "COPY copytest FROM STDIN";
    copyAPI.copyIn(sql, new ByteBufferByteStreamWriter(ByteBuffer.wrap(getData(origData))));
    int rowCount = getCount();
    assertEquals(dataRows, rowCount);
  }

  /**
   * Tests writing to a COPY ... FROM STDIN using both the standard OutputStream API
   * write(byte[]) and the driver specific write(ByteStreamWriter) API interleaved.
   */
  @Test
  void copyMultiApi() throws SQLException, IOException {
    TestUtil.execute(con, "CREATE TABLE pg_temp.copy_api_test (data text)");
    String sql = "COPY pg_temp.copy_api_test (data) FROM STDIN";
    PGCopyOutputStream out = new PGCopyOutputStream(copyAPI.copyIn(sql));
    try {
      out.write("a".getBytes());
      out.writeToCopy(new ByteBufferByteStreamWriter(ByteBuffer.wrap("b".getBytes())));
      out.write("c".getBytes());
      out.writeToCopy(new ByteBufferByteStreamWriter(ByteBuffer.wrap("d".getBytes())));
      out.write("\n".getBytes());
    } finally {
      out.close();
    }
    String data = TestUtil.queryForString(con, "SELECT data FROM pg_temp.copy_api_test");
    assertEquals("abcd", data, "The writes to the COPY should be in order");
  }

  @Test
  void skipping() {
    String sql = "COPY copytest FROM STDIN";
    String at = "init";
    int rowCount = -1;
    int skip = 0;
    int skipChar = 1;
    try {
      while (skipChar > 0) {
        at = "buffering";
        InputStream ins = new ByteArrayInputStream(getData(origData));
        at = "skipping";
        ins.skip(skip++);
        skipChar = ins.read();
        at = "copying";
        copyAPI.copyIn(sql, ins, 3);
        at = "using connection after writing copy";
        rowCount = getCount();
      }
    } catch (Exception e) {
      if (skipChar != '\t') {
        // error expected when field separator consumed
        fail("testSkipping at " + at + " round " + skip + ": " + e.toString());
      }
    }
    assertEquals(dataRows * (skip - 1), rowCount);
  }

  @Test
  void copyOutByRow() throws SQLException, IOException {
    copyInByRow(); // ensure we have some data.
    String sql = "COPY copytest TO STDOUT";
    CopyOut cp = copyAPI.copyOut(sql);
    int count = 0;
    byte[] buf;
    while ((buf = cp.readFromCopy()) != null) {
      count++;
    }
    assertFalse(cp.isActive());
    assertEquals(dataRows, count);

    long rowCount = cp.getHandledRowCount();

    assertEquals(dataRows, rowCount);

    assertEquals(dataRows, getCount());
  }

  @Test
  void copyOut() throws SQLException, IOException {
    copyInByRow(); // ensure we have some data.
    String sql = "COPY copytest TO STDOUT";
    ByteArrayOutputStream copydata = new ByteArrayOutputStream();
    copyAPI.copyOut(sql, copydata);
    assertEquals(dataRows, getCount());
    // deep comparison of data written and read
    byte[] copybytes = copydata.toByteArray();
    assertNotNull(copybytes);
    for (int i = 0, l = 0; i < origData.length; i++) {
      byte[] origBytes = origData[i].getBytes();
      assertTrue(copybytes.length >= l + origBytes.length, "Copy is shorter than original");
      for (int j = 0; j < origBytes.length; j++, l++) {
        assertEquals(origBytes[j], copybytes[l], "content changed at byte#" + j + ": " + origBytes[j] + copybytes[l]);
      }
    }
  }

  @Test
  void nonCopyOut() throws SQLException, IOException {
    String sql = "SELECT 1";
    try {
      copyAPI.copyOut(sql, new ByteArrayOutputStream());
      fail("Can't use a non-copy query.");
    } catch (SQLException sqle) {
    }
    // Ensure connection still works.
    assertEquals(0, getCount());
  }

  @Test
  void nonCopyIn() throws SQLException, IOException {
    String sql = "SELECT 1";
    try {
      copyAPI.copyIn(sql, new ByteArrayInputStream(new byte[0]));
      fail("Can't use a non-copy query.");
    } catch (SQLException sqle) {
    }
    // Ensure connection still works.
    assertEquals(0, getCount());
  }

  @Test
  void statementCopyIn() throws SQLException {
    Statement stmt = con.createStatement();
    try {
      stmt.execute("COPY copytest FROM STDIN");
      fail("Should have failed because copy doesn't work from a Statement.");
    } catch (SQLException sqle) {
    }
    stmt.close();

    assertEquals(0, getCount());
  }

  @Test
  void statementCopyOut() throws SQLException {
    copyInByRow(); // ensure we have some data.

    Statement stmt = con.createStatement();
    try {
      stmt.execute("COPY copytest TO STDOUT");
      fail("Should have failed because copy doesn't work from a Statement.");
    } catch (SQLException sqle) {
    }
    stmt.close();

    assertEquals(dataRows, getCount());
  }

  @Test
  void copyQuery() throws SQLException, IOException {
    copyInByRow(); // ensure we have some data.

    long count = copyAPI.copyOut("COPY (SELECT generate_series(1,1000)) TO STDOUT",
        new ByteArrayOutputStream());
    assertEquals(1000, count);
  }

  @Test
  void copyRollback() throws SQLException {
    con.setAutoCommit(false);
    copyInByRow();
    con.rollback();
    assertEquals(0, getCount());
  }

  @Test
  void changeDateStyle() throws SQLException {
    try {
      con.setAutoCommit(false);
      con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      CopyManager manager = con.unwrap(PGConnection.class).getCopyAPI();

      Statement stmt = con.createStatement();

      stmt.execute("SET DateStyle = 'ISO, DMY'");

      // I expect an SQLException
      String sql = "COPY copytest FROM STDIN with xxx " + copyParams;
      CopyIn cp = manager.copyIn(sql);
      for (String anOrigData : origData) {
        byte[] buf = anOrigData.getBytes();
        cp.writeToCopy(buf, 0, buf.length);
      }

      long count1 = cp.endCopy();
      long count2 = cp.getHandledRowCount();
      con.commit();
    } catch (SQLException ex) {

      // the with xxx is a syntax error which should return a state of 42601
      // if this fails the 'S' command is not being handled in the copy manager query handler
      assertEquals("42601", ex.getSQLState());
      con.rollback();
    }
  }

  @Test
  @Disabled("We don't support pg_terminate_backend() yet")
  void lockReleaseOnCancelFailure() throws SQLException, InterruptedException {
    if (!TestUtil.haveMinimumServerVersion(con, ServerVersion.v8_4)) {
      // pg_backend_pid() requires PostgreSQL 8.4+
      return;
    }

    // This is a fairly complex test because it is testing a
    // deadlock that only occurs when the connection to postgres
    // is broken during a copy operation. We'll start a copy
    // operation, use pg_terminate_backend to rudely break it,
    // and then cancel. The test passes if a subsequent operation
    // on the Connection object fails to deadlock.
    con.setAutoCommit(false);

    CopyManager manager = con.unwrap(PGConnection.class).getCopyAPI();
    CopyIn copyIn = manager.copyIn("COPY copytest FROM STDIN with " + copyParams);
    TestUtil.terminateBackend(con);
    try {
      byte[] bunchOfNulls = ",,\n".getBytes();
      while (true) {
        copyIn.writeToCopy(bunchOfNulls, 0, bunchOfNulls.length);
      }
    } catch (SQLException e) {
      acceptIOCause(e);
    } finally {
      if (copyIn.isActive()) {
        try {
          copyIn.cancelCopy();
          fail("cancelCopy should have thrown an exception");
        } catch (SQLException e) {
          acceptIOCause(e);
        }
      }
    }

    // Now we'll execute rollback on another thread so that if the
    // deadlock _does_ occur the case doesn't just hange forever.
    Rollback rollback = new Rollback(con);
    rollback.start();
    rollback.join(1000);
    if (rollback.isAlive()) {
      fail("rollback did not terminate");
    }
    SQLException rollbackException = rollback.exception();
    if (rollbackException == null) {
      fail("rollback should have thrown an exception");
    }
    acceptIOCause(rollbackException);
  }

  private static class Rollback extends Thread {
    private final Connection con;
    private SQLException rollbackException;

    Rollback(Connection con) {
      setName("Asynchronous rollback");
      setDaemon(true);
      this.con = con;
    }

    @Override
    public void run() {
      try {
        con.rollback();
      } catch (SQLException e) {
        rollbackException = e;
      }
    }

    public SQLException exception() {
      return rollbackException;
    }
  }

  private void acceptIOCause(SQLException e) throws SQLException {
    if (e.getSQLState().equals(PSQLState.CONNECTION_FAILURE.getState())
        || e.getSQLState().equals(PSQLState.CONNECTION_DOES_NOT_EXIST.getState())) {
      // The test expects network exception, so CONNECTION_FAILURE looks good
      return;
    }
    if (!(e.getCause() instanceof IOException)) {
      throw e;
    }
  }

}

/*
 * Copyright (c) 2005, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.jdbc2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.postgresql.PGProperty;
import org.postgresql.test.TestUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Tests for time and date types with calendars involved. TimestampTest was melting my brain, so I
 * started afresh. -O
 *
 * <p>Conversions that this code tests:</p>
 *
 * <p>setTimestamp -> timestamp, timestamptz, date, time, timetz</p>
 *
 * <p>setDate -> timestamp, timestamptz, date</p>
 *
 * <p>setTime -> time, timetz</p>
 *
 * <p>getTimestamp <- timestamp, timestamptz, date, time, timetz</p>
 *
 * <p>getDate <- timestamp, timestamptz, date</p>
 *
 * <p>getTime <- timestamp, timestamptz, time, timetz</p>
 *
 * <p>(this matches what we must support per JDBC 3.0, tables B-5 and B-6)</p>
 */
public class TimezoneTest {
  private static final int DAY = 24 * 3600 * 1000;
  private static final TimeZone saveTZ = TimeZone.getDefault();
  private static final int PREPARE_THRESHOLD = 2;

  private Connection con;

  //
  // We set up everything in different timezones to try to exercise many cases:
  //
  // default JVM timezone: GMT+0100
  // server timezone: GMT+0300
  // test timezones: GMT+0000 GMT+0100 GMT+0300 GMT+1300 GMT-0500

  private final Calendar cUTC;
  private final Calendar cGMT03;
  private final Calendar cGMT05;
  private final Calendar cGMT13;

  public TimezoneTest() {
    TimeZone tzUTC = TimeZone.getTimeZone("UTC"); // +0000 always
    TimeZone tzGMT03 = TimeZone.getTimeZone("GMT+03"); // +0300 always
    TimeZone tzGMT05 = TimeZone.getTimeZone("GMT-05"); // -0500 always
    TimeZone tzGMT13 = TimeZone.getTimeZone("GMT+13"); // +1000 always

    cUTC = Calendar.getInstance(tzUTC);
    cGMT03 = Calendar.getInstance(tzGMT03);
    cGMT05 = Calendar.getInstance(tzGMT05);
    cGMT13 = Calendar.getInstance(tzGMT13);
  }

  @BeforeEach
  void setUp() throws Exception {
    // We must change the default TZ before establishing the connection.
    // Arbitrary timezone that doesn't match our test timezones
    TimeZone.setDefault(TimeZone.getTimeZone("GMT+01"));

    connect();
    TestUtil.createTable(con, "testtimezone",
        "seq int4, tstz timestamp with time zone, ts timestamp without time zone, t time without time zone, d date");

    // This is not obvious, but the "gmt-3" timezone is actually 3 hours *ahead* of GMT
    // so will produce +03 timestamptz output
    con.createStatement().executeUpdate("set timezone to 'gmt-3'");

    // System.err.println("++++++ TESTS START (" + getName() + ") ++++++");
  }

  private void connect() throws Exception {
    Properties p = new Properties();
    PGProperty.PREPARE_THRESHOLD.set(p, 1);
    con = TestUtil.openDB(p);
  }

  @AfterEach
  void tearDown() throws Exception {
    // System.err.println("++++++ TESTS END (" + getName() + ") ++++++");
    TimeZone.setDefault(saveTZ);

    TestUtil.dropTable(con, "testtimezone");
    TestUtil.closeDB(con);
  }

  @Test
  void getTimestamp() throws Exception {
    con.createStatement().executeUpdate(
        "INSERT INTO testtimezone(tstz,ts,t,d) VALUES('2005-01-01 15:00:00 +0300', '2005-01-01 15:00:00', '15:00:00', '2005-01-01')");

    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      String format = i == 0 ? ", text" : ", binary";
      PreparedStatement ps = con.prepareStatement("SELECT tstz,ts,t,d from testtimezone");
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      checkDatabaseContents("SELECT tstz::text,ts::text,t::text,d::text from testtimezone",
          new String[]{"2005-01-01 12:00:00+00", "2005-01-01 15:00:00", "15:00:00", "2005-01-01"});

      Timestamp ts;
      String str;

      // timestamptz: 2005-01-01 15:00:00+03
      ts = rs.getTimestamp(1); // Represents an instant in time, timezone is irrelevant.
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 12:00:00 UTC
      ts = rs.getTimestamp(1, cUTC); // Represents an instant in time, timezone is irrelevant.
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 12:00:00 UTC
      ts = rs.getTimestamp(1, cGMT03); // Represents an instant in time, timezone is irrelevant.
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 12:00:00 UTC
      ts = rs.getTimestamp(1, cGMT05); // Represents an instant in time, timezone is irrelevant.
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 12:00:00 UTC
      ts = rs.getTimestamp(1, cGMT13); // Represents an instant in time, timezone is irrelevant.
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 12:00:00 UTC
      str = rs.getString(1);
      assertEquals("2005-01-01 15:00:00+03", str, "tstz -> getString" + format);

      // timestamp: 2005-01-01 15:00:00
      ts = rs.getTimestamp(2); // Convert timestamp to +0100
      assertEquals(1104588000000L, ts.getTime()); // 2005-01-01 15:00:00 +0100
      ts = rs.getTimestamp(2, cUTC); // Convert timestamp to UTC
      assertEquals(1104591600000L, ts.getTime()); // 2005-01-01 15:00:00 +0000
      ts = rs.getTimestamp(2, cGMT03); // Convert timestamp to +0300
      assertEquals(1104580800000L, ts.getTime()); // 2005-01-01 15:00:00 +0300
      ts = rs.getTimestamp(2, cGMT05); // Convert timestamp to -0500
      assertEquals(1104609600000L, ts.getTime()); // 2005-01-01 15:00:00 -0500
      ts = rs.getTimestamp(2, cGMT13); // Convert timestamp to +1300
      assertEquals(1104544800000L, ts.getTime()); // 2005-01-01 15:00:00 +1300
      str = rs.getString(2);
      assertEquals("2005-01-01 15:00:00", str, "ts -> getString" + format);

      // time: 15:00:00
      ts = rs.getTimestamp(3);
      assertEquals(50400000L, ts.getTime()); // 1970-01-01 15:00:00 +0100
      ts = rs.getTimestamp(3, cUTC);
      assertEquals(54000000L, ts.getTime()); // 1970-01-01 15:00:00 +0000
      ts = rs.getTimestamp(3, cGMT03);
      assertEquals(43200000L, ts.getTime()); // 1970-01-01 15:00:00 +0300
      ts = rs.getTimestamp(3, cGMT05);
      assertEquals(72000000L, ts.getTime()); // 1970-01-01 15:00:00 -0500
      ts = rs.getTimestamp(3, cGMT13);
      assertEquals(7200000L, ts.getTime()); // 1970-01-01 15:00:00 +1300
      str = rs.getString(3);
      assertEquals("15:00:00", str, "time -> getString" + format);

      // date: 2005-01-01
      ts = rs.getTimestamp(4);
      assertEquals(1104534000000L, ts.getTime()); // 2005-01-01 00:00:00 +0100
      ts = rs.getTimestamp(4, cUTC);
      assertEquals(1104537600000L, ts.getTime()); // 2005-01-01 00:00:00 +0000
      ts = rs.getTimestamp(4, cGMT03);
      assertEquals(1104526800000L, ts.getTime()); // 2005-01-01 00:00:00 +0300
      ts = rs.getTimestamp(4, cGMT05);
      assertEquals(1104555600000L, ts.getTime()); // 2005-01-01 00:00:00 -0500
      ts = rs.getTimestamp(4, cGMT13);
      assertEquals(1104490800000L, ts.getTime()); // 2005-01-01 00:00:00 +1300
      str = rs.getString(4);
      assertEquals("2005-01-01", str, "date -> getString" + format);

      assertFalse(rs.next());
      ps.close();
    }
  }

  @Test
  void getDate() throws Exception {
    con.createStatement().executeUpdate(
        "INSERT INTO testtimezone(tstz,ts,d) VALUES('2005-01-01 15:00:00 +0300', '2005-01-01 15:00:00', '2005-01-01')");

    PreparedStatement ps = con.prepareStatement("SELECT tstz,ts,d from testtimezone");
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      checkDatabaseContents("SELECT tstz::text,ts::text,d::text from testtimezone",
          new String[]{"2005-01-01 12:00:00+00", "2005-01-01 15:00:00", "2005-01-01"});

      Date d;

      // timestamptz: 2005-01-01 15:00:00+03
      d = rs.getDate(1); // 2005-01-01 13:00:00 +0100 -> 2005-01-01 00:00:00 +0100
      assertEquals(1104534000000L, d.getTime());
      d = rs.getDate(1, cUTC); // 2005-01-01 12:00:00 +0000 -> 2005-01-01 00:00:00 +0000
      assertEquals(1104537600000L, d.getTime());
      d = rs.getDate(1, cGMT03); // 2005-01-01 15:00:00 +0300 -> 2005-01-01 00:00:00 +0300
      assertEquals(1104526800000L, d.getTime());
      d = rs.getDate(1, cGMT05); // 2005-01-01 07:00:00 -0500 -> 2005-01-01 00:00:00 -0500
      assertEquals(1104555600000L, d.getTime());
      d = rs.getDate(1, cGMT13); // 2005-01-02 01:00:00 +1300 -> 2005-01-02 00:00:00 +1300
      assertEquals(1104577200000L, d.getTime());

      // timestamp: 2005-01-01 15:00:00
      d = rs.getDate(2); // 2005-01-01 00:00:00 +0100
      assertEquals(1104534000000L, d.getTime());
      d = rs.getDate(2, cUTC); // 2005-01-01 00:00:00 +0000
      assertEquals(1104537600000L, d.getTime());
      d = rs.getDate(2, cGMT03); // 2005-01-01 00:00:00 +0300
      assertEquals(1104526800000L, d.getTime());
      d = rs.getDate(2, cGMT05); // 2005-01-01 00:00:00 -0500
      assertEquals(1104555600000L, d.getTime());
      d = rs.getDate(2, cGMT13); // 2005-01-01 00:00:00 +1300
      assertEquals(1104490800000L, d.getTime());

      // date: 2005-01-01
      d = rs.getDate(3); // 2005-01-01 00:00:00 +0100
      assertEquals(1104534000000L, d.getTime());
      d = rs.getDate(3, cUTC); // 2005-01-01 00:00:00 +0000
      assertEquals(1104537600000L, d.getTime());
      d = rs.getDate(3, cGMT03); // 2005-01-01 00:00:00 +0300
      assertEquals(1104526800000L, d.getTime());
      d = rs.getDate(3, cGMT05); // 2005-01-01 00:00:00 -0500
      assertEquals(1104555600000L, d.getTime());
      d = rs.getDate(3, cGMT13); // 2005-01-01 00:00:00 +1300
      assertEquals(1104490800000L, d.getTime());

      assertFalse(rs.next());
      rs.close();
    }
  }

  @Test
  void getTime() throws Exception {
    con.createStatement().executeUpdate(
        "INSERT INTO testtimezone(tstz,ts,t) VALUES('2005-01-01 15:00:00 +0300', '2005-01-01 15:00:00', '15:00:00')");

    PreparedStatement ps = con.prepareStatement("SELECT tstz,ts,t from testtimezone");
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      checkDatabaseContents("SELECT tstz::text,ts::text,t::text,d::text from testtimezone",
          new String[]{"2005-01-01 12:00:00+00", "2005-01-01 15:00:00", "15:00:00"});

      Time t;

      // timestamptz: 2005-01-01 15:00:00+03
      t = rs.getTime(1);
      // 2005-01-01 13:00:00 +0100 -> 1970-01-01 13:00:00 +0100
      assertEquals(43200000L, t.getTime());
      t = rs.getTime(1, cUTC);
      // 2005-01-01 12:00:00 +0000 -> 1970-01-01 12:00:00 +0000
      assertEquals(43200000L, t.getTime());
      t = rs.getTime(1, cGMT03);
      // 2005-01-01 15:00:00 +0300 -> 1970-01-01 15:00:00 +0300
      assertEquals(43200000L, t.getTime());
      t = rs.getTime(1, cGMT05);
      // 2005-01-01 07:00:00 -0500 -> 1970-01-01 07:00:00 -0500
      assertEquals(43200000L, t.getTime());
      t = rs.getTime(1, cGMT13);
      // 2005-01-02 01:00:00 +1300 -> 1970-01-01 01:00:00 +1300
      assertEquals(43200000L, t.getTime());

      // timestamp: 2005-01-01 15:00:00
      t = rs.getTime(2);
      assertEquals(50400000L, t.getTime()); // 1970-01-01 15:00:00 +0100
      t = rs.getTime(2, cUTC);
      assertEquals(54000000L, t.getTime()); // 1970-01-01 15:00:00 +0000
      t = rs.getTime(2, cGMT03);
      assertEquals(43200000L, t.getTime()); // 1970-01-01 15:00:00 +0300
      t = rs.getTime(2, cGMT05);
      assertEquals(72000000L, t.getTime()); // 1970-01-01 15:00:00 -0500
      t = rs.getTime(2, cGMT13);
      assertEquals(7200000L, t.getTime()); // 1970-01-01 15:00:00 +1300

      // time: 15:00:00
      t = rs.getTime(3);
      assertEquals(50400000L, t.getTime()); // 1970-01-01 15:00:00 +0100
      t = rs.getTime(3, cUTC);
      assertEquals(54000000L, t.getTime()); // 1970-01-01 15:00:00 +0000
      t = rs.getTime(3, cGMT03);
      assertEquals(43200000L, t.getTime()); // 1970-01-01 15:00:00 +0300
      t = rs.getTime(3, cGMT05);
      assertEquals(72000000L, t.getTime()); // 1970-01-01 15:00:00 -0500
      t = rs.getTime(3, cGMT13);
      assertEquals(7200000L, t.getTime()); // 1970-01-01 15:00:00 +1300

      rs.close();
    }
  }

  /**
   * This test is broken off from testSetTimestamp because it does not work for pre-7.4 servers and
   * putting tons of conditionals in that test makes it largely unreadable. The time data type does
   * not accept timestamp with time zone style input on these servers.
   */
  @Test
  void setTimestampOnTime() throws Exception {
    // Pre-7.4 servers cannot convert timestamps with timezones to times.
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      con.createStatement().execute("delete from testtimezone");
      PreparedStatement insertTimestamp =
          con.prepareStatement("INSERT INTO testtimezone(seq,t) VALUES (?,?)");
      int seq = 1;

      Timestamp instant = new Timestamp(1104580800000L); // 2005-01-01 12:00:00 UTC
      Timestamp instantTime = new Timestamp(instant.getTime() % DAY);

      // +0100 (JVM default)
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant); // 13:00:00
      insertTimestamp.executeUpdate();

      // UTC
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cUTC); // 12:00:00
      insertTimestamp.executeUpdate();

      // +0300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT03); // 15:00:00
      insertTimestamp.executeUpdate();

      // -0500
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT05); // 07:00:00
      insertTimestamp.executeUpdate();

      // +1300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT13); // 01:00:00
      insertTimestamp.executeUpdate();

      insertTimestamp.close();

      checkDatabaseContents("SELECT seq::text,t::text from testtimezone ORDER BY seq",
          new String[][]{new String[]{"1", "13:00:00"}, new String[]{"2", "12:00:00"},
              new String[]{"3", "15:00:00"}, new String[]{"4", "07:00:00"},
              new String[]{"5", "01:00:00"}});

      seq = 1;
      PreparedStatement ps = con.prepareStatement("SELECT seq,t FROM testtimezone ORDER BY seq");
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instantTime, rs.getTimestamp(2));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instantTime, rs.getTimestamp(2, cUTC));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instantTime, rs.getTimestamp(2, cGMT03));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instantTime, rs.getTimestamp(2, cGMT05));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(normalizeTimeOfDayPart(instantTime, cGMT13), rs.getTimestamp(2, cGMT13));

      assertFalse(rs.next());
      ps.close();
    }
  }

  @Test
  void setTimestamp() throws Exception {
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      con.createStatement().execute("delete from testtimezone");
      PreparedStatement insertTimestamp =
          con.prepareStatement("INSERT INTO testtimezone(seq,tstz,ts,d) VALUES (?,?,?,?)");
      int seq = 1;

      Timestamp instant = new Timestamp(1104580800000L); // 2005-01-01 12:00:00 UTC
      Timestamp instantTime = new Timestamp(instant.getTime() % DAY);
      Timestamp instantDateJVM = new Timestamp(
          instant.getTime() - (instant.getTime() % DAY) - TimeZone.getDefault().getRawOffset());
      Timestamp instantDateUTC = new Timestamp(
          instant.getTime() - (instant.getTime() % DAY) - cUTC.getTimeZone().getRawOffset());
      Timestamp instantDateGMT03 = new Timestamp(
          instant.getTime() - (instant.getTime() % DAY) - cGMT03.getTimeZone().getRawOffset());
      Timestamp instantDateGMT05 = new Timestamp(
          instant.getTime() - (instant.getTime() % DAY) - cGMT05.getTimeZone().getRawOffset());
      Timestamp instantDateGMT13 = new Timestamp(instant.getTime() - (instant.getTime() % DAY)
          - cGMT13.getTimeZone().getRawOffset() + DAY);

      // +0100 (JVM default)
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant); // 2005-01-01 13:00:00 +0100
      insertTimestamp.setTimestamp(3, instant); // 2005-01-01 13:00:00
      insertTimestamp.setTimestamp(4, instant); // 2005-01-01
      insertTimestamp.executeUpdate();

      // UTC
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cUTC); // 2005-01-01 12:00:00 +0000
      insertTimestamp.setTimestamp(3, instant, cUTC); // 2005-01-01 12:00:00
      insertTimestamp.setTimestamp(4, instant, cUTC); // 2005-01-01
      insertTimestamp.executeUpdate();

      // +0300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT03); // 2005-01-01 15:00:00 +0300
      insertTimestamp.setTimestamp(3, instant, cGMT03); // 2005-01-01 15:00:00
      insertTimestamp.setTimestamp(4, instant, cGMT03); // 2005-01-01
      insertTimestamp.executeUpdate();

      // -0500
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT05); // 2005-01-01 07:00:00 -0500
      insertTimestamp.setTimestamp(3, instant, cGMT05); // 2005-01-01 07:00:00
      insertTimestamp.setTimestamp(4, instant, cGMT05); // 2005-01-01
      insertTimestamp.executeUpdate();

      // +1300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTimestamp(2, instant, cGMT13); // 2005-01-02 01:00:00 +1300
      insertTimestamp.setTimestamp(3, instant, cGMT13); // 2005-01-02 01:00:00
      insertTimestamp.setTimestamp(4, instant, cGMT13); // 2005-01-02
      insertTimestamp.executeUpdate();

      insertTimestamp.close();

      // check that insert went correctly by parsing the raw contents in UTC
      checkDatabaseContents(
          "SELECT seq::text,tstz::text,ts::text,d::text from testtimezone ORDER BY seq",
          new String[][]{
              new String[]{"1", "2005-01-01 12:00:00+00", "2005-01-01 13:00:00", "2005-01-01"},
              new String[]{"2", "2005-01-01 12:00:00+00", "2005-01-01 12:00:00", "2005-01-01"},
              new String[]{"3", "2005-01-01 12:00:00+00", "2005-01-01 15:00:00", "2005-01-01"},
              new String[]{"4", "2005-01-01 12:00:00+00", "2005-01-01 07:00:00", "2005-01-01"},
              new String[]{"5", "2005-01-01 12:00:00+00", "2005-01-02 01:00:00", "2005-01-02"}});

      //
      // check results
      //

      seq = 1;
      PreparedStatement ps =
          con.prepareStatement("SELECT seq,tstz,ts,d FROM testtimezone ORDER BY seq");
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instant, rs.getTimestamp(2));
      assertEquals(instant, rs.getTimestamp(3));
      assertEquals(instantDateJVM, rs.getTimestamp(4));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instant, rs.getTimestamp(2, cUTC));
      assertEquals(instant, rs.getTimestamp(3, cUTC));
      assertEquals(instantDateUTC, rs.getTimestamp(4, cUTC));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instant, rs.getTimestamp(2, cGMT03));
      assertEquals(instant, rs.getTimestamp(3, cGMT03));
      assertEquals(instantDateGMT03, rs.getTimestamp(4, cGMT03));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instant, rs.getTimestamp(2, cGMT05));
      assertEquals(instant, rs.getTimestamp(3, cGMT05));
      assertEquals(instantDateGMT05, rs.getTimestamp(4, cGMT05));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(instant, rs.getTimestamp(2, cGMT13));
      assertEquals(instant, rs.getTimestamp(3, cGMT13));
      assertEquals(instantDateGMT13, rs.getTimestamp(4, cGMT13));

      assertFalse(rs.next());
      ps.close();
    }
  }

  @Test
  void setDate() throws Exception {
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      con.createStatement().execute("delete from testtimezone");
      PreparedStatement insertTimestamp =
          con.prepareStatement("INSERT INTO testtimezone(seq,tstz,ts,d) VALUES (?,?,?,?)");

      int seq = 1;

      Date dJVM;
      Date dUTC;
      Date dGMT03;
      Date dGMT05;
      Date dGMT13 = null;

      // +0100 (JVM default)
      dJVM = new Date(1104534000000L); // 2005-01-01 00:00:00 +0100
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setDate(2, dJVM); // 2005-01-01 00:00:00 +0100
      insertTimestamp.setDate(3, dJVM); // 2005-01-01 00:00:00
      insertTimestamp.setDate(4, dJVM); // 2005-01-01
      insertTimestamp.executeUpdate();

      // UTC
      dUTC = new Date(1104537600000L); // 2005-01-01 00:00:00 +0000
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setDate(2, dUTC, cUTC); // 2005-01-01 00:00:00 +0000
      insertTimestamp.setDate(3, dUTC, cUTC); // 2005-01-01 00:00:00
      insertTimestamp.setDate(4, dUTC, cUTC); // 2005-01-01
      insertTimestamp.executeUpdate();

      // +0300
      dGMT03 = new Date(1104526800000L); // 2005-01-01 00:00:00 +0300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setDate(2, dGMT03, cGMT03); // 2005-01-01 00:00:00 +0300
      insertTimestamp.setDate(3, dGMT03, cGMT03); // 2005-01-01 00:00:00
      insertTimestamp.setDate(4, dGMT03, cGMT03); // 2005-01-01
      insertTimestamp.executeUpdate();

      // -0500
      dGMT05 = new Date(1104555600000L); // 2005-01-01 00:00:00 -0500
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setDate(2, dGMT05, cGMT05); // 2005-01-01 00:00:00 -0500
      insertTimestamp.setDate(3, dGMT05, cGMT05); // 2005-01-01 00:00:00
      insertTimestamp.setDate(4, dGMT05, cGMT05); // 2005-01-01
      insertTimestamp.executeUpdate();

      // +1300
      dGMT13 = new Date(1104490800000L); // 2005-01-01 00:00:00 +1300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setDate(2, dGMT13, cGMT13); // 2005-01-01 00:00:00 +1300
      insertTimestamp.setDate(3, dGMT13, cGMT13); // 2005-01-01 00:00:00
      insertTimestamp.setDate(4, dGMT13, cGMT13); // 2005-01-01
      insertTimestamp.executeUpdate();

      insertTimestamp.close();

      // check that insert went correctly by parsing the raw contents in UTC
      checkDatabaseContents(
          "SELECT seq::text,tstz::text,ts::text,d::text from testtimezone ORDER BY seq",
          new String[][]{
              new String[]{"1", "2004-12-31 23:00:00+00", "2005-01-01 00:00:00", "2005-01-01"},
              new String[]{"2", "2005-01-01 00:00:00+00", "2005-01-01 00:00:00", "2005-01-01"},
              new String[]{"3", "2004-12-31 21:00:00+00", "2005-01-01 00:00:00", "2005-01-01"},
              new String[]{"4", "2005-01-01 05:00:00+00", "2005-01-01 00:00:00", "2005-01-01"},
              new String[]{"5", "2004-12-31 11:00:00+00", "2005-01-01 00:00:00", "2005-01-01"}});
      //
      // check results
      //

      seq = 1;
      PreparedStatement ps =
          con.prepareStatement("SELECT seq,tstz,ts,d FROM testtimezone ORDER BY seq");
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(dJVM, rs.getDate(2));
      assertEquals(dJVM, rs.getDate(3));
      assertEquals(dJVM, rs.getDate(4));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(dUTC, rs.getDate(2, cUTC));
      assertEquals(dUTC, rs.getDate(3, cUTC));
      assertEquals(dUTC, rs.getDate(4, cUTC));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(dGMT03, rs.getDate(2, cGMT03));
      assertEquals(dGMT03, rs.getDate(3, cGMT03));
      assertEquals(dGMT03, rs.getDate(4, cGMT03));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(dGMT05, rs.getDate(2, cGMT05));
      assertEquals(dGMT05, rs.getDate(3, cGMT05));
      assertEquals(dGMT05, rs.getDate(4, cGMT05));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(dGMT13, rs.getDate(2, cGMT13));
      assertEquals(dGMT13, rs.getDate(3, cGMT13));
      assertEquals(dGMT13, rs.getDate(4, cGMT13));

      assertFalse(rs.next());
      ps.close();
    }
  }

  @Test
  void setTime() throws Exception {
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      con.createStatement().execute("delete from testtimezone");
      PreparedStatement insertTimestamp =
          con.prepareStatement("INSERT INTO testtimezone(seq,t) VALUES (?,?)");

      int seq = 1;

      Time tJVM;
      Time tUTC;
      Time tGMT03;
      Time tGMT05;
      Time tGMT13;

      // +0100 (JVM default)
      tJVM = new Time(50400000L); // 1970-01-01 15:00:00 +0100
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTime(2, tJVM); // 15:00:00
      insertTimestamp.executeUpdate();

      // UTC
      tUTC = new Time(54000000L); // 1970-01-01 15:00:00 +0000
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTime(2, tUTC, cUTC); // 15:00:00
      insertTimestamp.executeUpdate();

      // +0300
      tGMT03 = new Time(43200000L); // 1970-01-01 15:00:00 +0300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTime(2, tGMT03, cGMT03); // 15:00:00
      insertTimestamp.executeUpdate();

      // -0500
      tGMT05 = new Time(72000000L); // 1970-01-01 15:00:00 -0500
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTime(2, tGMT05, cGMT05); // 15:00:00
      insertTimestamp.executeUpdate();

      // +1300
      tGMT13 = new Time(7200000L); // 1970-01-01 15:00:00 +1300
      insertTimestamp.setInt(1, seq++);
      insertTimestamp.setTime(2, tGMT13, cGMT13); // 15:00:00
      insertTimestamp.executeUpdate();

      insertTimestamp.close();

      // check that insert went correctly by parsing the raw contents in UTC
      checkDatabaseContents("SELECT seq::text,t::text from testtimezone ORDER BY seq",
          new String[][]{new String[]{"1", "15:00:00",},
              new String[]{"2", "15:00:00",},
              new String[]{"3", "15:00:00",},
              new String[]{"4", "15:00:00",},
              new String[]{"5", "15:00:00",}});

      //
      // check results
      //

      seq = 1;
      PreparedStatement ps = con.prepareStatement("SELECT seq,t FROM testtimezone ORDER BY seq");
      ResultSet rs = ps.executeQuery();

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(tJVM, rs.getTime(2));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(tUTC, rs.getTime(2, cUTC));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(tGMT03, rs.getTime(2, cGMT03));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(tGMT05, rs.getTime(2, cGMT05));

      assertTrue(rs.next());
      assertEquals(seq++, rs.getInt(1));
      assertEquals(tGMT13, rs.getTime(2, cGMT13));

      assertFalse(rs.next());
      ps.close();
    }
  }

  @Test
  void halfHourTimezone() throws Exception {
    Statement stmt = con.createStatement();
    stmt.execute("SET TimeZone = 'GMT+3:30'");
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      PreparedStatement ps = con.prepareStatement("SELECT '1969-12-31 20:30:00'::timestamptz");
      ResultSet rs = ps.executeQuery();
      assertTrue(rs.next());
      assertEquals(0L, rs.getTimestamp(1).getTime());
      ps.close();
    }
  }

  @Test
  void timezoneWithSeconds() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("SET TimeZone = 'Europe/Paris'");
    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      PreparedStatement ps = con.prepareStatement("SELECT '1920-01-01'::timestamptz");
      ResultSet rs = ps.executeQuery();
      rs.next();
      // select extract(epoch from '1920-01-01'::timestamptz - 'epoch'::timestamptz) * 1000;

      assertEquals(-1577923200000L, rs.getTimestamp(1).getTime());
      ps.close();
    }
  }

  @Test
  void localTimestampsInNonDSTZones() throws Exception {
    for (int i = -12; i <= 13; i++) {
      localTimestamps(String.format("GMT%02d", i));
    }
  }

  @Test
  void localTimestampsInAfricaCasablanca() throws Exception {
    localTimestamps("Africa/Casablanca"); // It is something like GMT+0..GMT+1
  }

  @Test
  void localTimestampsInAtlanticAzores() throws Exception {
    localTimestamps("Atlantic/Azores"); // It is something like GMT-1..GMT+0
  }

  @Test
  void localTimestampsInEuropeMoscow() throws Exception {
    localTimestamps("Europe/Moscow"); // It is something like GMT+3..GMT+4 for 2000s
  }

  @Test
  void localTimestampsInPacificApia() throws Exception {
    localTimestamps("Pacific/Apia"); // It is something like GMT+13..GMT+14
  }

  @Test
  void localTimestampsInPacificNiue() throws Exception {
    localTimestamps("Pacific/Niue"); // It is something like GMT-11..GMT-11
  }

  @Test
  void localTimestampsInAmericaAdak() throws Exception {
    localTimestamps("America/Adak"); // It is something like GMT-10..GMT-9
  }

  private String setTimeTo00_00_00(String timestamp) {
    return timestamp.substring(0, 11) + "00:00:00";
  }

  public void localTimestamps(String timeZone) throws Exception {
    TimeZone.setDefault(TimeZone.getTimeZone(timeZone));

    final String testDateFormat = "yyyy-MM-dd HH:mm:ss";
    final List<String> datesToTest = Arrays.asList("2015-09-03 12:00:00", "2015-06-30 23:59:58",
        "1997-06-30 23:59:59", "1997-07-01 00:00:00", "2012-06-30 23:59:59", "2012-07-01 00:00:00",
        "2015-06-30 23:59:59", "2015-07-01 00:00:00", "2005-12-31 23:59:59", "2006-01-01 00:00:00",
        "2008-12-31 23:59:59", "2009-01-01 00:00:00", "2015-06-30 23:59:60", "2015-07-31 00:00:00",
        "2015-07-31 00:00:01",

        // On 2000-03-26 02:00:00 Moscow went to DST, thus local time became 03:00:00
        "2000-03-26 01:59:59", "2000-03-26 02:00:00", "2000-03-26 02:00:01", "2000-03-26 02:59:59",
        "2000-03-26 03:00:00", "2000-03-26 03:00:01", "2000-03-26 03:59:59", "2000-03-26 04:00:00",
        "2000-03-26 04:00:01",

        // This is a pre-1970 date, so check if it is rounded properly
        "1950-07-20 02:00:00",

        // On 2000-10-29 03:00:00 Moscow went to regular time, thus local time became 02:00:00
        "2000-10-29 01:59:59", "2000-10-29 02:00:00", "2000-10-29 02:00:01", "2000-10-29 02:59:59",
        "2000-10-29 03:00:00", "2000-10-29 03:00:01", "2000-10-29 03:59:59", "2000-10-29 04:00:00",
        "2000-10-29 04:00:01");

    con.createStatement().execute("delete from testtimezone");
    Statement stmt = con.createStatement();

    for (int i = 0; i < datesToTest.size(); i++) {
      stmt.execute(
          "insert into testtimezone (ts, d, seq) values ("
              + "'" + datesToTest.get(i) + "'"
              + ", '" + setTimeTo00_00_00(datesToTest.get(i)) + "'"
              + ", " + i + ")");
    }

    // Different timezone test should have different sql text, so we test both text and binary modes
    PreparedStatement pstmt =
        con.prepareStatement("SELECT ts, d FROM testtimezone order by seq /*" + timeZone + "*/");

    Calendar expectedTimestamp = Calendar.getInstance();

    SimpleDateFormat sdf = new SimpleDateFormat(testDateFormat);

    for (int i = 0; i < PREPARE_THRESHOLD; i++) {
      ResultSet rs = pstmt.executeQuery();
      for (int j = 0; rs.next(); j++) {
        String testDate = datesToTest.get(j);
        Date getDate = rs.getDate(1);
        Date getDateFromDateColumn = rs.getDate(2);
        Timestamp getTimestamp = rs.getTimestamp(1);
        String getString = rs.getString(1);
        Time getTime = rs.getTime(1);
        expectedTimestamp.setTime(sdf.parse(testDate));

        assertEquals(
            sdf.format(expectedTimestamp.getTimeInMillis()), sdf.format(getTimestamp), "getTimestamp: " + testDate + ", transfer format: " + (i == 0 ? "text" : "binary")
                + ", timeZone: " + timeZone);

        assertEquals(
            sdf.format(expectedTimestamp.getTimeInMillis()), sdf.format(sdf.parse(getString)), "getString: " + testDate + ", transfer format: " + (i == 0 ? "text" : "binary")
                + ", timeZone: " + timeZone);

        expectedTimestamp.set(Calendar.HOUR_OF_DAY, 0);
        expectedTimestamp.set(Calendar.MINUTE, 0);
        expectedTimestamp.set(Calendar.SECOND, 0);

        assertEquals(
            sdf.format(expectedTimestamp.getTimeInMillis()), sdf.format(getDate), "TIMESTAMP -> getDate: " + testDate + ", transfer format: " + (i == 0 ? "text" : "binary")
                + ", timeZone: " + timeZone);

        String expectedDateFromDateColumn = setTimeTo00_00_00(testDate);
        if ("Atlantic/Azores".equals(timeZone) && testDate.startsWith("2000-03-26")) {
          // Atlantic/Azores does not have 2000-03-26 00:00:00
          // They go right to 2000-03-26 01:00:00 due to DST.
          // Vladimir Sitnikov: I have no idea how do they represent 2000-03-26 00:00:00 :(
          // So the assumption is 2000-03-26 01:00:00 is the expected for that time zone
          expectedDateFromDateColumn = "2000-03-26 01:00:00";
        }

        assertEquals(
            expectedDateFromDateColumn, sdf.format(getDateFromDateColumn), "DATE -> getDate: " + expectedDateFromDateColumn + ", transfer format: " + (i == 0 ? "text" : "binary")
                + ", timeZone: " + timeZone);

        expectedTimestamp.setTime(sdf.parse(testDate));
        expectedTimestamp.set(Calendar.YEAR, 1970);
        expectedTimestamp.set(Calendar.MONTH, 0);
        expectedTimestamp.set(Calendar.DAY_OF_MONTH, 1);

        assertEquals(
            sdf.format(expectedTimestamp.getTimeInMillis()), sdf.format(getTime), "getTime: " + testDate + ", transfer format: " + (i == 0 ? "text" : "binary")
                + ", timeZone: " + timeZone);

      }
      rs.close();
    }
  }

  /**
   * Does a query in UTC time zone to database to check that the inserted values are correct.
   *
   * @param query The query to run.
   * @param correct The correct answers in UTC time zone as formatted by backend.
   */
  private void checkDatabaseContents(String query, String[] correct) throws Exception {
    checkDatabaseContents(query, new String[][]{correct});
  }

  private void checkDatabaseContents(String query, String[][] correct) throws Exception {
    Connection con2 = TestUtil.openDB();
    Statement s = con2.createStatement();
    assertFalse(s.execute("set time zone 'UTC'"));
    assertTrue(s.execute(query));
    ResultSet rs = s.getResultSet();
    for (int j = 0; j < correct.length; j++) {
      assertTrue(rs.next());
      for (int i = 0; i < correct[j].length; i++) {
        assertEquals(correct[j][i], rs.getString(i + 1), "On row " + (j + 1));
      }
    }
    assertFalse(rs.next());
    rs.close();
    s.close();
    con2.close();
  }

  /**
   * Converts the given time.
   *
   * @param t The time of day. Must be within -24 and + 24 hours of epoc.
   * @param tz The timezone to normalize to.
   * @return the Time normalized to 0 to 24 hours of epoc adjusted with given timezone.
   */
  private Timestamp normalizeTimeOfDayPart(Timestamp t, Calendar tz) {
    return new Timestamp(normalizeTimeOfDayPart(t.getTime(), tz.getTimeZone()));
  }

  private long normalizeTimeOfDayPart(long t, TimeZone tz) {
    long millis = t;
    long low = -tz.getOffset(millis);
    long high = low + DAY;
    if (millis < low) {
      do {
        millis += DAY;
      } while (millis < low);
    } else if (millis >= high) {
      do {
        millis -= DAY;
      } while (millis > high);
    }
    return millis;
  }
}

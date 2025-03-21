/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package org.postgresql.test.jdbc2;

import static org.junit.Assert.assertEquals;

import org.postgresql.PGProperty;
import org.postgresql.core.Oid;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

@RunWith(Parameterized.class)
public class NumericTransferTest extends BaseTest4 {
  public NumericTransferTest(BinaryMode binaryMode) {
    setBinaryMode(binaryMode);
  }

  @Override
  protected void updateProperties(Properties props) {
    super.updateProperties(props);
    PGProperty.BINARY_TRANSFER_ENABLE.set(props, Oid.NUMERIC);
  }

  @Parameterized.Parameters(name = "binary = {0}")
  public static Iterable<Object[]> data() {
    Collection<Object[]> ids = new ArrayList<>();
    for (BinaryMode binaryMode : BinaryMode.values()) {
      ids.add(new Object[]{binaryMode});
    }
    return ids;
  }

  @Test
  public void receive100000() throws SQLException {
    Statement statement = con.createStatement();
    for (String sign : new String[]{"", "-"}) {
      for (int i = 0; i < 32; i++) {
        final String input = (i == 0) ? sign + "1" : sign + String.format("1%0" + i + "d", 0);
        final String sql = "SELECT " + input + "::bignumeric(38,0)";
        ResultSet rs = statement.executeQuery(sql);
        rs.next();
        if (i == 0) {
          assertEquals("getString for " + sql, input, rs.getString(1));
          assertEquals("getBigDecimal for " + sql, input, rs.getBigDecimal(1).toString());
        } else {
          assertEquals("getString for " + sql, input, rs.getString(1));
          assertEquals("getBigDecimal for " + sql, input, rs.getBigDecimal(1).toString());
        }
        rs.close();
      }
    }
    statement.close();
  }

  @Test
  public void sendReceive100000() throws SQLException {
    PreparedStatement statement = con.prepareStatement("select ?::bignumeric(38,0)");
    for (String sign : new String[]{"", "-"}) {
      for (int i = 0; i < 32; i++) {
        final String expected = sign + (i == 0 ? 1 : String.format("1%0" + i + "d", 0));
        statement.setBigDecimal(1, new BigDecimal(expected));
        ResultSet rs = statement.executeQuery();
        rs.next();
        assertEquals("getString for " + expected, expected, rs.getString(1));
        assertEquals("getBigDecimal for " + expected, expected, rs.getBigDecimal(1).toString());
        rs.close();
      }
    }
    statement.close();
  }
}

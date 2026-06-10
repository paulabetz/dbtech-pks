package de.htwberlin.dbtech.aufgaben.ue03;

import de.htwberlin.dbtech.exceptions.DataException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Place {
    private static final Logger L = LoggerFactory.getLogger(Place.class);
    private Connection connection;
    private Integer trayId;
    private Integer sampleId;

    public void setConnection(Connection connection) { this.connection = connection; }
    public void setTrayId(Integer trayId) { this.trayId = trayId; }
    public void setSampleId(Integer sampleId) { this.sampleId = sampleId; }

    public void updateTrayExpirationDate(Integer sampleId) {
        String sql = "UPDATE Tray SET ExpirationDate = " +
                "(SELECT s.ExpirationDate + 30 FROM Sample s WHERE s.SampleId = ?) " +
                "WHERE TrayId = ? AND ExpirationDate IS NULL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, sampleId);
            ps.setInt(2, trayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    public void insert() {
        String sql = "INSERT INTO Place (TrayId, PlaceNo, SampleId) " +
                "SELECT ?, " +
                "(SELECT MIN(nr) FROM " +
                "  (SELECT LEVEL nr FROM DUAL CONNECT BY LEVEL <= (SELECT Capacity FROM Tray WHERE TrayId = ?)) " +
                "  WHERE nr NOT IN (SELECT PlaceNo FROM Place WHERE TrayId = ?)), " +
                "? FROM DUAL";
        L.info(sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, trayId);
            ps.setInt(2, trayId);
            ps.setInt(3, trayId);
            ps.setInt(4, sampleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }
}

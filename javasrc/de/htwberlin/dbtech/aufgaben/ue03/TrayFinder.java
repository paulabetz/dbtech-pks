package de.htwberlin.dbtech.aufgaben.ue03;

import de.htwberlin.dbtech.exceptions.DataException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrayFinder {
    private static final Logger L = LoggerFactory.getLogger(TrayFinder.class);
    private Connection connection;

    public void setConnection(Connection connection) { this.connection = connection; }

    public boolean existsSample(Integer sampleId) {
        String sql = "SELECT SampleId FROM Sample WHERE SampleId = ?";
        L.info(sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    public boolean existsFittingTray(Integer diameterInCM) {
        String sql = "SELECT COUNT(*) as anzahl FROM Tray WHERE DiameterInCM = ?";
        L.info(sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("anzahl") > 0;
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    public boolean existsFreeTrayWithExpirationDate(Integer sampleId, Integer diameterInCM) {
        String sql = "SELECT COUNT(*) as anzahl FROM Tray t " +
                "WHERE t.DiameterInCM = ? " +
                "AND t.ExpirationDate >= (SELECT s.ExpirationDate FROM Sample s WHERE s.SampleId = ?) " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            ps.setInt(2, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("anzahl") > 0;
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    public boolean existsFreeTrayWithNullExpirationDate(Integer diameterInCM) {
        String sql = "SELECT COUNT(*) as anzahl FROM Tray t " +
                "WHERE t.DiameterInCM = ? " +
                "AND t.ExpirationDate IS NULL " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("anzahl") > 0;
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    public Integer findSuitableTrayId(Integer sampleId, Integer diameterInCM) {
        String sql = "SELECT t.TrayId FROM Tray t " +
                "WHERE t.DiameterInCM = ? " +
                "AND (t.ExpirationDate IS NULL OR t.ExpirationDate >= (SELECT s.ExpirationDate FROM Sample s WHERE s.SampleId = ?)) " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity " +
                "ORDER BY t.TrayId " +
                "FETCH FIRST 1 ROW ONLY";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            ps.setInt(2, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("TrayId");
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }
}
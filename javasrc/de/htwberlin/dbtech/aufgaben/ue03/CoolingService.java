package de.htwberlin.dbtech.aufgaben.ue03;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import de.htwberlin.dbtech.exceptions.ServiceException;
import org.dbunit.Assertion;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.QueryDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.csv.CsvDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.dbtech.exceptions.DataException;

public class CoolingService implements ICoolingService {
    private static final Logger L = LoggerFactory.getLogger(CoolingService.class);
    private Connection connection;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("unused")
    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public void transferSample(Integer sampleId, Integer diameterInCM) {
        L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);
        if(!existenceSampleIdinDB(sampleId)) {
            throw new CoolingSystemException("sampleId existiert nicht in der Datenbank");
        }
        if(!nofittingtraysize(sampleId, diameterInCM)) {
            throw new CoolingSystemException("Kein passendes Tablet für die Größe gefunden");
        }
        boolean fittingDateTray = nofittingtraysizeBUTfittingexpirationdate(sampleId, diameterInCM);
        boolean nullDateTray = nofittingtraysizeBUTnullexpirationdate(sampleId, diameterInCM);

        if (!fittingDateTray && !nullDateTray) {
            throw new CoolingSystemException("Alle passenden Tabletts sind voll");
        }

        safeSample(sampleId, diameterInCM);

    }

    /**
     * SampleID existiert nicht.
     */
    private boolean existenceSampleIdinDB(Integer sampleId) {
        L.info("SampleId: " + sampleId);
        String sql = "SELECT SampleId FROM Sample WHERE SampleId=?";
        L.info(sql);
        try (PreparedStatement ps = useConnection().prepareStatement(sql)) {
            ps.setInt(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    /**
     * Kein Tablett mit passenden Durchmesser vorhanden.
     */
    private boolean nofittingtraysize(Integer sampleId, Integer diameterInCM) {
        L.info("sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);
        String sql = "SELECT COUNT(*) as anzahl FROM Tray WHERE DiameterInCM >= ?";
        L.info(sql);
        try (PreparedStatement ps = useConnection().prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int anzahl = rs.getInt("anzahl");
                L.info("anzahl passende Trays: " + anzahl);
                return anzahl > 0;
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }




    /**
     * Ablaufdatum Probe nicht zu gross. Alle passenden Tabletts voll. Kein freies
     * Tablett richtiger Groesse vorhanden
     */
    private boolean nofittingtraysizeBUTfittingexpirationdate(Integer sampleId, Integer diameterInCM) {
        String sql = "SELECT COUNT(*) as anzahl FROM Tray t " +
                "WHERE t.DiameterInCM = ? " +
                "AND t.ExpirationDate >= (SELECT s.ExpirationDate FROM Sample s WHERE s.SampleId = ?) " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity";
        try (PreparedStatement ps = useConnection().prepareStatement(sql)) {
            ps.setInt(1, diameterInCM);
            ps.setInt(2, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int anzahl = rs.getInt("anzahl");
                L.info("anzahl freie Trays: " + anzahl);
                return anzahl > 0;
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }
    }

    private boolean nofittingtraysizeBUTnullexpirationdate(Integer sampleId, Integer diameterInCM) {
        String sql = "SELECT COUNT(*) as anzahl FROM Tray t " +
                "WHERE t.DiameterInCM = ? " +
                "AND t.ExpirationDate IS NULL " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity";
        try (PreparedStatement ps = useConnection().prepareStatement(sql)) {
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




    private void safeSample(Integer sampleId, Integer diameterInCM) {
        L.info("safeSample called for sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);
        // Erst TrayId ermitteln
        String sqlSelect = "SELECT t.TrayId FROM Tray t " +
                "WHERE t.DiameterInCM >= ? " +
                "AND (t.ExpirationDate IS NULL OR t.ExpirationDate >= (SELECT s.ExpirationDate FROM Sample s WHERE s.SampleId = ?)) " +
                "AND (SELECT COUNT(*) FROM Place p WHERE p.TrayId = t.TrayId) < t.Capacity " +
                "ORDER BY t.TrayId " +
                "FETCH FIRST 1 ROW ONLY";
        int trayId;
        try (PreparedStatement ps = useConnection().prepareStatement(sqlSelect)) {
            ps.setInt(1, diameterInCM);
            ps.setInt(2, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                trayId = rs.getInt("TrayId");
            }
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }

        // Tray ExpirationDate aktualisieren
        String sqlUpdate = "UPDATE Tray SET ExpirationDate = " +
                "(SELECT s.ExpirationDate + 30 " +
                "FROM Sample s WHERE s.SampleId = ?) " +
                "WHERE TrayId = ? AND ExpirationDate IS NULL";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlUpdate)) {
            ps.setInt(1, sampleId);
            ps.setInt(2, trayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            L.error("", e);
            throw new DataException(e);
        }

        // Place einfügen
        String sqlInsert = "INSERT INTO Place (TrayId, PlaceNo, SampleId) " +
                "SELECT ?, " +
                "(SELECT MIN(nr) FROM " +
                "  (SELECT LEVEL nr FROM DUAL CONNECT BY LEVEL <= (SELECT Capacity FROM Tray WHERE TrayId = ?)) " +
                "  WHERE nr NOT IN (SELECT PlaceNo FROM Place WHERE TrayId = ?)), " +
                "? FROM DUAL";
        L.info(sqlInsert);
        try (PreparedStatement ps = useConnection().prepareStatement(sqlInsert)) {
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

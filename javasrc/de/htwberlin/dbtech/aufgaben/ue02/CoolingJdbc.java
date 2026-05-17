package de.htwberlin.dbtech.aufgaben.ue02;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import de.htwberlin.dbtech.exceptions.CoolingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.htwberlin.dbtech.exceptions.DataException;

public class CoolingJdbc implements ICoolingJdbc {

    private static final Logger L = LoggerFactory.getLogger(CoolingJdbc.class);
    private Connection connection;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public List<String> getSampleKinds() {
        L.info("getSampleKinds: start");
        String sql = "SELECT Text FROM SampleKind ORDER BY Text";
        List<String> kinds = new ArrayList<>();

        try (PreparedStatement ps = useConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                kinds.add(rs.getString("Text"));
            }

        } catch (SQLException e) {
            throw new DataException("getSampleKinds failed", e);
        }

        L.info("getSampleKinds: found {} kinds", kinds.size());
        return kinds;
    }


    @Override
    public Sample findSampleById(Integer sampleId) {
        L.info("findSampleById: sampleId: {}", sampleId);
        String sql = "SELECT SampleID, SampleKindID, ExpirationDate FROM Sample WHERE SampleID = ?";

        try (PreparedStatement ps = useConnection().prepareStatement(sql)) {
            ps.setInt(1, sampleId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate("ExpirationDate");
                    LocalDate expirationDate = (d != null) ? d.toLocalDate() : null;
                    Sample sample = new Sample(
                            rs.getInt("SampleID"),
                            rs.getInt("SampleKindID"),
                            expirationDate
                    );
                    L.info("findSampleById: found {}", sample);
                    return sample;
                }
            }

        } catch (SQLException e) {
            throw new DataException("findSampleById failed", e);
        }

        throw new CoolingSystemException("Sample not found: " + sampleId);
    }


    @Override
    public void createSample(Integer sampleId, Integer sampleKindId) {
        L.info("createSample: sampleId: {}, sampleKindId: {}", sampleId, sampleKindId);

        // Prüfe ob SampleKindID existiert und hole ValidNoOfDays
        int validNoOfDays;
        String sqlKind = "SELECT ValidNoOfDays FROM SampleKind WHERE SampleKindID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlKind)) {
            ps.setInt(1, sampleKindId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CoolingSystemException("SampleKindId not found: " + sampleKindId);
                }
                validNoOfDays = rs.getInt("ValidNoOfDays");
            }
        } catch (SQLException e) {
            throw new DataException("createSample: SampleKind lookup failed", e);
        }

        // Prüfe ob SampleID bereits existiert
        String sqlCheck = "SELECT SampleID FROM Sample WHERE SampleID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlCheck)) {
            ps.setInt(1, sampleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new CoolingSystemException("SampleId already exists: " + sampleId);
                }
            }
        } catch (SQLException e) {
            throw new DataException("createSample: duplicate check failed", e);
        }

        // Einfügen mit berechnetem ExpirationDate
        LocalDate expirationDate = LocalDate.now().plusDays(validNoOfDays);
        String sqlInsert = "INSERT INTO Sample (SampleID, SampleKindID, ExpirationDate) VALUES (?, ?, ?)";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlInsert)) {
            ps.setInt(1, sampleId);
            ps.setInt(2, sampleKindId);
            ps.setDate(3, Date.valueOf(expirationDate));
            int rows = ps.executeUpdate();
            L.info("createSample: {} row(s) inserted, expirationDate: {}", rows, expirationDate);

        } catch (SQLException e) {
            throw new DataException("createSample: insert failed", e);
        }
    }

    @Override
    public void clearTray(Integer trayId) {
        L.info("clearTray: trayId: {}", trayId);

        // Prüfe ob TrayID existiert
        String sqlCheck = "SELECT TrayID FROM Tray WHERE TrayID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlCheck)) {
            ps.setInt(1, trayId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CoolingSystemException("TrayId not found: " + trayId);
                }
            }
        } catch (SQLException e) {
            throw new DataException("clearTray: Tray lookup failed", e);
        }

        // Schritt 1: SampleIDs merken
        List<Integer> sampleIds = new ArrayList<>();
        String sqlSelect = "SELECT SampleID FROM Place WHERE TrayID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlSelect)) {
            ps.setInt(1, trayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sampleIds.add(rs.getInt("SampleID"));
                }
            }
        } catch (SQLException e) {
            throw new DataException("clearTray: Place lookup failed", e);
        }

        if (sampleIds.isEmpty()) {
            L.info("clearTray: no samples in tray {}", trayId);
            return;
        }

        // Schritt 2: Place-Einträge löschen (FK-Constraint zuerst auflösen)
        String sqlDeletePlace = "DELETE FROM Place WHERE TrayID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlDeletePlace)) {
            ps.setInt(1, trayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("clearTray: Place delete failed", e);
        }

        // Schritt 3: Samples löschen
        String sqlDeleteSample = "DELETE FROM Sample WHERE SampleID = ?";
        try (PreparedStatement ps = useConnection().prepareStatement(sqlDeleteSample)) {
            for (Integer sid : sampleIds) {
                ps.setInt(1, sid);
                ps.addBatch();
            }
            ps.executeBatch();
            L.info("clearTray: {} sample(s) removed from tray {}", sampleIds.size(), trayId);
        } catch (SQLException e) {
            throw new DataException("clearTray: Sample delete failed", e);
        }
    }
}

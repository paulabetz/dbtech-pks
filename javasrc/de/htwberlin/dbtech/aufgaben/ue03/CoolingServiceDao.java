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

public class CoolingServiceDao implements ICoolingService {
        private static final Logger L = LoggerFactory.getLogger(CoolingServiceDao.class);
        private Connection connection;

        @Override
        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void transferSample(Integer sampleId, Integer diameterInCM) {
            L.info("transferSample: sampleId: " + sampleId + ", diameterInCM: " + diameterInCM);

            TrayFinder trayFinder = new TrayFinder();
            trayFinder.setConnection(connection);

            if (!trayFinder.existsSample(sampleId)) {
                throw new CoolingSystemException("sampleId existiert nicht in der Datenbank");
            }
            if (!trayFinder.existsFittingTray(diameterInCM)) {
                throw new CoolingSystemException("Kein passendes Tablett für die Größe gefunden");
            }
            if (!trayFinder.existsFreeTrayWithExpirationDate(sampleId, diameterInCM)
                    && !trayFinder.existsFreeTrayWithNullExpirationDate(diameterInCM)) {
                throw new CoolingSystemException("Alle passenden Tabletts sind voll");
            }

            Integer trayId = trayFinder.findSuitableTrayId(sampleId, diameterInCM);

            Place place = new Place();
            place.setConnection(connection);
            place.setTrayId(trayId);
            place.setSampleId(sampleId);
            place.updateTrayExpirationDate(sampleId);
            place.insert();
        }
    }


/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.persistence.jdbc.internal;

import java.sql.SQLInvalidAuthorizationSpecException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.knowm.yank.Yank;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.Item;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistenceItemInfo;
import org.openhab.core.types.State;
import org.openhab.persistence.jdbc.dto.ItemVO;
import org.openhab.persistence.jdbc.dto.ItemsVO;
import org.openhab.persistence.jdbc.dto.JdbcPersistenceItemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;

/**
 * Mapper class
 *
 * @author Helmut Lehmeyer - Initial contribution
 */
@NonNullByDefault
public class JdbcMapper {
    private final Logger logger = LoggerFactory.getLogger(JdbcMapper.class);

    private final TimeZoneProvider timeZoneProvider;

    // Error counter - used to reconnect to database on error
    protected int errCnt;
    protected boolean initialized = false;
    protected @NonNullByDefault({}) JdbcConfiguration conf;
    protected final Map<String, String> itemNameToTableNameMap = new HashMap<>();
    protected @NonNullByDefault({}) NamingStrategy namingStrategy;
    private long afterAccessMin = 10000;
    private long afterAccessMax = 0;

    public JdbcMapper(TimeZoneProvider timeZoneProvider) {
        this.timeZoneProvider = timeZoneProvider;
    }

    /*****************
     * MAPPER ITEMS *
     *****************/
    public boolean pingDB() {
        logger.debug("JDBC::pingDB");
        boolean ret = false;
        long timerStart = System.currentTimeMillis();
        if (openConnection()) {
            if (conf.getDbName() == null) {
                logger.debug(
                        "JDBC::pingDB asking db for name as absolutely first db action, after connection is established.");
                String dbName = conf.getDBDAO().doGetDB();
                if (dbName == null) {
                    ret = false;
                } else {
                    conf.setDbName(dbName);
                    ret = dbName.length() > 0;
                }
            } else {
                final @Nullable Integer result = conf.getDBDAO().doPingDB();
                ret = result != null && result > 0;
            }
        }
        logTime("pingDB", timerStart, System.currentTimeMillis());
        return ret;
    }

    public String getDB() {
        logger.debug("JDBC::getDB");
        long timerStart = System.currentTimeMillis();
        String res = conf.getDBDAO().doGetDB();
        logTime("getDB", timerStart, System.currentTimeMillis());
        return res != null ? res : "";
    }

    public boolean ifItemsTableExists() {
        logger.debug("JDBC::ifItemsTableExists");
        long timerStart = System.currentTimeMillis();
        boolean res = conf.getDBDAO().doIfTableExists(new ItemsVO());
        logTime("doIfTableExists", timerStart, System.currentTimeMillis());
        return res;
    }

    public ItemsVO createNewEntryInItemsTable(ItemsVO vo) {
        logger.debug("JDBC::createNewEntryInItemsTable");
        long timerStart = System.currentTimeMillis();
        Long i = conf.getDBDAO().doCreateNewEntryInItemsTable(vo);
        vo.setItemId(i.intValue());
        logTime("doCreateNewEntryInItemsTable", timerStart, System.currentTimeMillis());
        return vo;
    }

    public boolean createItemsTableIfNot(ItemsVO vo) {
        logger.debug("JDBC::createItemsTableIfNot");
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doCreateItemsTableIfNot(vo);
        logTime("doCreateItemsTableIfNot", timerStart, System.currentTimeMillis());
        return true;
    }

    public boolean dropItemsTableIfExists(ItemsVO vo) {
        logger.debug("JDBC::dropItemsTableIfExists");
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doDropItemsTableIfExists(vo);
        logTime("doDropItemsTableIfExists", timerStart, System.currentTimeMillis());
        return true;
    }

    public ItemsVO deleteItemsEntry(ItemsVO vo) {
        logger.debug("JDBC::deleteItemsEntry");
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doDeleteItemsEntry(vo);
        logTime("deleteItemsEntry", timerStart, System.currentTimeMillis());
        return vo;
    }

    public List<ItemsVO> getItemIDTableNames() {
        logger.debug("JDBC::getItemIDTableNames");
        long timerStart = System.currentTimeMillis();
        List<ItemsVO> vo = conf.getDBDAO().doGetItemIDTableNames(new ItemsVO());
        logTime("getItemIDTableNames", timerStart, System.currentTimeMillis());
        return vo;
    }

    public List<ItemsVO> getItemTables() {
        logger.debug("JDBC::getItemTables");
        long timerStart = System.currentTimeMillis();
        ItemsVO vo = new ItemsVO();
        vo.setJdbcUriDatabaseName(conf.getDbName());
        List<ItemsVO> vol = conf.getDBDAO().doGetItemTables(vo);
        logTime("getItemTables", timerStart, System.currentTimeMillis());
        return vol;
    }

    /****************
     * MAPPERS ITEM *
     ****************/
    public void updateItemTableNames(List<ItemVO> vol) {
        logger.debug("JDBC::updateItemTableNames");
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doUpdateItemTableNames(vol);
        logTime("updateItemTableNames", timerStart, System.currentTimeMillis());
    }

    public ItemVO createItemTable(ItemVO vo) {
        logger.debug("JDBC::createItemTable");
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doCreateItemTable(vo);
        logTime("createItemTable", timerStart, System.currentTimeMillis());
        return vo;
    }

    public Item storeItemValue(Item item, State itemState, @Nullable ZonedDateTime date) {
        logger.debug("JDBC::storeItemValue: item={} state={} date={}", item, itemState, date);
        String tableName = getTable(item);
        long timerStart = System.currentTimeMillis();
        if (date == null) {
            conf.getDBDAO().doStoreItemValue(item, itemState, new ItemVO(tableName, null));
        } else {
            conf.getDBDAO().doStoreItemValue(item, itemState, new ItemVO(tableName, null), date);
        }
        logTime("storeItemValue", timerStart, System.currentTimeMillis());
        errCnt = 0;
        return item;
    }

    public List<HistoricItem> getHistItemFilterQuery(FilterCriteria filter, int numberDecimalcount, String table,
            Item item) {
        logger.debug(
                "JDBC::getHistItemFilterQuery filter='{}' numberDecimalcount='{}' table='{}' item='{}' itemName='{}'",
                true, numberDecimalcount, table, item, item.getName());
        long timerStart = System.currentTimeMillis();
        List<HistoricItem> result = conf.getDBDAO().doGetHistItemFilterQuery(item, filter, numberDecimalcount, table,
                item.getName(), timeZoneProvider.getTimeZone());
        logTime("getHistItemFilterQuery", timerStart, System.currentTimeMillis());
        errCnt = 0;
        return result;
    }

    public boolean deleteItemValues(FilterCriteria filter, String table) {
        logger.debug("JDBC::deleteItemValues filter='{}' table='{}' itemName='{}'", true, table, filter.getItemName());
        long timerStart = System.currentTimeMillis();
        conf.getDBDAO().doDeleteItemValues(filter, table, timeZoneProvider.getTimeZone());
        logTime("deleteItemValues", timerStart, System.currentTimeMillis());
        errCnt = 0;
        return true;
    }

    /***********************
     * DATABASE CONNECTION *
     ***********************/
    protected boolean openConnection() {
        logger.debug("JDBC::openConnection isDriverAvailable: {}", conf.isDriverAvailable());
        if (conf.isDriverAvailable() && !conf.isDbConnected()) {
            logger.info("JDBC::openConnection: Driver is available::Yank setupDataSource");
            try {
                Yank.setupDefaultConnectionPool(conf.getHikariConfiguration());
                conf.setDbConnected(true);
                return true;
            } catch (PoolInitializationException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SQLInvalidAuthorizationSpecException) {
                    logger.warn("JDBC::openConnection: failed to open connection: {}", cause.getMessage());
                } else {
                    logger.warn("JDBC::openConnection: failed to open connection: {}", e.getMessage());
                }
                initialized = false;
                return false;
            }
        } else if (!conf.isDriverAvailable()) {
            logger.warn("JDBC::openConnection: no driver available!");
            initialized = false;
            return false;
        }
        return true;
    }

    protected void closeConnection() {
        logger.debug("JDBC::closeConnection");
        // Closes all open connection pools
        Yank.releaseDefaultConnectionPool();
        conf.setDbConnected(false);
    }

    protected boolean checkDBAccessability() {
        // Check if connection is valid
        if (initialized) {
            return true;
        }
        // first
        boolean p = pingDB();
        if (p) {
            logger.debug("JDBC::checkDBAcessability, first try connection: {}", p);
            return (p && !(conf.getErrReconnectThreshold() > 0 && errCnt <= conf.getErrReconnectThreshold()));
        } else {
            // second
            p = pingDB();
            logger.debug("JDBC::checkDBAcessability, second try connection: {}", p);
            return (p && !(conf.getErrReconnectThreshold() > 0 && errCnt <= conf.getErrReconnectThreshold()));
        }
    }

    /**************************
     * DATABASE TABLEHANDLING *
     **************************/
    protected void checkDBSchema() {
        if (!conf.getTableUseRealCaseSensitiveItemNames()) {
            createItemsTableIfNot(new ItemsVO());
        }
        if (conf.getRebuildTableNames()) {
            formatTableNames();

            if (conf.getTableUseRealCaseSensitiveItemNames()) {
                dropItemsTableIfExists(new ItemsVO());
            }
            logger.info(
                    "JDBC::checkDBSchema: Rebuild complete, configure the 'rebuildTableNames' setting to 'false' to stop rebuilds on startup");
            // Reset the error counter
            errCnt = 0;
        }
        populateItemNameToTableNameMap();
    }

    private void populateItemNameToTableNameMap() {
        itemNameToTableNameMap.clear();
        if (conf.getTableUseRealCaseSensitiveItemNames()) {
            for (String itemName : getItemTables().stream().map(t -> t.getTableName()).collect(Collectors.toList())) {
                itemNameToTableNameMap.put(itemName, itemName);
            }
        } else {
            for (ItemsVO vo : getItemIDTableNames()) {
                itemNameToTableNameMap.put(vo.getItemName(),
                        namingStrategy.getTableName(vo.getItemId(), vo.getItemName()));
            }
        }
    }

    protected String getTable(Item item) {
        int itemId = 0;
        ItemsVO isvo;
        ItemVO ivo;

        String itemName = item.getName();
        String tableName = itemNameToTableNameMap.get(itemName);

        // Table already exists - return the name
        if (!Objects.isNull(tableName)) {
            return tableName;
        }

        logger.debug("JDBC::getTable: no table found for item '{}' in sqlTables", itemName);

        if (!conf.getTableUseRealCaseSensitiveItemNames()) {
            // Create a new entry in items table
            isvo = new ItemsVO();
            isvo.setItemName(itemName);
            isvo = createNewEntryInItemsTable(isvo);
            itemId = isvo.getItemId();
            if (itemId == 0) {
                logger.error("JDBC::getTable: Creating items entry for item '{}' failed.", itemName);
            }
        }

        // Create the table name
        logger.debug("JDBC::getTable: getTableName with rowId={} itemName={}", itemId, itemName);
        tableName = namingStrategy.getTableName(itemId, itemName);

        // Create table for item
        String dataType = conf.getDBDAO().getDataType(item);
        ivo = new ItemVO(tableName, itemName);
        ivo.setDbType(dataType);
        ivo = createItemTable(ivo);
        logger.debug("JDBC::getTable: Table created for item '{}' with dataType {} in SQL database.", itemName,
                dataType);

        itemNameToTableNameMap.put(itemName, tableName);

        return tableName;
    }

    private void formatTableNames() {
        boolean tmpinit = initialized;
        if (tmpinit) {
            initialized = false;
        }

        List<ItemsVO> itemIdTableNames = ifItemsTableExists() ? getItemIDTableNames() : new ArrayList<ItemsVO>();
        List<String> itemTables = getItemTables().stream().map(t -> t.getTableName()).collect(Collectors.toList());
        List<ItemVO> oldNewTableNames;

        if (itemIdTableNames.isEmpty()) {
            // Without mappings we can only migrate from direct item name to numeric mapping.
            if (conf.getTableUseRealCaseSensitiveItemNames()) {
                logger.info("JDBC::formatTableNames: Nothing to migrate.");
                initialized = tmpinit;
                return;
            }
            oldNewTableNames = new ArrayList<>();
            for (String itemName : itemTables) {
                ItemsVO isvo = new ItemsVO();
                isvo.setItemName(itemName);
                isvo = createNewEntryInItemsTable(isvo);
                int itemId = isvo.getItemId();
                if (itemId == 0) {
                    logger.error("JDBC::formatTableNames: Creating items entry for item '{}' failed.", itemName);
                } else {
                    String newTableName = namingStrategy.getTableName(itemId, itemName);
                    oldNewTableNames.add(new ItemVO(itemName, newTableName));
                    logger.info("JDBC::formatTableNames: Table '{}' will be renamed to '{}'", itemName, newTableName);
                }
            }
        } else {
            String itemsManageTable = new ItemsVO().getItemsManageTable();
            Map<Integer, String> itemIdToItemNameMap = new HashMap<>();

            for (ItemsVO vo : itemIdTableNames) {
                int itemId = vo.getItemId();
                String itemName = vo.getItemName();
                itemIdToItemNameMap.put(itemId, itemName);
            }

            oldNewTableNames = namingStrategy.prepareMigration(itemTables, itemIdToItemNameMap, itemsManageTable);
        }

        updateItemTableNames(oldNewTableNames);
        logger.info("JDBC::formatTableNames: Finished updating {} item table names", oldNewTableNames.size());

        initialized = tmpinit;
    }

    public Set<PersistenceItemInfo> getItems() {
        // TODO: in general it would be possible to query the count, earliest and latest values for each item too but it
        // would be a very costly operation
        return itemNameToTableNameMap.keySet().stream().map(itemName -> new JdbcPersistenceItemInfo(itemName))
                .collect(Collectors.<PersistenceItemInfo> toSet());
    }

    /*****************
     * H E L P E R S *
     *****************/
    private void logTime(String me, long timerStart, long timerStop) {
        if (conf.enableLogTime && logger.isInfoEnabled()) {
            conf.timerCount++;
            int timerDiff = (int) (timerStop - timerStart);
            if (timerDiff < afterAccessMin) {
                afterAccessMin = timerDiff;
            }
            if (timerDiff > afterAccessMax) {
                afterAccessMax = timerDiff;
            }
            conf.timeAverage50arr.add(timerDiff);
            conf.timeAverage100arr.add(timerDiff);
            conf.timeAverage200arr.add(timerDiff);
            if (conf.timerCount == 1) {
                conf.timer1000 = System.currentTimeMillis();
            }
            if (conf.timerCount == 1001) {
                conf.time1000Statements = Math.round(((int) (System.currentTimeMillis() - conf.timer1000)) / 1000);// Seconds
                conf.timerCount = 0;
            }
            logger.info(
                    "JDBC::logTime: '{}':\n afterAccess     = {} ms\n timeAverage50  = {} ms\n timeAverage100 = {} ms\n timeAverage200 = {} ms\n afterAccessMin  = {} ms\n afterAccessMax  = {} ms\n 1000Statements = {} sec\n statementCount = {}\n",
                    me, timerDiff, conf.timeAverage50arr.getAverageInteger(),
                    conf.timeAverage100arr.getAverageInteger(), conf.timeAverage200arr.getAverageInteger(),
                    afterAccessMin, afterAccessMax, conf.time1000Statements, conf.timerCount);
        }
    }
}

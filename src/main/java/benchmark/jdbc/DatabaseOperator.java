package benchmark.jdbc;

import benchmark.Constants;
import benchmark.database.DatabaseInfo;
import com.sun.tools.internal.jxc.ap.Const;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


// TODO: Add necessary connection closing
public class DatabaseOperator {

    private final DatabaseInfo databaseInfo;
    private Connection connection;

    private static List<String> processingTableColumnNames = new ArrayList<String>() {{
        add(Constants.KEY_COLUMN_NAME);
        add(Constants.VALUE_COLUMN_NAME);
    }};

    private boolean hasCreatedCustomDatabase = false;

    // MARK: - Constructor
    public DatabaseOperator(DatabaseInfo databaseInfo) {
        this.databaseInfo = databaseInfo;
        final String SCHEMA_MOCK = "public";
        final String TABLE_NAME = this.databaseInfo.getTargetTable();
        System.out.println("Table name: " + TABLE_NAME);
    }

    public void establishConnection() throws SQLException, JdbcCrudFailureException {

        final String POSTGRES_CLASS_MOCK =  "org.postgresql.Driver";
        // TODO: Replace Postgres to some dynamic class
        try {
            Class.forName( POSTGRES_CLASS_MOCK );
        } catch (ClassNotFoundException error) {
            System.out.println("POSTGRESQL Driver hasn't been found");
        }
        final String databaseURL = databaseInfo.getDatabaseURL();
        System.out.println("Connecting to database: " + databaseURL);
        final String databaseUsername = databaseInfo.getUsername();
        final String databaseUserPassword = databaseInfo.getPassword();
        this.connection = DriverManager.getConnection(databaseURL, databaseUsername, databaseUserPassword);


        try {
            this.createMissingDatabaseElements();

        } catch (JdbcCrudFailureException error) {
            System.err.println("Unable to create required DB element, reason: " + error.getMessage());
            System.exit(Constants.CONNECTION_ERROR);
        }


    }

    private void createMissingDatabaseElements() throws JdbcCrudFailureException {
        final String targetDatabase = this.databaseInfo.getTargetDatabaseName();
        try {
            this.createDatabase(targetDatabase);
        } catch (SQLException error) {
            // NOTE: I check if DB exist this way since PostgreSQL returns an empty list of catalogs, ...
            // ... yet still throwing errors when I'm trying to create a DB with an existing name.
            this.hasCreatedCustomDatabase = false;
            System.out.println("Database " + targetDatabase + " exists. Using it for benchmark.");
        }

        final String targetTableName = this.databaseInfo.getTargetTable();
        try {
            this.createTable(targetTableName);
        } catch (SQLException error) {
            // NOTE: I check if DB exist this way since PostgreSQL returns an empty list of catalogs, ...
            // ... yet still throwing errors when I'm trying to create a DB with an existing name.
            this.hasCreatedCustomDatabase = false;
            System.out.println("Table " + targetTableName + " exists. Using it for benchmark.");
        }


        final String varcharType = "VARCHAR(10)";
        String processingColumnName = "";
        try {
            for (String columnName : DatabaseOperator.processingTableColumnNames) {
                processingColumnName = columnName;
                this.createColumn(columnName, varcharType);
            }
        } catch (SQLException error) {
            System.out.println("Column " + processingColumnName + " exists. Using it for benchmark.");
        }

    }


    private boolean isColumnExistInTable(final String table, final String column) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        ResultSet rs = md.getColumns(null, null, table, column);
        if (rs.next()) {
            return true;
        }
        return false;
    }
    // NOTE: Deleting temprorary created databases and tables if needed.
    public void shutDownConnection() throws SQLException {
        if (this.connection == null) {
            return;
        }

        try {

            // NOTE: Deletion of created database
            if (this.hasCreatedCustomDatabase) {
                this.dropDatabase(this.databaseInfo.getTargetDatabaseName());
            }

            // TODO: Add deletion of created tables

            for (String createdColumn: DatabaseOperator.processingTableColumnNames) {
                this.dropColumnWithinTable(this.databaseInfo.getTargetTable(), createdColumn);
            }

            this.connection.close();

        } catch (JdbcCrudFailureException deleteError) {
            System.err.println("An error has occured while deleting: " + deleteError.getMessage());
        }
    }

    private void dropDatabase(String name) throws SQLException, JdbcCrudFailureException {
        final String dropDatabaseSqlQuery = "DROP DATABASE " + name;
        Statement statement = this.connection.createStatement();
        final int amountOfSuccessOperations = statement.executeUpdate(dropDatabaseSqlQuery);
        if (!this.isStatementExcecutionCorrect(amountOfSuccessOperations)) {
            throw new JdbcCrudFailureException("Database " + name + " couldn't be dropped.", CrudOperationType.DELETE);
        }
    }

    private void dropColumnWithinTable(String table, String column) throws SQLException {
        final String dropColumnSqlQuery = String.format("ALTER TABLE \"%s\" DROP %s;", table, column);
        try (PreparedStatement preparedStatement = this.connection.prepareStatement(dropColumnSqlQuery)) {
            preparedStatement.executeUpdate();
        }
    }

    private void createDatabase(String name) throws SQLException, JdbcCrudFailureException {
        System.err.println("CREATING DATABASE.....");
        if (!this.isDatabaseElementNameValid(name)) {
            final String nameFormatMisleadingMsg = "Name should contain only latin letters, numbers and an underscore.";
            throw new JdbcCrudFailureException("\"" + name + "\" is not a valid database name." + nameFormatMisleadingMsg, CrudOperationType.CREATE);
        }
        try(Statement statement = this.connection.createStatement()) {
            final String createDatabaseSqlQuery = "CREATE DATABASE " + name + "name";
            statement.executeUpdate(createDatabaseSqlQuery);

        }
        this.hasCreatedCustomDatabase = true;
    }

    private boolean isDatabaseElementNameValid(String name) {
        final String avaliableCharactersInNameRegEx = "^[a-zA-Z_][a-zA-Z0-9_]*$";
        return name.matches(avaliableCharactersInNameRegEx);
    }


    // NOTE: This method doesn't work with PostgreSQL: it returns an empty list of catalogs, ...
    // ... but throwing errors when I try to create DB with an existing name
    private boolean isDatabaseExistInServer(String databaseName) throws SQLException {
        if (this.connection == null) {
            System.err.println("Unable to fetch catalog: connection hasn't been established.");
            return false;
        }
        boolean isDatabaseExist = false;

        try(ResultSet resultSet = this.connection.getMetaData().getCatalogs()) {
            while (resultSet.next()) {
                final int databaseNameIndex = 1;
                final String processingDatabaseName = resultSet.getString(databaseNameIndex);
                isDatabaseExist = (processingDatabaseName.equals(databaseName));
            }
        }
        return isDatabaseExist;
    }


    // TODO: Move it onto a separate object may be?
    private List<String> getColumnNames (String tableName, String schemaName) throws SQLException {

        ResultSet resultSet = null;

        ResultSetMetaData resultSetMetaData = null;
        PreparedStatement preparedStatement = null;
        List<String> columnNames = new ArrayList<String>();
        String qualifiedSchemaName = this.databaseInfo.getTargetDatabaseName(); // TEST: this.getQualifiedSchemaName(schemaName, tableName);
        try {
            preparedStatement = this.connection.prepareStatement("select * from " + qualifiedSchemaName + " where 0=1");
            //NOTE: we're getting empty result set, yet meta data would still be avaliable
            resultSet = preparedStatement.executeQuery();
            resultSetMetaData = resultSet.getMetaData();
            for(int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                columnNames.add(resultSetMetaData.getColumnLabel(i));
            }
        } catch(SQLException error) {
            final String misleadingMsg = "An error has occured while fetching metadata from " + tableName + ". Reason: " + error.getMessage();
            throw new SQLException(misleadingMsg);
        }
        finally {
            if (resultSet != null)
                try {
                    resultSet.close();
                } catch (SQLException error) {
                    throw error;
                }
            if (preparedStatement != null)
                try {
                    preparedStatement.close();
                } catch (SQLException error) {
                    throw error;
                }
        }
        return columnNames;
    }

    private final String getQualifiedSchemaName(final String targetSchema, final String targetTable) {
        return (targetSchema!=null && !targetSchema.isEmpty()) ? (targetSchema + "." + targetTable) : targetTable;
    }



    public void createTable(final String name) throws SQLException {
        String sqlCreate = "CREATE TABLE IF NOT EXISTS \"" + name + "\""
                + "  (key           VARCHAR(10),"
                + "   value            VARCHAR(10));";

        try (Statement statement = this.connection.createStatement()) {
            statement.execute(sqlCreate);
        }
    }


    private void createColumn(final String name, final String type) throws JdbcCrudFailureException, SQLException {
        if (this.isColumnExistInTable(this.databaseInfo.getTargetTable(), name)) {
            return;
        }
        if (!this.isDatabaseElementNameValid(name)) {
            throw new JdbcCrudFailureException("\"" + name + "\" is not a valid column name.", CrudOperationType.CREATE);
        }

        final String addColumnSqlQuery = String.format("ALTER TABLE \"%s\" ADD %s %s;", this.databaseInfo.getTargetTable(), name, type);
        try (PreparedStatement preparedStatement = this.connection.prepareStatement(addColumnSqlQuery)) {
            preparedStatement.executeUpdate();
        }
    }




    public void insertKeyValueTest(Map.Entry<String, String> value) throws SQLException, IllegalArgumentException  {
        if (this.connection == null) {
            final String misleadingMsg = "Connection to required database hasn't been established.";
            throw new IllegalArgumentException(misleadingMsg);
        }
        final String columnName = value.getKey();
        final String columnValue = value.getValue();
        this.insertValueIntoColumn(columnName, columnValue);
    }

    private void insertValueIntoColumn(final String column, String value) throws SQLException, IllegalArgumentException {

        if (!isColumnExistInCurrentDB(column)) {
            final String misleadingMsg = "Column " + column + " doesn't exist in database " + this.databaseInfo.getTargetDatabaseName();
            throw new IllegalArgumentException(misleadingMsg);
        }

        if (value.isEmpty()) {
            // NOTE: SQL doesn't allow to inster empty strings, so I'm adding an empty value.
            final String minimalStringAllowed = " ";
            value = minimalStringAllowed;
        }
        
        String insertSqlQuery = String.format("INSERT INTO \"%s\" (%s) VALUES ('%s');", this.databaseInfo.getTargetTable(), column, value); //"INSERT INTO " + targetTable + "(" + column + ") VALUES ('" + value + "')";
        try (PreparedStatement preparedStatement = this.connection.prepareStatement(insertSqlQuery)) {
            // NOTE: (JavaDoc) either (1) the row count for SQL Data Manipulation Language (DML) statements or ...
            // ... (2) 0 for SQL statements that return nothing.
            preparedStatement.executeUpdate();
        }

    }

    private boolean isStatementExcecutionCorrect(int amountOfOperations) {
        return (amountOfOperations > 0);
    }

    private Boolean isColumnExistInCurrentDB(final String column) {
        // TODO: Impliment method
        return this.processingTableColumnNames.contains(column);
    }

}

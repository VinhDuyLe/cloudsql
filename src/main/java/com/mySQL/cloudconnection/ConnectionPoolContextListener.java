package com.mySQL.cloudconnection;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


@SuppressFBWarnings(
    value = {"HARD_CODE_PASSWORD", "WEM_WEAK_EXCEPTION_MESSAGING"},
    justification = "Extracted from enviroment, Exception message for later use."
)

@WebListener("Create a connection pool that is stored in the Servlet's context for later use.")

public class ConnectionPoolContextListener implements ServletContextListener {
    private static final String INSTANCE_CONNECTION_NAME = System.getenv("INSTANCE_CONNECTION_NAME");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASS = System.getenv("DB_PASS");
    private static final String DB_NAME = System.getenv("DB_NAME");


    private DataSource createConnectionPool() {
        //[START cloud_sql_mysql_servlet_create]
        //for Java users, Cloud SQL JDBC Socket Company can provide authenticated connections
        //which is preferred to using the Cloud SQL Proxy with Unix sockets.

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql:///%s", DB_NAME));
        config.setUsername("DB_USER"); //"root", "mysql"
        config.setPassword("DB_PASS"); //"my-password"

        config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory");
        config.addDataSourceProperty("cloudSqlInstance", INSTANCE_CONNECTION_NAME);

        //the argument ipTypes=PRIVATE will force the SocketFactory to connect with an instance's associated private IP
        config.addDataSourceProperty("ipTypes", "PUBLIC,PRIVATE" );

        // Specify addtional connection properties here
        // [START_EXCLUDE]

        // [START cloud_sql_mysql_servlet_limit]
        // maximumPoolSize limits the total number of concurrent connections this pool will keep.
        config.setMaximumPoolSize(5);
        // minimumIdle is the minimum number of idle connections Hikari maintains in the pool
        config.setMinimumIdle(5);
        // [END cloud_sql_mysql_servlet_limit]

        // [START cloud_sql_mysql_servlet_timeout]
        // setConnectionTimeout is the maximum number of milliseconds to wait for a connection checkout.
        // Any attempt to retrieve connection exceeding the set limit will throw SQLException
        config.setConnectionTimeout(10000); //10 seconds
        // idleTimeout is the maximum amount of time a connection can sit in the pool
        config.setIdleTimeout(600000); //10 minutes
        // [END cloud_sql_mysql_servlet_timeout]

        // [START cloud_sql_mysql_servlet_backoff]
        // Hikari auto delays btw failed connection attempts, then reach max delay of connectionTimeout/2 btw attempts
        // [END cloud_sql_mysql_servlet_backoff]

        // [START cloud_sql_mysql_servlet_lifetime]
        // maxLifetime is the max possible lifetime of a connection in the pool
        config.setMaxLifetime(180000); //30 minutes
        // [END cloud_sql_mysql_servlet_lifetime]

        // [END_EXCLUDE]

        // Initialize the connection pool using the configuration object
        DataSource pool = new HikariDataSource(config);
        // [END cloud_sql_mysql_servlet_create]
        return pool;
    }

    private void createTable(DataSource pool) throws SQLException {
        // Safely attempt to create the table schema.
        try (Connection conn = pool.getConnection()) {
            String stmt =
                    "CREATE TABLE IF NOT EXISTS votes ( "
                    + "vote_id SERIAL NOT NULL, time_cast timestamp NOT NULL, candidate CHAR(6) NOT NULL,"
                    + " PRIMARY KEY (vote_id) );";
            try (PreparedStatement createTableStatement = conn.prepareStatement(stmt);) {
                createTableStatement.execute();
            }
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        // This function is called when the Servlet is destroyed.
        HikariDataSource pool = (HikariDataSource) event.getServletContext().getAttribute("my-pool");
        if (pool != null) {
            pool.close();
        }
    }

    public void contextInitialized(ServletContextEvent event) {
        // This function is called when the application starts and will safely create a connection pool
        // that can be used to connect to.
        ServletContext servletContext = event.getServletContext();
        DataSource pool = (DataSource) servletContext.getAttribute("my-pool");
        if (pool == null) {
            pool = createConnectionPool();
            servletContext.setAttribute("my-pool", pool);
        }
        try {
            createTable(pool);
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Unable to verify table schema. Please double check the steps"
                    + "in the README and try again.", ex);
        }
    }
}

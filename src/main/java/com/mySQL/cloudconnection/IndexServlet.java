package com.mySQL.cloudconnection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressFBWarnings(
        value = {"SE_NO_SERIALVERSIONID", "WEM_WEAK_EXCEPTION_MESSAGING"},
         justification = "Not needed for IndexServlet, Exception adds context"
)
@WebServlet(name = "Index", value ="")
public class IndexServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(IndexServlet.class.getName());

    class TemplateData {
        public int tabCount;
        public int spaceCount;
        public List<Vote> recentVotes;

        public TemplateData(int tabCount, int spaceCount, List<Vote> recentVotes) {
            this.tabCount = tabCount;
            this.spaceCount = spaceCount;
            this.recentVotes = recentVotes;
        }
    }

    public TemplateData getTemplateData(DataSource pool) throws ServletException {
        int tabCount = 0;
        int spaceCount = 0;
        List<Vote> recentVotes = new ArrayList<>();
        try (Connection conn = pool.getConnection()) {
            // PreparedStatements are compiled by the database immidiately and executed at a later date.
            // Most databases cache previously compiled queries, which improves efficienc.

            String stmt1 = "SELECT candidate, time_cast FROM votes ORDER BY time_cast DESC LIMIT 5";
            try (PreparedStatement voteStmt = conn.prepareStatement(stmt1);) {
                // Execute stmt;
                ResultSet voteResults = voteStmt.executeQuery();
                // Convert a ResultSet into Vote objects
                while (voteResults.next()) {
                    String candidate = voteResults.getString(1);
                    Timestamp timeCast = voteResults.getTimestamp(2);
                    recentVotes.add(new Vote(candidate,timeCast));
                }
            }

            // PreparedStatments can be executed multi times with diff arguments.
            // This improve efficiency, and project a query from being vulnerable to an SQL injection
            String stm2 = "SELECT COUNT(vote_id) FROM votes WHERE candidate=?";
            try (PreparedStatement voteCountStmt = conn.prepareStatement(stm2);) {
                voteCountStmt.setString(1, "TABS");
                ResultSet tabResult = voteCountStmt.executeQuery();
                if (tabResult.next()) {
                    // Move to the first result
                    tabCount = tabResult.getInt(1);
                }

                voteCountStmt.setString(1, "SPACES");
                ResultSet spaceResult = voteCountStmt.executeQuery();
                if (spaceResult.next()) {
                    // Move to first result
                    spaceCount = spaceResult.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new ServletException(
                    "Unable to successfully connect to the database. Please check the "
                            + "steps in the README and try again.", ex
            );
        }
        TemplateData templateData = new TemplateData(tabCount, spaceCount, recentVotes);
        return templateData;
    }
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws IOException, ServletException {
        // Extract the pool from Servlet Context, reusing the one that was created
        // in the ContextListener when the application was started
        DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");

        TemplateData templateData = getTemplateData(pool);

        // Add variables and render the page
        req.setAttribute("tabCount", templateData.tabCount);
        req.setAttribute("spaceCount", templateData.spaceCount);
        req.setAttribute("recentVotes", templateData.recentVotes);
        req.getRequestDispatcher("/index.jsp").forward(req, resp);
    }
    // Used to validate user input. All user provided data should be validated and sanitized before
    // being used something like a SQL query. Returns null if invalid.
    @Nullable
    private String validateTeam(String input) {
        if ( input != null) {
            input = input.toUpperCase(Locale.ENGLISH);
            // Must be either "TABS" or "SPACES"
            if (!"TABS".equals(input) && !"SPACES".equals(input)) return null;
        }
        return input;
    }

    @SuppressFBWarnings(
            value = {"SERVLET_PARAMETER", "XSS_SERVLET"},
            justification = "Input is validated and sanitized."
    )
    public void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
        // Get team from request and record the time of vote
        String team = validateTeam(req.getParameter("team"));
        Timestamp now = new Timestamp(new Date().getTime());
        if (team == null) {
            resp.setStatus(400);
            resp.getWriter().append("Invalid team specified.");
            return;
        }

        // Reuse the pool that was create in the ContextListener when Servlet started.
        DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");
        // [START cloud_sql_mysql_servlet_connection]
        // using try-with-resources stmt resures that conenction is always released back
        // into the pool at the end of stmt (even error occur)
        try (Connection conn = pool.getConnection()) {
            // PreparedStatements can be more efficient & project against injection
            String stmt = "INSERT INTO votes (time_cast, candidate) VALUES (?,?);";
            try (PreparedStatement voteStmt = conn.prepareStatement(stmt);) {
                voteStmt.setTimestamp(1, now);
                voteStmt.setString(2, team);

                // Finally, execute the stmt. If it fails, an error will be thrown
                voteStmt.execute();
            }
        } catch (SQLException ex) {
            // sth wrong, handle error: adjusting parameters
            // [START_EXCLUDE]
            LOGGER.log(Level.WARNING, "Error while attempting to submit vote.", ex);
            resp.setStatus(500);
            resp.getWriter().write(
                    "Unable to sucessfully cast vote! Please check the application"
                    + "logs for more details."
            );
            // [END_EXCLUDE]
        }
        // [END cloud_sql_mysql_servlet_connection]
        resp.setStatus(200);
        resp.getWriter().printf("Vote successfully cast for '%s' at time %s!%n", team, now);
    }

}

package com.mySQL.cloudconnection;

import java.sql.Timestamp;
import java.util.Locale;

public class Vote {
    private String candidate;
    private Timestamp timeCast;

    public Vote(String candidate, Timestamp timeCast) {
        this.candidate = candidate.toUpperCase(Locale.ENGLISH);
        this.timeCast = new Timestamp(timeCast.getTime());
    }

    public String getCandidate() {
        return candidate;
    }

    public void setCandidate(String candidate) {
        this.candidate = candidate.toUpperCase(Locale.ENGLISH);
    }

    public Timestamp getTimeCast() {
        return new Timestamp(timeCast.getTime());
    }

    public void setTimeCast(Timestamp timeCast) {
        this.timeCast = new Timestamp(timeCast.getTime());
    }

    public String toString() {
        return String.format("Vote(candidate=%s, timeCast=%s)", this.candidate, this.timeCast);
    }
}

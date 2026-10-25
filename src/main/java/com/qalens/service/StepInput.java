package com.qalens.service;

/** One editor row as bound from the form. A plain bean because Spring fills list elements through setters. */
public class StepInput {

    private String action = "";
    private String expected = "";

    public StepInput() {
    }

    public StepInput(String action, String expected) {
        this.action = action;
        this.expected = expected;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }
}

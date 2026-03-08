package com.anshali.calculator;

public class HistoryItem {
    private String expression;
    private String result;

    public HistoryItem(String expression, String result) {
        this.expression = expression;
        this.result = result;
    }

    public String getExpression() {
        return expression;
    }

    public String getResult() {
        return result;
    }
}

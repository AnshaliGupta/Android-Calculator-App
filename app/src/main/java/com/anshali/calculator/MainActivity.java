package com.anshali.calculator;

import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "CalculatorPrefs";
    private static final String KEY_HISTORY = "history";
    
    private EditText mainEntryText;
    private TextView answerText;
    private LinearLayout historyLayout;
    private RecyclerView historyRecycler;
    private HistoryAdapter historyAdapter;
    private List<HistoryItem> historyList;
    private FrameLayout frameLayout;

    private boolean isHistoryVisible = false;
    private boolean lastNumeric = false;
    private boolean stateError = false;
    private boolean lastDot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameLayout = findViewById(R.id.frame_layout);
        mainEntryText = findViewById(R.id.main_entry_text);

        mainEntryText.setShowSoftInputOnFocus(false);
        mainEntryText.setVerticalScrollBarEnabled(true);
        mainEntryText.setHorizontallyScrolling(false);
        mainEntryText.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mainEntryText.setScrollbarFadingEnabled(false);

        mainEntryText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();

                if (length < 12) {
                    mainEntryText.setTextSize(45);
                }
                else if (length < 22) {
                    mainEntryText.setTextSize(36);
                }
                else if (length < 32) {
                    mainEntryText.setTextSize(28);
                }
                else {
                    mainEntryText.setTextSize(22);
                }

                mainEntryText.setSelection(mainEntryText.getText().length());
                scrollToCursor();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        //mainEntryText.setOnClickListener(v ->
        //       mainEntryText.setSelection(mainEntryText.getText().length()));

        answerText = findViewById(R.id.answer_text);
        historyLayout = findViewById(R.id.history_layout);
        historyRecycler = findViewById(R.id.history_recycler);

        loadHistory();
        historyAdapter = new HistoryAdapter(historyList);
        historyRecycler.setLayoutManager(new LinearLayoutManager(this));
        historyRecycler.setHasFixedSize(true);
        historyRecycler.setAdapter(historyAdapter);
        historyRecycler.setNestedScrollingEnabled(true);

        setNumericClickListeners();
        setOperatorClickListeners();

        findViewById(R.id.clear_button).setOnClickListener(v -> {
            mainEntryText.setText("");
            answerText.setText("");
            lastNumeric = false;
            stateError = false;
            lastDot = false;
        });

        findViewById(R.id.del_button).setOnClickListener(v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            int start = mainEntryText.getSelectionStart();
            int end = mainEntryText.getSelectionEnd();
            if (start != end) {
                mainEntryText.getText().delete(Math.min(start, end), Math.max(start, end));
            } else if (start > 0) {
                mainEntryText.getText().delete(start - 1, start);
            }
            scrollToCursor();
            
            try {
                String newText = mainEntryText.getText().toString();
                if (!newText.isEmpty()) {
                    char lastChar = newText.charAt(newText.length() - 1);
                    lastNumeric = Character.isDigit(lastChar) || lastChar == ')';
                    onCalculate();
                } else {
                    answerText.setText("");
                    lastNumeric = false;
                }
            } catch (Exception e) {
                answerText.setText("");
            }
        });

        findViewById(R.id.equals_button).setOnClickListener(v -> onEqual());

        findViewById(R.id.history_button).setOnClickListener(v -> toggleHistory());

        findViewById(R.id.clear_history).setOnClickListener(v -> {
            historyList.clear();
            historyAdapter.notifyDataSetChanged();
            historyRecycler.scrollToPosition(0);
            saveHistory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.bracket_button).setOnClickListener(v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            String text = mainEntryText.getText().toString();
            int openBrackets = countOccurrences(text, '(');
            int closeBrackets = countOccurrences(text, ')');

            if (openBrackets > closeBrackets && (lastNumeric || text.endsWith(")"))) {
                appendToEntry(")");
                lastNumeric = true;
            } else {
                appendToEntry("(");
                lastNumeric = false;
            }
            onCalculate();
            scrollToCursor();
        });

        findViewById(R.id.percentage_button).setOnClickListener(v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            if (lastNumeric && !stateError) {
                appendToEntry("%");
                lastNumeric = true; // Still numeric in some sense for chaining, but actually it's an operator
                onCalculate();
                scrollToCursor();
            }
        });

        findViewById(R.id.sign_button).setOnClickListener(v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));

            int cursor = mainEntryText.getSelectionStart();
            String text = mainEntryText.getText().toString();

            if (text.isEmpty()) {
                appendToEntry("(-");
                return;
            };
            //char before cursor
            char prev = cursor > 0 ? text.charAt(cursor - 1):' ';

            if(cursor > 0 && prev == ')') {

                if(cursor >= 3 && text.substring(cursor - 3, cursor).equals("×(-")) {
                    mainEntryText.getText().delete(cursor - 2, cursor);
                    mainEntryText.setSelection(cursor - 2);
                }
                else {
                    mainEntryText.getText().insert(cursor, "×(-");
                    mainEntryText.setSelection(cursor + 3);
                }
                return;
            }

            if(cursor == 0 || "+-×÷(".indexOf(prev) != -1) {
                if(cursor >= 2 && text.substring(cursor - 2, cursor).equals("(-")) {
                    mainEntryText.getText().delete(cursor - 2, cursor);
                    mainEntryText.setSelection(cursor - 2);
                } else {
                    mainEntryText.getText().insert(cursor, "(-");
                    mainEntryText.setSelection(cursor + 2);
                }
                return;
            }

            int[] bounds = findNumberBounds(text, cursor);
            if (bounds == null) return;

            int start = bounds[0];
            int end = bounds[1];
            String numberPart = text.substring(start, end);

            if (numberPart.startsWith("(-") && numberPart.endsWith(")")) {
                String newVal = numberPart.substring(2, numberPart.length() - 1);
                mainEntryText.getText().replace(start, end, newVal);
                mainEntryText.setSelection(start + newVal.length());

            } else if (numberPart.startsWith("-")) {
                String newVal = numberPart.substring(1);
                mainEntryText.getText().replace(start, end, newVal);
                mainEntryText.setSelection(start + newVal.length());

            } else {
                String newVal = "(-" + numberPart + ")";
                mainEntryText.getText().replace(start, end, newVal);
                mainEntryText.setSelection(start + newVal.length());
            }
            onCalculate();
            scrollToCursor();
        });
    }

    private void setNumericClickListeners() {
        View.OnClickListener listener = v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            Button button = (Button) v;
            if (stateError) {
                mainEntryText.setText(button.getText());
                stateError = false;
            } else {
                appendToEntry(button.getText().toString());
            }
            lastNumeric = true;
            onCalculate();
        };

        findViewById(R.id.button_0).setOnClickListener(listener);
        findViewById(R.id.button_1).setOnClickListener(listener);
        findViewById(R.id.button_2).setOnClickListener(listener);
        findViewById(R.id.button_3).setOnClickListener(listener);
        findViewById(R.id.button_4).setOnClickListener(listener);
        findViewById(R.id.button_5).setOnClickListener(listener);
        findViewById(R.id.button_6).setOnClickListener(listener);
        findViewById(R.id.button_7).setOnClickListener(listener);
        findViewById(R.id.button_8).setOnClickListener(listener);
        findViewById(R.id.button_9).setOnClickListener(listener);
        findViewById(R.id.dec_button).setOnClickListener(v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            if (lastNumeric && !stateError && !lastDot) {
                appendToEntry(".");
                lastNumeric = false;
                lastDot = true;
            }
        });
    }

    private void setOperatorClickListeners() {
        View.OnClickListener listener = v -> {
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.textColor));
            if(!stateError) {
                Button button = (Button) v;
                String op = button.getText().toString();
                String text = mainEntryText.getText().toString();

                if(text.isEmpty()) return;

                char lastChar = text.charAt(text.length() - 1);

                if("+-×÷".indexOf(lastChar) != -1) {
                    mainEntryText.getText().replace(text.length()-1, text.length(), op);
                } else {
                    appendToEntry(op);
                }

                lastNumeric = false;
                lastDot = false;
            }
        };

        findViewById(R.id.plus_button).setOnClickListener(listener);
        findViewById(R.id.subtract_button).setOnClickListener(listener);
        findViewById(R.id.multiply_button).setOnClickListener(listener);
        findViewById(R.id.divide_button).setOnClickListener(listener);
    }

    private void appendToEntry(String str) {
        int start = Math.max(mainEntryText.getSelectionStart(), 0);
        int end = Math.max(mainEntryText.getSelectionEnd(), 0);
        mainEntryText.getText().replace(Math.min(start, end), Math.max(start, end), str);
        mainEntryText.post(() -> {
            mainEntryText.setSelection(mainEntryText.getText().length());
            scrollToCursor();
        });
    }

    private void onCalculate() {
        String expressionStr = mainEntryText.getText().toString();
        if (TextUtils.isEmpty(expressionStr)) {
            answerText.setText("");
            return;
        }

        try {
            // Replace visual operators with math operators for exp4j
            String evalExpr = expressionStr.replace("÷", "/")
                                         .replace("×", "*")
                                         .replace("−", "-");
            
            // Handle percentage logic: convert x% to (x/100)
            evalExpr = evalExpr.replaceAll("([0-9.]+)?%", "($1/100)");
            
            // If the expression ends with an operator, remove it for intermediate calculation
            if (evalExpr.endsWith("+") || evalExpr.endsWith("-") || evalExpr.endsWith("*") || evalExpr.endsWith("/") || evalExpr.endsWith("(")) {
                evalExpr = evalExpr.substring(0, evalExpr.length() - 1);
            }

            if (TextUtils.isEmpty(evalExpr)) {
                answerText.setText("");
                return;
            }

            // Also check for unbalanced brackets for intermediate calculation
            int open = countOccurrences(evalExpr, '(');
            int close = countOccurrences(evalExpr, ')');
            StringBuilder sb = new StringBuilder(evalExpr);
            while (open > close) {
                sb.append(")");
                close++;
            }
            evalExpr = sb.toString();

            Expression expression = new ExpressionBuilder(evalExpr).build();
            double result = expression.evaluate();
            
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                answerText.setText("Error");
                stateError = true;
            } else {
                if (result == (long) result) {
                    answerText.setText(String.valueOf((long) result));
                } else {
                    answerText.setText(String.valueOf(result));
                }
            }
        } catch (Exception e) {
            // Intermediate state might not be calculatable
            answerText.setText("");
        }
    }

    private void onEqual() {
        String resultText = answerText.getText().toString();
        if (!resultText.isEmpty() && !resultText.equals("Error")) {
            String expressionText = mainEntryText.getText().toString();

            mainEntryText.setText(resultText);
            mainEntryText.setTextColor(ContextCompat.getColor(this, R.color.accentOrange));
            mainEntryText.setSelection(mainEntryText.getText().length());
            scrollToCursor();

            answerText.setText("");
            lastNumeric = true;
            lastDot = resultText.contains(".");

            historyList.add(0, new HistoryItem(expressionText, resultText));
            historyAdapter.notifyItemInserted(0);
            historyRecycler.scrollToPosition(0);
            saveHistory();
        }
    }

    private void saveHistory() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY, new Gson().toJson(historyList))
                .apply();
    }

    private void loadHistory() {
        String json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_HISTORY, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<HistoryItem>>() {}.getType();
            historyList = new Gson().fromJson(json, type);
        } else {
            historyList = new ArrayList<>();
        }
    }

    private void toggleHistory() {
        isHistoryVisible = !isHistoryVisible;
        historyLayout.setVisibility(isHistoryVisible ? View.VISIBLE : View.GONE);
        findViewById(R.id.grid_layout).setVisibility(isHistoryVisible ? View.GONE : View.VISIBLE);
    }

    private int countOccurrences(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }

    private int[] findNumberBounds(String text, int cursor) {
        if (text.isEmpty()) return null;

        int start = cursor;
        if (start >= text.length()) start = text.length() - 1;

        while (start > 0 && !isDigitOrDot(text.charAt(start))) {
            start--;
        }

        if (!isDigitOrDot(text.charAt(start))) {
            start = cursor;
            while (start < text.length() && !isDigitOrDot(text.charAt(start))) {
                start++;
            }
        }

        if (start >= text.length() || !isDigitOrDot(text.charAt(start))) {
            return null;
        }

        int end = start;
        while (start > 0 && isDigitOrDot(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() - 1 && isDigitOrDot(text.charAt(end + 1))) {
            end++;
        }

        if (start >= 2 && text.substring(start - 2, start).equals("(-") && end < text.length() - 1 && text.charAt(end + 1) == ')') {
            return new int[]{start - 2, end + 2};
        }

        if (start >= 1 && text.charAt(start - 1) == '-') {
            if (start == 1 || "+−×÷(".indexOf(text.charAt(start - 2)) != -1) {
                return new int[]{start - 1, end + 1};
            }
        }

        return new int[]{start, end + 1};
    }

    private boolean isDigitOrDot(char c) {
        return Character.isDigit(c) || c == '.';
    }

    private void scrollToCursor() {

        mainEntryText.post(() -> {

            if (mainEntryText.getLayout() == null) return;

            int cursorPosition = mainEntryText.getSelectionStart();

            Layout layout = mainEntryText.getLayout();

            int line = layout.getLineForOffset(cursorPosition);

            int lineTop = ((Layout) layout).getLineTop(line);
            int lineBottom = layout.getLineBottom(line);

            int scrollY = mainEntryText.getScrollY();
            int height = mainEntryText.getHeight();

            if (lineBottom > scrollY + height) {
                mainEntryText.scrollTo(0, lineBottom - height);
            }
            else if (lineTop < scrollY) {
                mainEntryText.scrollTo(0, lineTop);
            }
        });
    }

}
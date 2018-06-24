package com.example.admin.audit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ScrollingActivity extends AppCompatActivity {

    private TextView scrollingText;

    private Button gotoNextActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);

        scrollingText = (TextView) findViewById(R.id.scrolling_text);
        gotoNextActivity = (Button) findViewById(R.id.scroll_button);
        scrollingText.setSelected(true);

        gotoNextActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ScrollingActivity.this, CardActivity.class));
            }
        });
    }
}

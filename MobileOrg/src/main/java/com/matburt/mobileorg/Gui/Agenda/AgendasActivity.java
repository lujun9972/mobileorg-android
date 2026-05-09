package com.matburt.mobileorg.Gui.Agenda;

import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;
import com.matburt.mobileorg.R;

public class AgendasActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.agendas);
		
		getSupportActionBar().setTitle("Agenda");
	}
}

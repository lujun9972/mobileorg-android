package com.matburt.mobileorg.Gui.Agenda;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

public class AgendasActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		OrgUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.agendas);
		OrgUtils.applyCustomThemeWindow(this);
		getSupportActionBar().setTitle("Agenda");
	}
}

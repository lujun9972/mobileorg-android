package com.matburt.mobileorg.Gui.Wizard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.view.ViewTreeObserver;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Wizard.Wizards.SSHWizard;
import com.matburt.mobileorg.Gui.Wizard.Wizards.Wizard;
import com.matburt.mobileorg.Settings.SettingsActivity;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.SyncConfigHelper;

public class WizardActivity extends Activity implements RadioGroup.OnCheckedChangeListener, ViewTreeObserver.OnGlobalLayoutListener  {

	private WizardView wizardView;
	private Wizard activeWizard;
	
	private RadioGroup syncGroup;

	private int syncWebDav, syncSdCard, syncNull, syncSSH;

	private static final int REQUEST_IMPORT_CONFIG = 2003;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		OrgUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);
		
		wizardView = (WizardView) findViewById(R.id.wizard_parent);
		// when wizard first starts can't go to next page
		wizardView.setNavButtonStateOnPage(0, false, WizardView.FIRST_PAGE);

		syncGroup = (RadioGroup) findViewById(R.id.sync_group);
		syncGroup.clearCheck();
		syncGroup.setOnCheckedChangeListener(this);

		syncWebDav = ((RadioButton) findViewById(R.id.sync_webdav)).getId();
		syncSdCard = ((RadioButton) findViewById(R.id.sync_sdcard)).getId();
		syncNull = ((RadioButton) findViewById(R.id.sync_null)).getId();
		syncSSH = ((RadioButton) findViewById(R.id.sync_ssh)).getId();
		
		findViewById(R.id.wizard_import_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onImportConfigClicked();
					}
				});

		ViewTreeObserver observer = wizardView.getViewTreeObserver();
		if (observer.isAlive()) {
		  observer.addOnGlobalLayoutListener(this);
		}
	}

	private void onImportConfigClicked() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		// Older Android MimeTypeMap doesn't map ".json" → file MIME unknown
		// → greyed out in SAF picker. Accept any file; wrong picks fail
		// JSON parsing with an error toast in importConfig().
		intent.setType("*/*");
		startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
	}

	private void selectPrevSource(Context context) {
		SharedPreferences srcPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		String syncSource = srcPrefs.getString("syncSource", "");
		
		int id = -1;
		if (syncSource == "")
			return;
		
		if (syncSource.equals("webdav"))
			id = syncWebDav;
		else if (syncSource.equals("sdcard"))
			id = syncSdCard;
        else if (syncSource.equals("scp"))
			id = syncSSH;
		else if (syncSource.equals("null"))
			id = syncNull;
		else 
			return;
		
		syncGroup.check(id);
	}

	@Override
	protected void onPause() {
		super.onPause();
		wizardView.saveCurrentPage();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		if(activeWizard != null)
			activeWizard.refresh();
	}
	

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SSHWizard.SSH_CHOOSE_PUB) {
			if (resultCode == RESULT_OK) {
				String filePath = data.getData().getPath();

				if (activeWizard instanceof SSHWizard)
					((SSHWizard) activeWizard).setPubFile(filePath);
			}
		} else if (requestCode == REQUEST_IMPORT_CONFIG) {
			if (resultCode == RESULT_OK && data != null && data.getData() != null) {
				Uri uri = data.getData();
				String error = SyncConfigHelper.importConfig(this, uri);
				if (error == null) {
					Toast.makeText(this, R.string.wizard_import_config_success,
							Toast.LENGTH_SHORT).show();
					finish();
				} else {
					Toast.makeText(this,
							getString(R.string.sync_config_import_failed, error),
							Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	@Override
	public void onCheckedChanged(RadioGroup arg, int checkedId) {
		this.activeWizard = Wizard.getWizard(getWizardType(checkedId), wizardView,
				this);
		// allow scrolling to next page
		wizardView.enablePage(0);
	}
	
	public Wizard.TYPE getWizardType(int checkedId) {
		if (checkedId == syncWebDav) {
			return Wizard.TYPE.WebDAV;
		} else if (checkedId == syncSdCard) {
			return Wizard.TYPE.SDCard;
		} else if (checkedId == syncSSH) {
			return Wizard.TYPE.SSH;
		} else if (checkedId == syncNull) {
			return Wizard.TYPE.Null;
		}

		return Wizard.TYPE.Null;
	}

	@Override
	public void onGlobalLayout() {
		wizardView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		selectPrevSource(this);
	}
}

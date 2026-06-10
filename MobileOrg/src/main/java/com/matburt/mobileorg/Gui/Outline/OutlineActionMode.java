package com.matburt.mobileorg.Gui.Outline;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.ViewActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.Services.CalendarSyncService;
import com.matburt.mobileorg.Services.RecordingService;
import com.matburt.mobileorg.Services.TimeclockService;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

public class OutlineActionMode implements ActionMode.Callback {

	private Context context;
	private ContentResolver resolver;
	private OrgNodeRepository repo;
	
	private ListView list;
	private OutlineAdapter adapter;
	private int listPosition;
	private OrgNode node;

	public OutlineActionMode(Context context) {
		super();
		this.context = context;
		this.resolver = context.getContentResolver();
		this.repo = new OrgNodeRepository(resolver);
	}
	
	public void initActionMode(ListView list, int position, int restorePosition) {
		initActionMode(list, position);
		this.listPosition = restorePosition;
	}
	
	public void initActionMode(ListView list, int position) {
		list.setItemChecked(position, true);
		this.list = list;
		this.adapter = (OutlineAdapter) list.getAdapter();
		this.listPosition = position;
		this.node = adapter.getItem(position);
	}
	
	@Override
	public void onDestroyActionMode(ActionMode mode) {
		this.list.setItemChecked(this.listPosition, true);
	}
	
	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
		
		if (this.node != null && this.node.id >= 0 && repo.isNodeEditable(node)) {
	        inflater.inflate(R.menu.outline_node, menu);
		}
		else if(this.node != null && repo.isFilenode(node)) {
			if(this.node.name.equals(OrgFile.AGENDA_FILE_ALIAS))
		        inflater.inflate(R.menu.outline_file_uneditable, menu);
			else
				inflater.inflate(R.menu.outline_file, menu);
		} else
	        inflater.inflate(R.menu.outline_node_uneditable, menu);
        
        return true;
	}
	
	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		MenuItem pomoItem = menu.findItem(R.id.menu_pomodoro);
		if (pomoItem != null) {
			TimeclockService service = TimeclockService.getInstance();
			pomoItem.setVisible(service == null || !service.isPomodoroRunning());
		}
		return true;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.menu_edit) {
			runEditNodeActivity(node.id, context);
		} else if (id == R.id.menu_delete) {
			runDeleteNode();
		} else if (id == R.id.menu_delete_file) {
			runDeleteFileNode();
		} else if (id == R.id.menu_clockin) {
			runTimeClockingService();
		} else if (id == R.id.menu_pomodoro) {
			showPomodoroDurationPicker();
		} else if (id == R.id.menu_record) {
			runRecordingService();
		} else if (id == R.id.menu_archive) {
			runArchiveNode(false);
		} else if (id == R.id.menu_archive_tosibling) {
			runArchiveNode(true);
		} else if (id == R.id.menu_view) {
			runViewNodeActivity();
		} else if (id == R.id.menu_recover) {
			runRecover();
		} else if (id == R.id.menu_capturechild) {
			runCaptureActivity(node.id, context);
		} else {
			mode.finish();
			return false;
		}

		mode.finish();
		return true;
	}

	
	public static void runEditNodeActivity(long nodeId, Context context) {
		Intent intent = new Intent(context, EditActivity.class);
		intent.putExtra(EditActivityController.ACTIONMODE, EditActivityController.ACTIONMODE_EDIT);
		intent.putExtra(EditActivityController.NODE_ID, nodeId);
		context.startActivity(intent);
	}
	
	public static  void runCaptureActivity(long id, Context context) {
		Intent intent = new Intent(context, EditActivity.class);
		
		String captureMode = EditActivityController.ACTIONMODE_CREATE;
		if (PreferenceUtils.useAdvancedCapturing()) {
			captureMode = EditActivityController.ACTIONMODE_ADDCHILD;
		}
		
		intent.putExtra(EditActivityController.ACTIONMODE, captureMode);
		intent.putExtra(EditActivityController.NODE_ID, id);
		context.startActivity(intent);
	}
	
	private void runDeleteNode() {	
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(R.string.prompt_node_delete)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								repo.deleteNode(node);
								Synchronizer.announceSyncDone(context);
							}
						})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		builder.create().show();
	}
	
	private void runArchiveNode(final boolean archiveToSibling) {	
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(R.string.prompt_node_archive)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								archiveNode(archiveToSibling);
							}
						})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		builder.create().show();
	}

	private void archiveNode(boolean archiveToSibling) {		
		if(archiveToSibling)
			repo.archiveNodeToSibling(node);
		else
			repo.archiveNode(node);
		Synchronizer.announceSyncDone(context);
	}
	
	private void runDeleteFileNode() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(R.string.prompt_delete_file)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								deleteFileNode();
							}
						})
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
		builder.create().show();
	}
	
	private void deleteFileNode() {
		try {
			OrgFile file = new OrgFileRepository(resolver).getById(node.fileId);
			new OrgFileRepository(resolver).removeFile(file);
			
			Intent calDeleteIntent = new Intent(context, CalendarSyncService.class);
			calDeleteIntent.putExtra(CalendarSyncService.CLEARDB, true);
			calDeleteIntent.putExtra(CalendarSyncService.FILELIST, new String[] {file.filename});
			context.startService(calDeleteIntent);
			
			Synchronizer.announceSyncDone(context);
		} catch (OrgFileNotFoundException e) {}
	}
	
	public static void runViewNodeActivity(long nodeId, Context context) {
		Intent intent = new Intent(context, ViewActivity.class);
		intent.putExtra(ViewActivity.NODE_ID, nodeId);
		context.startActivity(intent);
	}
	
	private void runViewNodeActivity() {		
		runViewNodeActivity(node.id, context);
	}
	
	private void runTimeClockingService() {
		Log.d("MobileOrg", "[ClockIn] OutlineActionMode.runTimeClockingService: node.id=" + node.id
				+ ", name=" + node.name);

		Intent intent = new Intent(context, TimeclockService.class);
		intent.setAction(TimeclockService.ACTION_CLOCK_IN);
		intent.putExtra(TimeclockService.NODE_ID, node.id);
		// 不杀已有 service，由 service 内部处理已 clock in 的情况

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
		Log.d("MobileOrg", "[ClockIn] startService intent sent for node_id=" + node.id);
	}

	private void showPomodoroDurationPicker() {
		int defaultDuration = PreferenceUtils.getPomodoroDuration();
		int defaultCount = PreferenceUtils.getPomodoroCountDefault();
		// Read POMODORO_COUNT from node property if available
		if (node != null && node.getOrgNodePayload() != null) {
			String propValue = node.getOrgNodePayload().getProperty("POMODORO_COUNT");
			if (propValue != null && !propValue.isEmpty()) {
				try {
					defaultCount = Integer.parseInt(propValue.trim());
				} catch (NumberFormatException e) {
					Log.w("MobileOrg", "[Pomodoro] Invalid POMODORO_COUNT property: " + propValue);
				}
			}
		}

		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.HORIZONTAL);
		layout.setGravity(Gravity.CENTER);
		int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, pad);

		// Duration picker (minutes)
		final NumberPicker minutePicker = new NumberPicker(context);
		minutePicker.setMinValue(1);
		minutePicker.setMaxValue(120);
		minutePicker.setValue(defaultDuration);
		minutePicker.setWrapSelectorWheel(true);
		layout.addView(minutePicker);

		TextView minLabel = new TextView(context);
		minLabel.setText(" min × ");
		minLabel.setTextSize(18);
		minLabel.setGravity(Gravity.CENTER);
		layout.addView(minLabel);

		// Count picker
		final NumberPicker countPicker = new NumberPicker(context);
		countPicker.setMinValue(1);
		countPicker.setMaxValue(99);
		countPicker.setValue(defaultCount);
		countPicker.setWrapSelectorWheel(true);
		layout.addView(countPicker);

		TextView countLabel = new TextView(context);
		countLabel.setText(" 个");
		countLabel.setTextSize(18);
		countLabel.setGravity(Gravity.CENTER);
		layout.addView(countLabel);

		new AlertDialog.Builder(((android.app.Activity) context))
				.setTitle(R.string.pomodoro_picker_title)
				.setView(layout)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						minutePicker.clearFocus();
						countPicker.clearFocus();
						int duration = minutePicker.getValue();
						int count = countPicker.getValue();
						Log.d("MobileOrg", "[Pomodoro] User selected: duration=" + duration + " min, count=" + count);
						runPomodoroService(duration, count);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void runPomodoroService(int durationMinutes, int count) {
		Log.d("MobileOrg", "[Pomodoro] runPomodoroService: duration=" + durationMinutes + " min, count=" + count);

		Intent intent = new Intent(context, TimeclockService.class);
		intent.setAction(TimeclockService.ACTION_POMODORO_START);
		intent.putExtra(TimeclockService.POMODORO_DURATION, durationMinutes);
		intent.putExtra(TimeclockService.POMODORO_COUNT, count);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
		Log.d("MobileOrg", "[Pomodoro] startService intent sent for duration=" + durationMinutes);
	}

	private void runRecordingService() {
		if (context instanceof OutlineActivity) {
			((OutlineActivity) context).tryStartRecording(node.id);
		}
	}

	private void runRecover() {
		try {
			OrgFile orgFile = repo.getOrgFile(this.node);
			Log.d("MobileOrg", new OrgFileRepository(resolver).nodesToString(orgFile));
		} catch (OrgFileNotFoundException e) {
			e.printStackTrace();
		}
	}
}

/**
 *  Catroid: An on-device graphical programming language for Android devices
 *  Copyright (C) 2010  Catroid development team
 *  (<http://code.google.com/p/catroid/wiki/Credits>)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.tugraz.ist.catroid.stage;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import at.tugraz.ist.catroid.ProjectManager;
import at.tugraz.ist.catroid.R;
import at.tugraz.ist.catroid.LegoNXT.LegoNXT;
import at.tugraz.ist.catroid.bluetooth.BluetoothManager;
import at.tugraz.ist.catroid.bluetooth.DeviceListActivity;
import at.tugraz.ist.catroid.content.bricks.SensorBrick;
import at.tugraz.ist.catroid.io.SoundManager;
import at.tugraz.ist.catroid.utils.Utils;

public class StageActivity extends Activity {

	private static final int REQUEST_ENABLE_BT = 2000;
	private static final int REQUEST_CONNECT_DEVICE = 1000;
	public static SurfaceView stage;
	private SoundManager soundManager;
	private StageManager stageManager;
	private boolean stagePlaying = false;
	private LegoNXT legoNXT;
	private SensorBrick arduino;
	private BluetoothManager bluetoothManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (Utils.checkForSdCard(this)) {
			Window window = getWindow();
			window.requestFeature(Window.FEATURE_NO_TITLE);
			window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

			setContentView(R.layout.activity_stage);
			stage = (SurfaceView) findViewById(R.id.stageView);

			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			Utils.updateScreenWidthAndHeight(this);

			soundManager = SoundManager.getInstance();
			stageManager = new StageManager(this);
			legoNXT = new LegoNXT(this);
			bluetoothManager = new BluetoothManager(this);

			if (!stageManager.getBluetoothNeeded()) {
				stageManager.start();
				stageManager.startScripts();
				stagePlaying = true;
			} else {
				int bluetoothState = bluetoothManager.activateBluetooth();
				if (bluetoothState == -1) {
					Toast.makeText(StageActivity.this, R.string.notification_blueth_err, Toast.LENGTH_LONG).show();
					finish();
				} else if (bluetoothState == 1) {
					legoNXT.connectLegoNXT();
					// TODO connect to Arduino, when connected do: stageManager.start(); stageManager.startScripts(); stagePlaying = true; 
					arduino.connectArduino();
				}
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i("bt", "requestcode " + requestCode + " result code" + resultCode);
		switch (requestCode) {

			case REQUEST_ENABLE_BT:
				switch (resultCode) {
					case Activity.RESULT_OK:
						legoNXT.connectLegoNXT();
						break;

					case Activity.RESULT_CANCELED:
						Toast.makeText(StageActivity.this, R.string.notification_blueth_err, Toast.LENGTH_LONG).show();
						finish();
						break;
				}

				break;
			case REQUEST_CONNECT_DEVICE: {
				switch (resultCode) {
					case Activity.RESULT_OK:
						String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
						//pairing = data.getExtras().getBoolean(DeviceListActivity.PAIRING);
						legoNXT.startBTCommunicator(address);

						stageManager.startScripts();
						stageManager.start();
						stagePlaying = true;
						break;

					case Activity.RESULT_CANCELED:

						break;

				}
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		// first pointer: MotionEvent.ACTION_DOWN
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			processOnTouch((int) event.getX(), (int) event.getY());
		}

		// second pointer: MotionEvent.ACTION_POINTER_2_DOWN
		if (event.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
			processOnTouch((int) event.getX(1), (int) event.getY(1));
		}

		return false;
	}

	public void processOnTouch(int xCoordinate, int yCoordinate) {
		xCoordinate = xCoordinate + stage.getTop();
		yCoordinate = yCoordinate + stage.getLeft();

		stageManager.processOnTouch(xCoordinate, yCoordinate);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.stage_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.stagemenuStart:
				pauseOrContinue();
				break;
			case R.id.stagemenuConstructionSite:
				manageLoadAndFinish();
				break;
		}
		return true;
	}

	@Override
	protected void onStop() {
		super.onStop();
		soundManager.pause();
		stageManager.pause(false);
		stagePlaying = false;
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		stageManager.resume();
		soundManager.resume();
		stagePlaying = true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stageManager.finish();
		soundManager.clear();
		legoNXT.destroyBTCommunicator();
	}

	@Override
	public void onBackPressed() {
		manageLoadAndFinish();
		legoNXT.destroyBTCommunicator();

	}

	private void manageLoadAndFinish() {
		ProjectManager projectManager = ProjectManager.getInstance();
		int currentSpritePos = projectManager.getCurrentSpritePosition();
		int currentScriptPos = projectManager.getCurrentScriptPosition();
		projectManager.loadProject(projectManager.getCurrentProject().getName(), this, false);
		projectManager.setCurrentSpriteWithPosition(currentSpritePos);
		projectManager.setCurrentScriptWithPosition(currentScriptPos);
		finish();
	}

	private void pauseOrContinue() {
		if (stagePlaying) {
			stageManager.pause(true);
			soundManager.pause();
			stagePlaying = false;
		} else {
			stageManager.resume();
			soundManager.resume();
			stagePlaying = true;
		}
	}

	@Override
	protected void onResume() {
		if (!Utils.checkForSdCard(this)) {
			return;
		}
		super.onResume();
	}
}

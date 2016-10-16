package com.deviantdev.pdsampleproject;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

	private static final String TAG = "PDSampleProject";

	Button btnPlaySound;
	ToggleButton btnToggleSound;
	SeekBar seekbarFrequency;
	SeekBar seekbarVolume;

	/**
	 * The PdService is provided by the pd-for-android library.
	 */
	private PdService pdService = null;

	/**
	 * The volume value as integer from 0 to 100 percent
	 */
	int volume = 0;

	/**
	 * Initialises the pure data service for playing audio and receiving control commands.
	 */
	private final ServiceConnection pdConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			pdService = ((PdService.PdBinder)service).getService();
			initPd();

			try {
				int sampleRate = AudioParameters.suggestSampleRate();
				pdService.initAudio( sampleRate, 0, 2, 8 );
				pdService.startAudio();
			} catch (IOException e) {
				toast(e.toString());
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			pdService.stopAudio();
		}
	};

	/**
	 * Initialises the pure data audio interface and loads the patch file packaged within the app.
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void initPd() {
		File patchFile = null;
		try {
			PdBase.setReceiver(new PdUiDispatcher());
			PdBase.subscribe("android");
			File dir = getFilesDir();
			IoUtils.extractZipResource( getResources().openRawResource( R.raw.pdpatch ), dir, true );
			patchFile = new File( dir, "pdpatch.pd" );
			PdBase.openPatch( patchFile.getAbsolutePath() );
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			finish();
		} finally {
			if (patchFile != null) {
				patchFile.delete();
			}
		}
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		AudioParameters.init(this);
		bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);

		initGui();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			unbindService(pdConnection);
		} catch (IllegalArgumentException e) {
			pdService = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate( R.menu.menu_main, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();

		if ( id == R.id.action_link ) {
			Intent browserIntent = new Intent( Intent.ACTION_VIEW, Uri.parse( "http://www.journal.deviantdev.com/example-libpd-android-studio/" ) );
			startActivity( browserIntent );
			return true;
		}

		if ( id == R.id.action_exit ) {
			moveTaskToBack( true );
			return true;
		}

		return super.onOptionsItemSelected( item );
	}

	/**
	 * Initialises the user interface elements and necessary handlers responsibly for the interaction with the
	 * pre-loaded pure data patch. The code is really pure data patch specific.
	 */
	private void initGui() {
		// touch to play button
		this.btnPlaySound = (Button) findViewById( R.id.buttonPlaySound );
		this.btnPlaySound.setOnTouchListener( new View.OnTouchListener() {
			@Override
			public boolean onTouch( View v, MotionEvent event ) {
				if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
					PdBase.sendFloat( "osc_volume", volume / 100f ); // send volume (0 to 1)
				} else if ( event.getAction() == MotionEvent.ACTION_UP ) {
					PdBase.sendFloat( "osc_volume", 0 ); // quiet down
				}
				return false;
			}
		} );

		// toggle play button
		this.btnToggleSound = (ToggleButton) findViewById( R.id.buttonToggleSound );
		this.btnToggleSound.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {

				if ( btnPlaySound.isEnabled() ) {
					btnPlaySound.setEnabled( false );
					PdBase.sendFloat( "osc_volume", volume / 100f ); // enable volume while locked
				} else {
					btnPlaySound.setEnabled( true );
					PdBase.sendFloat( "osc_volume", 0 ); // quiet down for after unlock
				}
			}
		} );

		// seekbar for volume
		this.seekbarVolume = (SeekBar) findViewById( R.id.seekbarVolume );
		this.seekbarVolume.setMax( 100 );
		this.seekbarVolume.incrementProgressBy( 1 );
		this.seekbarVolume.setProgress( 0 );
		this.seekbarVolume.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {

			public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
				volume = progress;
				if ( btnToggleSound.isChecked() ) {
					PdBase.sendFloat( "osc_volume", volume / 100f ); // send volume (0 to 1) if locked
				}
			}

			public void onStartTrackingTouch( SeekBar seekBar ) {}

			public void onStopTrackingTouch( SeekBar seekBar ) {}
		} );

		// seekbar for frequency
		this.seekbarFrequency = (SeekBar) findViewById( R.id.seekbarFrequency );
		this.seekbarFrequency.setMax( 100 );
		this.seekbarFrequency.incrementProgressBy( 1 );
		this.seekbarFrequency.setProgress( 0 );
		this.seekbarFrequency.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
				if ( progress == 0 ) progress = 1;
				float a = progress / 100f;
				float frequency = (float) ( 2500 * Math.exp( 2.19722 * a ) - 2500 );
				PdBase.sendFloat( "osc_pitch", frequency );
			}

			public void onStartTrackingTouch( SeekBar seekBar ) {}

			public void onStopTrackingTouch( SeekBar seekBar ) {}
		} );

		FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
		if ( fab != null ) {
			fab.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent i = new Intent(Intent.ACTION_SEND);
					i.setType("message/rfc822");
					i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"contact@deviantdev.com"});
					i.putExtra( Intent.EXTRA_SUBJECT, "Mail to the Author...");
					i.putExtra(Intent.EXTRA_TEXT   , "Thanks for all the fish!");
					try {
						startActivity(Intent.createChooser(i, "Send mail..."));
					} catch (android.content.ActivityNotFoundException ex) {
						Toast.makeText(MainActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
					}
				}
			});
		}
	}

	/**
	 * Trigger a native Android toast message.
	 * @param text
	 */
	private void toast(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);
				toast.setText(TAG + ": " + text);
				toast.show();
			}
		});
	}
}
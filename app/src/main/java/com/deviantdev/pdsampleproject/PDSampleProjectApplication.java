package com.deviantdev.pdsampleproject;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class PDSampleProjectApplication extends Application {

	@Override
	public void onCreate() {

		initLanguageDebugMode("en");

		super.onCreate();
	}

	private void initLanguageDebugMode(String localeString) {
		Resources res = getApplicationContext().getResources();

		Locale locale = new Locale(localeString);
		Locale.setDefault(locale);

		Configuration config = new Configuration();
		config.locale = locale;

		res.updateConfiguration(config, res.getDisplayMetrics());
	}
}

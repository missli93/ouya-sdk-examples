/*
 * Copyright (C) 2012-2015 OUYA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package plugin.ouya;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;
import com.ansca.corona.*;
import java.io.IOException;
import java.io.InputStream;
import tv.ouya.console.api.OuyaController;
import tv.ouya.sdk.corona.*;

public class LuaLoader implements com.naef.jnlua.JavaFunction {
	
	private final String TAG = LuaLoader.class.getSimpleName();
		
	com.naef.jnlua.NamedJavaFunction[] mLuaFunctions = null;
	
	private static LuaLoader.CoronaRuntimeEventHandler sRuntimeEventHandler = null;

	public static Activity sActivity = null;

	public LuaLoader() {
	
		if (null == sRuntimeEventHandler) {
			// OUYA activity has not been initialized
			
			Log.i(TAG, "Switching to OUYA SDK activity");
			final Activity activity = CoronaEnvironment.getCoronaActivity();
			if (null != activity) {
				Intent intent = new Intent(activity, CoronaOuyaActivity.class);
				activity.startActivity(intent);
				activity.finish();
			}
			return;
		}
		
		// Set up a Corona runtime listener used to add custom APIs to Lua.
		if (null == sRuntimeEventHandler) { //only construct one
			sRuntimeEventHandler = new LuaLoader.CoronaRuntimeEventHandler();
			com.ansca.corona.CoronaEnvironment.addRuntimeListener(sRuntimeEventHandler);
		}
	}

	/**
	 * Called when this plugin has been loaded by Lua's require() function.
	 * <p>
	 * Warning! This method is not called on the main UI thread.
	 */
	@Override
	public int invoke(com.naef.jnlua.LuaState luaState) {
		
		//Log.i(LOG_TAG, "invoke");

		// This is where you would register Lua functions into this object's Lua library.
		
		// the normal way to register named java functions
		//Log.i(LOG_TAG, "Register named functions for lua");
		
		// Add a module named "myTests" to Lua having the following functions.
		mLuaFunctions = new com.naef.jnlua.NamedJavaFunction[] {
			new LuaOuyaIsAvailable(),
			new LuaOuyaShutdown(),
			new AsyncLuaOuyaGetControllerName(),
			new AsyncLuaOuyaInitInput(),
			new AsyncLuaInitOuyaPlugin(),
			new AsyncLuaOuyaRequestGamerInfo(),
			new AsyncLuaOuyaRequestProducts(),
			new AsyncLuaOuyaRequestPurchase(),
			new AsyncLuaOuyaRequestReceipts(),
			new LuaOuyaGetDeviceHardwareName(),
			new LuaOuyaGetStringResource(),
		};
		luaState.register("ouyaSDK", mLuaFunctions);
		luaState.pop(1);
		
		initializeOUYA();
		
		//Log.i(LOG_TAG, "Named functions for lua registered.");
		//Log.i(LOG_TAG, "****\n*****\n****\n****\n****\n");

		return 1;
	}
	
	// instead register named java functions so we have access to the events
	
	/** Receives and handles Corona runtime events. */
	private class CoronaRuntimeEventHandler implements com.ansca.corona.CoronaRuntimeListener {

		private CoronaActivity.OnActivityResultHandler mOnActivityResultHandler = null;

		/**
		 * Called after the Corona runtime has been created and just before executing the "main.lua" file.
		 * This is the application's opportunity to register custom APIs into Lua.
		 * <p>
		 * Warning! This method is not called on the main thread.
		 * @param runtime Reference to the CoronaRuntime object that has just been loaded/initialized.
		 *                Provides a LuaState object that allows the application to extend the Lua API.
		 */
		@Override
		public void onLoaded(com.ansca.corona.CoronaRuntime runtime) {
			Log.i(TAG, "CoronaRuntimeEventHandler.onLoaded");
		}
		
		/**
		 * Called just after the Corona runtime has executed the "main.lua" file.
		 * <p>
		 * Warning! This method is not called on the main thread.
		 * @param runtime Reference to the CoronaRuntime object that has just been started.
		 */
		@Override
		public void onStarted(com.ansca.corona.CoronaRuntime runtime) {
			Log.d(TAG, "CoronaRuntimeEventHandler.onStarted");

			mOnActivityResultHandler = new CoronaActivity.OnActivityResultHandler() {
				@Override
				public void onHandleActivityResult(CoronaActivity activity, int requestCode, int resultCode, android.content.Intent data) {
					Log.d(TAG, "onHandleActivityResult");
					CoronaOuyaFacade coronaOuyaFacade = IOuyaActivity.GetCoronaOuyaFacade();
			    	if (null != coronaOuyaFacade)
					{
				    	// Forward this result to the facade, in case it is waiting for any activity results
				        if(coronaOuyaFacade.processActivityResult(requestCode, resultCode, data)) {
				            return;
				        }
					}
				}
			};

			Log.d(TAG, "Get activity from Corona Environment...");
			CoronaActivity coronaActivity = CoronaEnvironment.getCoronaActivity();
			if (null != coronaActivity) {
				Log.d(TAG, "Registered Activity Result Handler");
				int result = coronaActivity.registerActivityResultHandler(mOnActivityResultHandler);

				Log.d(TAG, "Registered Activity Result Handler returned="+result);
			}
		}
		
		/**
		 * Called just after the Corona runtime has been suspended which pauses all rendering, audio, timers,
		 * and other Corona related operations. This can happen when another Android activity (ie: window) has
		 * been displayed, when the screen has been powered off, or when the screen lock is shown.
		 * <p>
		 * Warning! This method is not called on the main thread.
		 * @param runtime Reference to the CoronaRuntime object that has just been suspended.
		 */
		@Override
		public void onSuspended(com.ansca.corona.CoronaRuntime runtime) {
			Log.d(TAG, "CoronaRuntimeEventHandler.onSuspended");
		}
		
		/**
		 * Called just after the Corona runtime has been resumed after a suspend.
		 * <p>
		 * Warning! This method is not called on the main thread.
		 * @param runtime Reference to the CoronaRuntime object that has just been resumed.
		 */
		@Override
		public void onResumed(com.ansca.corona.CoronaRuntime runtime) {
			Log.d(TAG, "CoronaRuntimeEventHandler.onResumed");
		}
		
		/**
		 * Called just before the Corona runtime terminates.
		 * <p>
		 * This happens when the Corona activity is being destroyed which happens when the user presses the Back button
		 * on the activity, when the native.requestExit() method is called in Lua, or when the activity's finish()
		 * method is called. This does not mean that the application is exiting.
		 * <p>
		 * Warning! This method is not called on the main thread.
		 * @param runtime Reference to the CoronaRuntime object that is being terminated.
		 */
		@Override
		public void onExiting(com.ansca.corona.CoronaRuntime runtime) {
			Log.d(TAG, "CoronaRuntimeEventHandler.onExiting");
		}
	}

	private void initializeOUYA() {
		
		Log.d(TAG, "Initializing OUYA...");
		
		Context context = com.ansca.corona.CoronaEnvironment.getApplicationContext();
		if (null == context) {
			Log.e(TAG, "initializeOUYA: Context is null!");
			return;
		}
		
		Log.d(TAG, "Load signing key...");
		// load the application key from assets
		try {
			AssetManager assetManager = context.getAssets();
			InputStream inputStream = assetManager.open("key.der", AssetManager.ACCESS_BUFFER);
			byte[] applicationKey = new byte[inputStream.available()];
			inputStream.read(applicationKey);
			inputStream.close();
			IOuyaActivity.SetApplicationKey(applicationKey);
			
			Log.d(TAG, "***Loaded signing key*********");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Log.d(TAG, "Initialize controller...");
		
		// Init the controller
		OuyaController.init(context);

		Log.d(TAG, "Get activity from Corona Environment...");
		final Activity activity = CoronaEnvironment.getCoronaActivity();
		if (null == activity) {
			Log.e(TAG, "initializeOUYA: Corona activity is null!");
		} else {
			sActivity = activity;
		}
	}
}
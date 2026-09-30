package com.google.android.libraries.launcherclient;

import android.view.WindowManager.LayoutParams;
import android.os.Bundle;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;

// Wire compatibility contract. Declaration order determines Binder transaction IDs.
interface ILauncherOverlay {
    oneway void startScroll();
    oneway void onScroll(float progress);
    oneway void endScroll();
    oneway void windowAttached(in LayoutParams lp, ILauncherOverlayCallback callback, int flags);
    oneway void windowDetached(boolean changingConfigurations);
    oneway void closeOverlay(int flags);
    oneway void onPause();
    oneway void onResume();
    oneway void openOverlay(int flags);
    oneway void requestVoiceDetection(boolean start);
    String getVoiceSearchLanguage();
    boolean isVoiceDetectionRunning();
    boolean hasOverlayContent();
    oneway void windowAttached2(in Bundle configuration, ILauncherOverlayCallback callback);
    oneway void unusedMethod();
    oneway void setActivityState(int state);
    boolean startSearch(in byte[] data, in Bundle extras);
}

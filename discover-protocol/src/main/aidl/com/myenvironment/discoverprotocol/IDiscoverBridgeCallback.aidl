package com.myenvironment.discoverprotocol;

interface IDiscoverBridgeCallback {
    oneway void onConnected(IBinder overlay, int googleApiVersion);
    oneway void onDisconnected();
    oneway void onError(String message);
}

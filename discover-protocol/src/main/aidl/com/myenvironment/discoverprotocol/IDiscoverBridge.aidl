package com.myenvironment.discoverprotocol;
import com.myenvironment.discoverprotocol.IDiscoverBridgeCallback;

interface IDiscoverBridge {
    oneway void connect(IDiscoverBridgeCallback callback);
    oneway void disconnect();
}

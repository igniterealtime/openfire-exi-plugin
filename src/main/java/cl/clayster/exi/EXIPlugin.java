/**
 * Copyright 2013-2014 Javier Placencio, 2023 Ignite Realtime Foundation
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
package cl.clayster.exi;

import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class EXIPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger(EXIPlugin.class);

    @Override
    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
        try {
            EXIUtils.generateSchemasFile();
            EXIUtils.generateDefaultCanonicalSchema();
        } catch (IOException e) {
            Log.warn("Exception while trying to initialize the Openfire EXI plugin.", e);
            return;
        }
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        if (socketAcceptor == null) return;

        socketAcceptor.getFilterChain().addBefore("xmpp", EXIAlternativeBindingFilter.filterName, new EXIAlternativeBindingFilter());
        EXIFilter exiFilter = new EXIFilter();
        socketAcceptor.getFilterChain().addAfter("xmpp", EXIFilter.filterName, exiFilter);
        Log.info("Starting EXI Plugin");
    }

    @Override
    public void destroyPlugin()
    {
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        final NioSocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        if (socketAcceptor != null && socketAcceptor.getFilterChain().contains(EXIFilter.filterName)) {
            socketAcceptor.getFilterChain().remove(EXIFilter.filterName);
        }
        if (socketAcceptor != null && socketAcceptor.getFilterChain().contains(EXIAlternativeBindingFilter.filterName)) {
            socketAcceptor.getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
        }
    }
}

package cl.clayster.exi;

import java.io.File;
import java.io.IOException;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;

public class EXIPlugin implements Plugin{

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		try {
			EXIUtils.generateSchemasFile();
			EXIUtils.generateDefaultCanonicalSchema();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        if (socketAcceptor == null)	return;
        
    	socketAcceptor.getFilterChain().addBefore("xmpp", EXIAlternativeBindingFilter.filterName, new EXIAlternativeBindingFilter());
    	EXIFilter exiFilter = new EXIFilter();
    	socketAcceptor.getFilterChain().addAfter("xmpp", EXIFilter.filterName, exiFilter);
    	System.out.println("Starting EXI Plugin");
	}

	@Override
	public void destroyPlugin() {
		ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
		if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains(EXIFilter.filterName)) {
        	connManager.getSocketAcceptor().getFilterChain().remove(EXIFilter.filterName);
        }
        if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains(EXIAlternativeBindingFilter.filterName)) {
        	connManager.getSocketAcceptor().getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
        }
        
	}
	
	

}

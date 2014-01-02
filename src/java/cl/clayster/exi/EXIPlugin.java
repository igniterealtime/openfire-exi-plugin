package cl.clayster.exi;

import java.io.File;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;

public class EXIPlugin implements Plugin{
	EXIFilter exiFilter;
	EXIAlternativeBindingFilter abFilter;

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		// Add filter to filter chain builder
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        if (socketAcceptor == null)	return;
        
    	exiFilter = new EXIFilter();
    	socketAcceptor.getFilterChain().addLast(EXIFilter.filterName, exiFilter);
    	abFilter = new EXIAlternativeBindingFilter();
    	socketAcceptor.getFilterChain().addBefore("xmpp", EXIAlternativeBindingFilter.filterName, abFilter);
    	System.out.println("Starting EXI Plugin");
	}

	@Override
	public void destroyPlugin() {
		ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
		if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains(EXIFilter.filterName)) {
        	connManager.getSocketAcceptor().getFilterChain().remove(EXIFilter.filterName);
        }
        exiFilter = null;
        if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains(EXIAlternativeBindingFilter.filterName)) {
        	connManager.getSocketAcceptor().getFilterChain().remove(EXIAlternativeBindingFilter.filterName);
        }
        abFilter = null;
        
	}

}

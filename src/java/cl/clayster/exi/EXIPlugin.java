package cl.clayster.exi;

import java.io.File;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;

public class EXIPlugin implements Plugin{
	EXIFilter exiFilter;
	static EXIEncoderInterceptor exiEncoderInterceptor;

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		// Add filter to filter chain builder
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        exiFilter = new EXIFilter(exiEncoderInterceptor);
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        exiEncoderInterceptor = new EXIEncoderInterceptor();
        
        if (socketAcceptor != null) {
        	socketAcceptor.getFilterChain().addLast(EXIFilter.filterName, exiFilter);
        }
        InterceptorManager.getInstance().addInterceptor(exiEncoderInterceptor);
        System.out.println("Starting EXI Plugin");
	}

	@Override
	public void destroyPlugin() {
		ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains("exiFilter")) {
        	connManager.getSocketAcceptor().getFilterChain().remove("exiFilter");
        }
        exiFilter = null;
	}

}

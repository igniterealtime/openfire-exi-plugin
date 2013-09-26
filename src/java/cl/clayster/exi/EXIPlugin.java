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
	EXIEncoderInterceptor exiEncoderInterceptor;
	EXIInterceptor exiInterceptor;

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		// Add filter to filter chain builder
        ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        exiFilter = new EXIFilter();
        SocketAcceptor socketAcceptor = connManager.getSocketAcceptor();
        exiEncoderInterceptor = new EXIEncoderInterceptor(exiFilter);
        
        if (socketAcceptor != null) {
        	socketAcceptor.getFilterChain().addBefore("xmpp", "exiFilter", exiFilter);
        }
        InterceptorManager.getInstance().addInterceptor(exiEncoderInterceptor);
        // TODO: agregar el EXIEncoderInterceptor asociado a la conexión, y removerla al cerrar la conexión!
        exiInterceptor = new EXIInterceptor(exiFilter);
        InterceptorManager.getInstance().addInterceptor(exiInterceptor);
        
        System.out.println("EXIPlugin Started!");
	}

	@Override
	public void destroyPlugin() {
		ConnectionManagerImpl connManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        if (connManager.getSocketAcceptor() != null && connManager.getSocketAcceptor().getFilterChain().contains("exiFilter")) {
        	connManager.getSocketAcceptor().getFilterChain().remove("exiFilter");
        }
                
        // TODO: ¿destruir los EXIEncoders y EXIDecoders?
        
        exiFilter = null;
	}

}

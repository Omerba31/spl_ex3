package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

import java.io.IOException;

public interface BidiMessagingProtocol<T> {
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    void start(int connectionId, Connections<T> connections);

	T process(T message) throws Exception;
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}

/** 
 
Copyright 2013 Intel Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 
*/ 

package com.intel.cosbench.api.nioengine;

import static com.intel.cosbench.api.nioengine.NIOEngineConstants.*;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;

import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;
import com.intel.cosbench.api.context.*;
import com.intel.cosbench.api.ioengine.*;


/**
 * This class encapsulates a NIO Engine implementation for the IOEngine API.
 * 
 * @IOEngineor ywang19
 * 
 */

// below is for httpcore 4.1.3
/*
class NIOEngine extends NoneIOEngine {

	private int channels;
	
	private ConnectingIOReactor ioReactor;
	private IOEventDispatch ioEventDispatch;
	private CountDownLatch requestCount;

    public NIOEngine() {
    }
    
    public CountDownLatch getLatch()
    {
    	return requestCount;
    }
    
    public static void main(String[] args)
    {
    	NIOEngine ioengine = new NIOEngine();

    	LogManager manager = LogFactory.createLogManager();
        Logger logger = manager.getLogger();
        	
    	ioengine.init(null,logger);
    	ioengine.startup();
    	
    	try
    	{
    		ioengine.issueRequest(ioengine.getLatch());
    	}catch(InterruptedException ie) {
    		ie.printStackTrace();
    	}
    	
    	ioengine.shutdown();
    	
    }
    
    @Override
    public boolean init(Config config, Logger logger) {
        super.init(config, logger);
        channels = 8;

//        channels = config.getInt(IOENGINE_CHANNELS_KEY, IOENGINE_CHANNELS_DEFAULT);
        parms.put(IOENGINE_CHANNELS_KEY, channels);
        
        logger.debug("using IOEngine config: {}", parms);
        
        try
        {
        	HttpHost proxy = new HttpHost("proxy-prc.intel.com", 911, "http");
        	HttpParams params = new SyncBasicHttpParams();
            params
                .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 50000)
                .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 100000)
                .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                .setParameter(CoreProtocolPNames.USER_AGENT, "HttpComponents/1.1")
           	.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

                 ioReactor = new DefaultConnectingIOReactor(channels, params);

            HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                    new RequestContent(),
                    new RequestTargetHost(),
                    new RequestConnControl(),
                    new RequestUserAgent(),
                    new RequestExpectContinue()});

            // We are going to use this object to synchronize between the 
            // I/O event and main threads
            requestCount = new CountDownLatch(1);
          
            BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
                    httpproc,
                    new MyHttpRequestExecutionHandler(requestCount),
                    new DefaultConnectionReuseStrategy(),
                    params);
           
            handler.setEventListener(new EventLogger());
            
            ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
            
      }catch(Exception e) {
            logger.debug("NIOEngine is failed to initialize");
        	e.printStackTrace();
        	
        	return false;
        }

        logger.debug("NIOEngine has been initialized");
        
        return true;
    }

    public void issueRequest(final CountDownLatch requestCount) throws InterruptedException {
    	 SessionRequest[] reqs = new SessionRequest[1];
         reqs[0] = ioReactor.connect(
                 new InetSocketAddress("www.yahoo.com", 80), 
                 null, 
                 new HttpHost("www.yahoo.com"),
                 new MySessionRequestCallback(requestCount));
         // Block until all connections signal
         // completion of the request execution
         requestCount.await();
    }
    
    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public boolean shutdown() throws IOEngineException {
    	
    	try {
    		ioReactor.shutdown();
    	}catch(IOException e) {
    		logger.error("Failed to shut down I/O Reactor.");
    		
    		throw new IOEngineException(e);
    	}
    	
    	return true;
    }
    
    @Override
    public IOEngineContext startup() throws IOEngineException {
        super.startup();
        
        try {
            // 
        	Thread ioThread = new Thread(new Runnable() {

                public void run() {
                    try {
                        // Ready to go!
                        ioReactor.execute(ioEventDispatch);
                    } catch (InterruptedIOException ex) {
                        System.err.println("Interrupted");
                    } catch (IOException e) {
                        System.err.println("I/O error: " + e.getMessage());
                    }
                    System.out.println("Shutdown");
                }

            });
            // Start the client thread
            ioThread.start();            
        	
        } catch (Exception e) {
        	logger.error(e.getMessage());
        	
        	throw new IOEngineException(e);
        }
        
        return createContext();
    }

    private IOEngineContext createContext() {
        IOEngineContext context = new IOEngineContext();
        return context;
    }

    static class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        private final static String REQUEST_SENT       = "request-sent";
        private final static String RESPONSE_RECEIVED  = "response-received";
        
        private final CountDownLatch requestCount;
        
        public MyHttpRequestExecutionHandler(final CountDownLatch requestCount) {
            super();
            this.requestCount = requestCount;
        }
        
        public void initalizeContext(final HttpContext context, final Object attachment) {
            HttpHost targetHost = (HttpHost) attachment;
            context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
        }
        
        public void finalizeContext(final HttpContext context) {
            Object flag = context.getAttribute(RESPONSE_RECEIVED);
            if (flag == null) {
                // Signal completion of the request execution
                requestCount.countDown();
            }
        }

        public HttpRequest submitRequest(final HttpContext context) {
            HttpHost targetHost = (HttpHost) context.getAttribute(
                    ExecutionContext.HTTP_TARGET_HOST);
            Object flag = context.getAttribute(REQUEST_SENT);
            if (flag == null) {
                // Stick some object into the context
                context.setAttribute(REQUEST_SENT, Boolean.TRUE);

                System.out.println("--------------");
                System.out.println("Sending request to " + targetHost);
                System.out.println("--------------");
                
                return new BasicHttpRequest("GET", "/");
            } else {
                // No new request to submit
                return null;
            }
        }
        
        public void handleResponse(final HttpResponse response, final HttpContext context) {
            HttpEntity entity = response.getEntity();
            try {
                String content = EntityUtils.toString(entity);
                
                System.out.println("--------------");
                System.out.println(response.getStatusLine());
                System.out.println("--------------");
                System.out.println("Document length: " + content.length());
                System.out.println("--------------");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }

            context.setAttribute(RESPONSE_RECEIVED, Boolean.TRUE);
            
            // Signal completion of the request execution
            requestCount.countDown();
        }
        
    }
    
    static class MySessionRequestCallback implements SessionRequestCallback {

        private final CountDownLatch requestCount;        
        
        public MySessionRequestCallback(final CountDownLatch requestCount) {
            super();
            this.requestCount = requestCount;
        }
        
        public void cancelled(final SessionRequest request) {
            System.out.println("Connect request cancelled: " + request.getRemoteAddress());
            this.requestCount.countDown();
        }

        public void completed(final SessionRequest request) {
        }

        public void failed(final SessionRequest request) {
            System.out.println("Connect request failed: " + request.getRemoteAddress());
            this.requestCount.countDown();
        }

        public void timeout(final SessionRequest request) {
            System.out.println("Connect request timed out: " + request.getRemoteAddress());
            this.requestCount.countDown();
        }
        
    }
    
    static class EventLogger implements EventListener {

        public void connectionOpen(final NHttpConnection conn) {
            System.out.println("Connection open: " + conn);
        }

        public void connectionTimeout(final NHttpConnection conn) {
            System.out.println("Connection timed out: " + conn);
        }

        public void connectionClosed(final NHttpConnection conn) {
            System.out.println("Connection closed: " + conn);
        }

        public void fatalIOException(final IOException ex, final NHttpConnection conn) {
            System.err.println("I/O error: " + ex.getMessage());
        }

        public void fatalProtocolException(final HttpException ex, final NHttpConnection conn) {
            System.err.println("HTTP error: " + ex.getMessage());
        }
        
    }

}
*/


/* below is for httpcore 4.3-beta2 */

/**
 * This class encapsulates basic operations need to setup NIO engine.
 * 
 * @author ywang19
 *
 */
public class NIOEngine extends NoneIOEngine {

	private int channels = IOENGINE_CHANNELS_DEFAULT;		// the number of working channel.
	private int concurrency = IOENGINE_CONCURRENCY_DEFAULT; 	// the queue or pool size in max.

	private ConnectingIOReactor ioReactor;
	private IOEventDispatch ioEventDispatch;
	private BasicNIOConnPool connPool;
	

	public BasicNIOConnPool getConnPool() {
		return connPool;
	}

	public int getChannels() {
		return channels;
	}

	public void setChannels(int channels) {
		this.channels = channels;
	}

	public int getConcurrency() {
		return concurrency;
	}

	public void setConcurrency(int concurrency) {
		this.concurrency = concurrency;
	}

   
    public NIOEngine() {
    }

    
    @Override
    public boolean init(Config config, Logger logger) {
        super.init(config, logger);
        
        //@TODO
        channels = 8;	// how many io channel reactors will be used to serve i/o.
        concurrency = 16;	// how many outstanding io can support.
//      channels = config.getInt(IOENGINE_CHANNELS_KEY, IOENGINE_CHANNELS_DEFAULT);
//      concurrency = config.getInt(IOENGINE_CONCURRENCY_KEY, IOENGINE_CONCURRENCY_DEFAULT);

//        parms.put(IOENGINE_CHANNELS_KEY, channels);
//        parms.put(IOENGINE_CONCURRENCY_KEY, concurrency);
        
        
        logger.debug("using IOEngine config: {}", parms);
        
        return true;
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public boolean shutdown() throws IOEngineException {
    	
    	try {
    		ioReactor.shutdown();
    	}catch(IOException e) {
    		logger.error("Failed to shut down I/O Reactor.");
    		
    		throw new IOEngineException(e);
    	}
    	
    	return true;
    }
    
    @Override
    public IOEngineContext startup() throws IOEngineException {
        super.startup();
        
        try {
            // 
        	Thread ioThread = new Thread(new Runnable() {

                public void run() {
                    try {
                        // Ready to go!
                        ioReactor.execute(ioEventDispatch);
                    } catch (InterruptedIOException ex) {
                        System.err.println("Interrupted");
                    } catch (IOException e) {
                        System.err.println("I/O error: " + e.getMessage());
                    }
                    System.out.println("Shutdown");
                }

            });
            // Start the client thread
            ioThread.start();            
        	
        } catch (Exception e) {
        	logger.error(e.getMessage());
        	
        	throw new IOEngineException(e);
        }
        
        return createContext();
    }

    private IOEngineContext createContext() {
        IOEngineContext context = new IOEngineContext();
        return context;
    }
    
    public NIOClient newClient() {
    	NIOClient ioclient = new NIOClient(getConnPool());
    	
    	return ioclient;
    }

}


package com.red5pro.live;

import java.util.*;

import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IServiceCapableConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamPlaybackSecurity;
import org.red5.server.api.stream.IStreamPublishSecurity;
import org.red5.server.api.stream.ISubscriberStream;

import com.red5pro.examples.service.LiveStreamListService;
import com.red5pro.plugin.Red5ProPlugin;
/**
 * This example application adapter illustrates some of the most commonly used overrides.
 * @author Andy Shaules
 *
 */
public class Red5ProLive extends MultiThreadedApplicationAdapter implements IStreamPlaybackSecurity, IStreamPublishSecurity{

	//private double audioTime = 0.0;
	//private List<String> streams = new ArrayList<String>(); 


	/**
	 * Application life-cycle begins here
	 */
	public boolean appStart(IScope scope){

		super.appStart(scope);		
		//This demo app creates a service to get stream names.
		//There are two ways to register a handler for RPC calls.
		//One is to create a bean in red5-web.xml with a name that ends with a dot '.'
		//This example handler bean name would be 'streams.'

		//The second way is to create it at run time and register using the scope API.
		//Create an object to handle http request from this app
		LiveStreamListService rpcHandler = new LiveStreamListService(this);
		scope.registerServiceHandler("streams", rpcHandler);

		//To implement security handlers to prevent access to streams, shared objects, or publishing,
		//register an IStreamSecurity or ISharedObjectSecurity type.
		this.registerStreamPlaybackSecurity(this);
		this.registerStreamPublishSecurity(this);


		//Return false to prevent app from starting for any reason
		return true;
	}

	public void doStats(){

		Iterator<IConnection> conns = scope.getClientConnections().iterator();

		while(conns.hasNext()){
			IConnection conn = conns.next();
			String userId=conn.getAttribute("userId").toString();
			String broadcastId=conn.getAttribute("broadcastId").toString();
			String type=conn.getAttribute("type").toString();
			String age=conn.getAttribute("age").toString();
			Long ageComputeable = Long.valueOf(age);
			long duration = System.currentTimeMillis() -ageComputeable;

			log.info("user id {}  broadcast {}  type {} duration {}",new Object[]{userId,broadcastId,type,duration});


		}

	}


	private boolean isInternal(IConnection conn){
		Map<String, Object> params = conn.getConnectParams();
		//com links, if they connected the password. 
		if(params.containsKey("clusterPass")){
			String configuredPass=Red5ProPlugin.getCluster().getConfiguration().getPassword();
			if(params.get("clusterPass").equals(configuredPass)){
				return true;
			}
		}
		//stream links, if they connected with password.
		if(params.containsKey("restreamer")){
			String configuredPass=Red5ProPlugin.getCluster().getConfiguration().getPassword();
			if(params.get("restreamer").equals(configuredPass)){
				return true;
			}
		}
		//this case should include original publisher params at app adapter level.
		if(params.containsKey("cluster-restreamer-name")){ 
			return true;
		}
		//this is the stream manager checking if we are alive.
		if(params.containsKey("streammanager-checker")){ 
			return true;
		}		
		return false;
		
	}
	/**
	 * Called when a client connects to the application.	
	 */
	public boolean appConnect(IConnection conn, Object[] params){

		super.appConnect(conn, params); 
		
		if(isInternal(conn)){
			//this connection is a communication link used in clustering pro instance.
			log.info("Red5 Pro clustering connection received."); 
		}else{
			//else this conneciton is a serviceable client. 
			log.info("Red5 Pro service connection received.");
		}
		

		//begin parameter section	
		if(params!=null && params.length>=5){
			String userId = String.valueOf(params[0]);
			String broadcastId = String.valueOf(params[1]);
			String type = String.valueOf(params[2]);
			String when = String.valueOf(params[3]);
			//how old is this token?
			Long timeWhen = Long.valueOf(when);
			long age = System.currentTimeMillis() - timeWhen;

			if(age>30000){//30 seconds for example
				//REJECT CLIENT, OLD COOKIE!
				//return false; 
			}

			String digest = String.valueOf(params[4]);

			StringBuilder sb = new StringBuilder();
			sb.append(userId);
			sb.append(broadcastId);
			sb.append(type);
			sb.append(when);
			String digestReformed = "TODO";//=recreateDigest(userId,broadcastId,type,when);

			if(!digestReformed.equals(digest)){
				//REJECT CLIENT, Bad digest!
				//return false;
			}else{				
				//Lets set server-side session attributes.
				conn.setAttribute("userId", userId);
				conn.setAttribute("broadcastId", broadcastId);
				conn.setAttribute("type", type);
				conn.setAttribute("sessionAge", age);
			}


		}else{
			//REJECT CLIENT, NO PARAMS!
			//return false;
		}
		//end parameter section	

		//If you have a custom session or state object,
		//set an attribute to the connection with it.
		conn.setAttribute("statefulObject", new Object());
		if(params!=null){
			conn.setAttribute("params",params);
			for(Object o:params){
				log.info("param {}",String.valueOf(o)); 
			}
		}

		//to reject a client, call rejectClient();
		//The Thread local will not return from this call.
		//rejectClient();
		//NOTE: rejectClient(reason)	is not supported by mobile.	
		//you can also reject the client by returning false.
		return true;
	}
	/**
	 * Called when the client disconnects. This is where clean-up of client associated objects should occur.
	 */
	public void appDisconnect(IConnection conn){
		//If you have a stateful object, release it here.
		if(conn.hasAttribute("statefulObject")){
			@SuppressWarnings("unused")
			Object state = conn.getAttribute("statefulObject");
			//Do something with it.
			// state.release();
			conn.removeAttribute("statefulObject");
		}
	}
	/**
	 * If the client context uri is deeper than the top level application name,
	 * room join will be called for each sub-scope below it.
	 * proto://domain/app_name/room_name/...
	 */
	public boolean roomJoin(IClient client,IScope room){
		//Room Join events pass in the IClient rather than the IConnection.
		//To get the associated IConnection from the IClient, iterate the set.
		Iterator<IConnection> iterator = client.getConnections().iterator();
		while(iterator.hasNext()){
			IConnection theReference = iterator.next();
			if(theReference.hasAttribute("statefulObject")){
				@SuppressWarnings("unused")
				Object theState = theReference.getAttribute("statefulObject");
				//do Something with it.
				//theState.currentRoom = room.getName();
			}
		}

		//Another way to get the IConnection inside a method call is to use Red5 static methods.
		IConnection connection = Red5.getConnectionLocal();
		if(connection.hasAttribute("statefulObject")){
			@SuppressWarnings("unused")
			Object theState = connection.getAttribute("statefulObject");
			//do Something with it.
			//theState.currentRoom = room.getName();
		}
		return true;
	}
	/**
	 * Called when a client begins to publish media or data.
	 */
	public void streamBroadcastStart(IBroadcastStream stream){
		log.info("streamBroadcastStart "); 
		IConnection connection = Red5.getConnectionLocal(); 
		if(connection !=null &&  stream!=null){
			connection.setAttribute("streamStart", System.currentTimeMillis());
			connection.setAttribute("streamName", stream.getPublishedName());
		}
	}

	@SuppressWarnings("unused")
	public void streamSubscriberStart(ISubscriberStream stream){
		log.info("streamSubscriberStart "); 
		String streamName = stream.getBroadcastStreamPublishName();
		IConnection conn = Red5.getConnectionLocal();
		if(conn.hasAttribute("userId")){
			String userId=conn.getAttribute("userId").toString();
			String broadcastId=conn.getAttribute("broadcastId").toString();
			String type=conn.getAttribute("type").toString();
			String age=conn.getAttribute("sessionAge").toString();

			// broadcastId vs actual name

			//Should a client be rejected if choosing different stream than parameters authorized?
			//if(!streamName.equals(broadcastId)){//for demo simplicity sake, lets force it.
			//	conn.setAttribute("broadcastId", streamName);
			//}

			//lets reset age for stat accuracy
			
		}
		conn.setAttribute("broadcastId", streamName);
		conn.setAttribute("sessionAge",  System.currentTimeMillis()); 
		
	}

	public void streamSubscriberClose(ISubscriberStream stream){
		log.info("streamSubscriberClose "); 

		//this should match broadcastId
		String streamName = stream.getBroadcastStreamPublishName();


		IConnection conn = Red5.getConnectionLocal();
		if(conn.hasAttribute("userId")){
			String userId=conn.getAttribute("userId").toString();
			String broadcastId=conn.getAttribute("broadcastId").toString();
			String type=conn.getAttribute("type").toString();
			String age=conn.getAttribute("sessionAge").toString();		
			Long ageComputeable = Long.valueOf(age);
			long duration = System.currentTimeMillis() -ageComputeable;

			log.info("User id {}  watched broadcast {}  type:{} for {} seconds.",new Object[]{userId,broadcastId,type,duration/1000.0f});
		}else{
			String age=conn.getAttribute("sessionAge").toString();		
			Long ageComputeable = Long.valueOf(age);
			long duration = System.currentTimeMillis() -ageComputeable;

			log.info("unknown user watched broadcast {} for {} seconds.",new Object[]{streamName,duration/1000.0f});
		}
	}

	public void sendMessageToPublisher(Map<Object,Object> message){
		IConnection conn = Red5.getConnectionLocal();
		String thePublisher=null;
		if(conn.hasAttribute("broadcastId")){
			thePublisher = conn.getAttribute("broadcastId").toString(); 
		}else{//not a subscriber yet
			return;
		}
		
		Iterator<IConnection> inter = conn.getScope().getClientConnections().iterator();
		while(inter.hasNext()){
			IConnection client = inter.next();
			if(client.hasAttribute("streamName")){
				String theStream = client.getAttribute("streamName").toString(); 
				//this is a publisher. we set this above.
				if(theStream.equals(thePublisher) && client instanceof IServiceCapableConnection){
					((IServiceCapableConnection) client).invoke("subscriberMessage", new Object[]{message}); 
					log.info("sent message");
					return;				
				}
			}
		}
		
	}

	public void sendMessageToPublisher(String message){
		IConnection conn = Red5.getConnectionLocal();
		String thePublisher=null;
		if(conn.hasAttribute("broadcastId")){
			thePublisher = conn.getAttribute("broadcastId").toString(); 
		}else{//not a subscriber yet
			return;
		}
		
		Iterator<IConnection> inter = conn.getScope().getClientConnections().iterator();
		while(inter.hasNext()){
			IConnection client = inter.next();
			if(client.hasAttribute("streamName")){
				String theStream = client.getAttribute("streamName").toString(); 
				//this is a publisher. we set this above.
				if(theStream.equals(thePublisher) && client instanceof IServiceCapableConnection){
					((IServiceCapableConnection) client).invoke("subscriberMessage", new Object[]{message}); 
					log.info("sent message");
					return;				
				}
			}
		}
		
	}
	
	
	public List<String> getLiveStreams() {

		Iterator<IClient> iter = scope.getClients().iterator();
		List<String> streams = new ArrayList<String>();

		THE_OUTER:while(iter.hasNext()){
			IClient client = iter.next();
			Iterator<IConnection> cset = client.getConnections().iterator();
			THE_INNER:while(cset.hasNext()){
				IConnection c = cset.next();
				if(c.hasAttribute("streamName")){
					if(!c.isConnected()){
						try{
							c.close();
							client.disconnect();
						}catch(Exception e){

						}
						continue THE_OUTER;
					}

					if(streams.contains(c.getAttribute("streamName").toString()))
						continue THE_INNER;

					streams.add(c.getAttribute("streamName").toString());
				}
			}
		}

		return streams ;
	}

	@Override
	public boolean isPublishAllowed(IScope arg0, String arg1, String arg2) {
		Object[] params = (Object[]) Red5.getConnectionLocal().getAttribute("params");
		if(params!=null){
			for(Object o:params){
				log.info("param {}",String.valueOf(o)); 
			}
		}
		return true;
	}
	@Override
	public boolean isPlaybackAllowed(IScope arg0, String arg1, int arg2,
			int arg3, boolean arg4) {
		Object[] params = (Object[]) Red5.getConnectionLocal().getAttribute("params");
		if(params!=null){
			for(Object o:params){
				log.info("param {}",String.valueOf(o)); 
			}
		}
		return true;
	}

}

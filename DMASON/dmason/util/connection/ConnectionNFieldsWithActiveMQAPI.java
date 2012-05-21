package dmason.util.connection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.jms.DeliveryMode;
import javax.jms.MessageListener;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQTopicPublisher;
import org.apache.activemq.ActiveMQTopicSession;
import org.apache.activemq.ActiveMQTopicSubscriber;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQTopic;

public class ConnectionNFieldsWithActiveMQAPI implements ConnectionWithJMS, Serializable
{
	private static Logger logger = Logger.getLogger(ConnectionNFieldsWithActiveMQAPI.class.getCanonicalName());
	
	private ActiveMQConnection connection;
	
	/**
	 * Publishers' topic session.
	 */
	private ActiveMQTopicSession pubSession;
	
	/**
	 * Address of the provider this object is connected to.
	 */
	private Address providerAddress;
	
	/**
	 * Subscribers' topic session.
	 */
	private ActiveMQTopicSession subSession;
	
	private HashMap<String,ActiveMQTopicPublisher> publishers;
	private HashMap<String, MyHashMap> contObj;
	private HashMap<String,ActiveMQTopicSubscriber> subscribers;
	private HashMap<String,ActiveMQTopic> topics;
	private MessageListener listener;
	
	/** If you're implementing Connection your program has a standard behavior after receiving:
	 * you should use only a message listener and with this constructor you can set the 'class listener'.
	 * For more complex after-receiving actions you had to customize your class or interface...
	 * @param listener
	 */
	public ConnectionNFieldsWithActiveMQAPI(MessageListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Default constructor if you're implementing ConnectionWithJMS.
	 */
	public ConnectionNFieldsWithActiveMQAPI(){}
	
	/** 
	 * Establishes a connection with an ActiveMQ provider.
	 * @return true if the connection was successfully established, false otherwise.
	 */ 
	@Override
	public boolean setupConnection(Address providerAddr)
	{
		// Create an ActiveMQConnectionFactory
		String strAddr = "tcp://" + providerAddr.getIPaddress() + ":" + providerAddr.getPort();
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(strAddr);
		try
		{
			// Use the ActiveMQConnectionFactory to get an ActiveMQConnection object
			connection = (ActiveMQConnection) factory.createTopicConnection();
			// Create a topic session for publishers
			pubSession = (ActiveMQTopicSession) connection.createTopicSession(false, ActiveMQTopicSession.AUTO_ACKNOWLEDGE);
			// Create a topic session for subscribers
			subSession = (ActiveMQTopicSession) connection.createTopicSession(false, ActiveMQTopicSession.AUTO_ACKNOWLEDGE);
			// initialize HashMaps
			publishers = new HashMap<String, ActiveMQTopicPublisher>();
			contObj = new HashMap<String, MyHashMap>();
			subscribers = new HashMap<String, ActiveMQTopicSubscriber>();
			topics = new HashMap<String, ActiveMQTopic>();
			// Enable the in-bound flow of messages
			providerAddress = providerAddr;
			connection.start();
			return true;
		}catch (Exception e) {
			logger.severe("Unable to create a connection with the provider at address " + strAddr);
			//e.printStackTrace();
			return false;
		}
		
	}
	
	public Address getAdress()
	{
		return providerAddress;
	}

	@Override
	public boolean subscribeToTopic(String topicName) throws Exception{
		try
		{
			subscribers.put(topicName, (ActiveMQTopicSubscriber) subSession.createSubscriber(subSession.createTopic(topicName)));
			return true;
		} catch (Exception e) {
			logger.severe("Unable to subscrive to topic: " + topicName);
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public synchronized boolean publishToTopic(Serializable object, String topicName, String key) throws Exception
	{
		if (!topicName.equals("") || !(object == null))
		{
			MyHashMap mh = contObj.get(topicName);
			mh.put(key, (Object)object);
			contObj.put(topicName, mh);
			if(mh.isFull())
			{				
				ActiveMQObjectMessage msg ;
				try
				{				
					msg = (ActiveMQObjectMessage) pubSession.createObjectMessage(contObj.get(topicName));
					publishers.get(topicName).publish(topics.get(topicName), msg);
					MyHashMap mm = new MyHashMap(mh.NUMBER_FIELDS);
					contObj.put(topicName, mm);
					return true;
				} catch (Exception e) {
					logger.severe("Can't publish:" + "\n"
							+ "    topicName: " + topicName          + "\n"
							+ "    key      : " + key                + "\n"
							+ "    object   : " + object.toString() );
					e.printStackTrace();
					return false;
				}
			}	
		}
		
		return false;
	}

	/** 
	 * Allows to asynchronously receive updates, using a MessageListener
	 * that intercept the message as soon it is published.
	 * Since in a large number peer simulations we would need lots of
	 * subscribers, we associate a single MessageListener to each subscriber.
	 */
	@Override
	public boolean asynchronousReceive(String key){
		try {
			subscribers.get(key).setMessageListener(listener);
			return true;
		} catch (Exception e) {
			logger.severe("Failed to enable asynchronous reception... probably no message listener set.");
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * We extend Connection with ConnectionWithJMS only if we need a 
	 * customized listener for every (or many) topic.
	 * @oaram key A string associated to the object to receive.
	 * @param listener The listener to run when an object of type <code>key</code> is received. 
	 */
	@Override
	public boolean asynchronousReceive(String key, MyMessageListener listener)
	{
		try 
		{
			subscribers.get(key).setMessageListener(listener);
			return true;
		} catch (Exception e) {
			logger.severe("Failed to enable asynchronous reception.");
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Given a string, creates a topic's identifier, referring to a
	 * physical topic on the provider. Also creates a publisher, because
	 * when a peer creates a topic, certainly it will publish on it.
	 */
	@Override
	public boolean createTopic(String topicName, int numFields) throws Exception {
		try
		{
			ActiveMQTopic topic = new ActiveMQTopic(topicName);
			topics.put(topicName,topic);
			contObj.put(topicName, new MyHashMap(numFields));
			ActiveMQTopicPublisher p = (ActiveMQTopicPublisher) pubSession.createPublisher(topic);
			p.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			publishers.put(topicName,p);
			return true;
		} catch (Exception e) {
			logger.severe("Unable to create topic: " + topicName);
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public ArrayList<String> getTopicList() throws Exception
	{
		// The list is retrieved using a DestinationSource object
		DestinationSource provider = connection.getDestinationSource();
		Set<ActiveMQTopic> topics = provider.getTopics();
		ArrayList<String> list = new ArrayList<String>();
		Iterator<ActiveMQTopic> iter = topics.iterator();
		while(iter.hasNext()){ 
			String topic = iter.next().getTopicName();
				list.add(topic);
		}
		return list;
	}
	
	@Override
	public void setTable(HashMap table)
	{	
	}
}

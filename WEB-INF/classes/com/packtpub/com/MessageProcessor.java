package com.packtpub.com;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jms.*;//JMS interfaces 
import javax.naming.InitialContext;


/**
 * @author Steve Robinson
 * 
 */
public class MessageProcessor extends HttpServlet implements Servlet {

    static final long serialVersionUID=0; 
	private static InitialContext initialContext = null;
	private static boolean initialized = false;
	private QueueConnectionFactory qcf = null;
	private QueueConnection conn = null;
	private QueueSession session = null;
	private Queue queue = null;
	private QueueReceiver receiver = null;
	private QueueSender sender = null;

	public void init() throws ServletException {

		super.init();
		
		try {

			if (initialized == false) {

				// Get JNDI intial context
				initialContext = new InitialContext();

				// Flag as initialized
				initialized = true;

			}

		} catch (Throwable t) {

			System.out.println(t.getMessage());

		}

		
	}

	/**
	 * Outputs the results to the user's browser.
	 * 
	 */
	private void printHTML(StringBuffer strBuffer, HttpServletResponse resp) {

		try {

			// Setup output stream, print HTML, then close
			resp.setContentType("text/plain");
			PrintWriter out = resp.getWriter();
			out.println(strBuffer.toString());
			out.close();

		} catch (Throwable t) {

			// Log event
			System.out.println(t.getStackTrace());

		}

	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		// Send to do post
		doPost(req, resp);

	}

	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {

			//Get type of form action ie produce (send) or consume(receive)
			String formAction = req.getParameter("hReqType").trim();
			System.out.println(formAction);
			// Process action
			if (formAction.equals("Put")) {
				produceMsg(req, resp);
			} else if (formAction.equals("Get")) {
				consumeMsg(req, resp);
			} else {
				throw new Exception("That is not a valid option!");
			}

		} catch (Throwable t) {

			System.out.println(t.getMessage());

			//	Create the results
			StringBuffer strBuffer = new StringBuffer();
			strBuffer.append("ERROR: In doPost() Routine\n");
			printHTML(strBuffer, resp);

		}

	}

	/**
	 * Handles a request to get a message from a queue.
	 * 
	 */
	private void consumeMsg(HttpServletRequest req, HttpServletResponse resp) throws Throwable {

		// Prepare the results
		StringBuffer strBuffer = new StringBuffer();
		strBuffer.append("INFO: Consuming a message\n");

		// Get parameters
		String QCF = req.getParameter("txtQCF").trim();
		String Queue = req.getParameter("txtQueue").trim();
		
		// Receive the message from the Queue destination
		String messageText = getMsg(QCF, Queue);

		// Create the results
		if (messageText == null) {
			strBuffer.append("INFO: No message found on queue!\n");
		} else {
			strBuffer.append("INFO: Message found on queue.\n");
			strBuffer.append("MESSAGE:\n" + messageText + "\n");
		}

		// Output the results
		printHTML(strBuffer, resp);
	}

	/**
	 * Handles a request to put a message in a queue.
	 *  
	 */
	private void produceMsg(HttpServletRequest req, HttpServletResponse resp) throws Throwable {

		// Prepare the results
		StringBuffer strBuffer = new StringBuffer();
		strBuffer.append("INFO: Producing a message\n");

		// Get parameters
		String QCF = req.getParameter("txtQCF").trim();
		String Queue = req.getParameter("txtQueue").trim();
		String messageText = req.getParameter("txtMessage").trim();

		
		// Send the message
		String messageID = putMsg(QCF, Queue, 1,messageText);

		// print HTML information
		strBuffer.append("MESSAGE:\n" + messageText);
		strBuffer.append("\n\nINFO: Message put on queue.\n");
		strBuffer.append("\nJMS Message ID=" + messageID);

		// Output the results
		printHTML(strBuffer, resp);
	}
	
	public String getMsg(String getQueueConnectionFactory, String getQueueDestination) throws Throwable {

		// Call initialize
		init();

		try {

			// Lookup the queue connection factory from the initial context.
			System.out.println("Getting QCF");
			//qcf = (QueueConnectionFactory) initialContext.lookup(getQueueConnectionFactory);
			//the above code is doing a direct jndi lookup, what I want is an indirect lookup
			//using the java:comp/env context
			qcf = (QueueConnectionFactory) initialContext.lookup("java:comp/env/"+getQueueConnectionFactory);
			System.out.println("QCF initial Conext Found");
			System.out.println("QCF initial Conext Found");

			// Create a new queue connection from the queue connection factory.
			System.out.println("Create Connection to QCF");
			conn = qcf.createQueueConnection();
			System.out.println("Created Connection to QCF");

			// Start the connection so we can receive messages
			System.out.println("Starting Connection");
			conn.start();
			System.out.println("Connection started");


			/*Create a new queue session from the queue connection, the session should not be transacted and should use 
			  automatic message acknowledgement.
			 */
			System.out.println("Creating a non transactional Queue Session");
			session = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
			System.out.println("Queue Session: Created");
			

			// Lookup the queue to be used to send and receive  messages from the initial context
			System.out.println("Looking up the Queue Destination");
			queue = (Queue) initialContext.lookup("java:comp/env/"+getQueueDestination);
			System.out.println("Connection established to Queue Destination");

			// Create a new queue receiver using the queue session
			System.out.println("Creating a Receiver");
			receiver = session.createReceiver(queue, null);
			System.out.println("Created a Receiver");

			// Receive the message
			System.out.println("Receiver Message");
			Message receivedMessage = receiver.receiveNoWait();
			System.out.println("Message Received");

			// Check if a message was received
			if (receivedMessage == null) {

				System.out.println("No Messages on queue!");
				

				return null;
			}

			// Get the message ID
			String messageID = receivedMessage.getJMSMessageID().trim();

			// Cast the message to a text message
			TextMessage textMessage = (TextMessage) receivedMessage;

			// Display Data
			System.out.println("Message ID: " + messageID);
			System.out.println("Message text:\n" + textMessage.getText());
			System.out.println("Entire Message content:\n " + textMessage.toString());

			return textMessage.getText().trim();
			
		} catch (Throwable t) {

			//Print Error and Stack Trace
			System.out.println(t.getMessage());
			
			//Throw again
			throw t;

		}

	}
	
	public String putMsg(String putQueueConnectionFactory,
			String putQueueDestination, int putPriority, String putText)
			throws Throwable {

		// Call initialize
		init();

		try {
			System.out.println("Getting QCF");
			// Lookup the queue connection factory from the initial context
			//qcf = (QueueConnectionFactory) initialContext.lookup(putQueueConnectionFactory);
			//the above code is doing a direct jndi lookup, what I want is an indirect lookup
			//using the java:comp/env context
			qcf = (QueueConnectionFactory) initialContext.lookup("java:comp/env/"+putQueueConnectionFactory);
			System.out.println("QCF initial Conext Found");

			System.out.println("Create Connection to QCF");
			// Create a new queue connection from the queue connection factory.
			conn = qcf.createQueueConnection();
			System.out.println("Connection established to QCF");
			

			/*Create a new queue session from the queue connection, the session should not be transacted and should use 
			  automatic message acknowledgement.
			 */
			System.out.println("Creating a non transactional Queue Session");
			session = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
			System.out.println("Queue Session: Created");

			// Lookup the queue to be used to send messages from the initial
			// context
			System.out.println("Looking up the Queue Destination");
			queue = (Queue) initialContext.lookup("java:comp/env/"+putQueueDestination);
			System.out.println("Queue object found");

			// Create a new queue sender using the queue session, the sender
			// should be created to send messages to the queue q
			System.out.println("Creating a sender");
			sender = session.createSender(queue);
			System.out.println("Sender created");
			

			// Create a text message using the queue session.
			System.out.println("Creating a TextMessage Object");
			TextMessage textMessage = session.createTextMessage();
			System.out.println("Created TextMessage object");
			System.out.println("Putting message String");
			textMessage.setText(putText);
			System.out.println("Sending message");
			sender.send(textMessage, DeliveryMode.PERSISTENT, putPriority, 0);
			System.out.println("Sent message");

			// Get the message ID
			System.out.println("Getting Message ID");
			String messageID = textMessage.getJMSMessageID().trim();
			System.out.println("MessageID="+messageID);

			// Return the message ID
			return messageID;

		} catch (Throwable t) {

			//Print Error and Stack Trace
			System.out.println(t.getMessage());
			
			//Throw again
			throw t;	

		}

	}

}

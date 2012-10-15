/**
 * Copyright 2012 UniversitÓ degli Studi di Salerno
 

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

package dmason.util.SystemManagement;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Scanner;

import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import dmason.util.connection.Address;
import dmason.util.connection.ConnectionNFieldsWithActiveMQAPI;
import dmason.util.exception.NoDigestFoundException;

/**
 * Executable, GUI version worker.
 *
 *@author Mario Fiore Vitale (reconnection,reset simulation, update)
 */
public class StartWorkerWithGui extends JFrame implements StartWorkerInterface , Observer
{	
	private static final long serialVersionUID = 1L;
	
	private static final String version = "2.0";
	
	public boolean START = false;
	
	//private static final long SCHEDULE_INTERVAL = 10+1000L;//2 * 60 * 1000L;
	//private static final long SCHEDULE_DELAY = 10 * 1000L;
	//private java.util.Timer timer = new java.util.Timer();
	
	/**
	 * Connection with the provider.
	 */
	private ConnectionNFieldsWithActiveMQAPI connection;
	
	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
	// Generated using JFormDesigner Evaluation license - aaaa aaaa
	private JScrollPane scrollPane1;
	public JTextArea textArea;
	private JButton btnConnect;
	private JLabel label1;
	private JLabel label2;
	private JComboBox cmbPort;
	private JLabel label3;
	public JLabel labelnumber;
	private JLabel lblLogo;
	private JComboBox cmbIp;
	private static boolean updated;
	private static boolean autoStart;
	
	private static String myTopic;
	private static String ip;
	private static String port;
	

	private static Logger logger;
	
	
	private String digest;

	public StartWorkerWithGui(boolean start, boolean up, String topic,String ipCS,String portCS)
	{
		initComponents();

		connection = new ConnectionNFieldsWithActiveMQAPI();

		connection.addObserver(this);
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		
		String path;
		// Get the path from which worker was started
		try {
			path = URLDecoder.decode(StartWorkerWithGui.class.getProtectionDomain().getCodeSource().getLocation().getFile(),"UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			path = "";
		}

		logger.debug("Path: "+path);

		if(path.contains(".jar")) //from jar
		{

			File jarfile = new File(path);

			Digester dg = new Digester(DigestAlgorithm.MD5);

			try {
				InputStream in = new FileInputStream(path);

				digest = dg.getDigest(in);

				String fileName = FilenameUtils.removeExtension(jarfile.getName());
				//save properties to project root folder
				dg.storeToPropFile(fileName+".hash");

			} catch (IOException ex) {
				ex.printStackTrace();
			} catch (NoDigestFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		else
		{ // not from jar
			digest = null;
		}

		autoStart = start;
		updated = up;
		myTopic = topic;
		ip = ipCS;
		port = portCS;

		if(autoStart)
			connect();

		//initSchedule();
	}

	public static void main(String[] args)
	{
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		 
	    //
	    // Get name representing the running Java virtual machine.
	    // It returns something like 6460@AURORA. Where the value
	    // before the @ symbol is the PID.
	    //
	    String jvmName = bean.getName();
	    
	    //Used for log4j properties
		System.setProperty("logfile.name",jvmName);
		
		
		logger = Logger.getLogger(StartWorker.class.getCanonicalName());
		logger.debug("StartWorker "+version);
		
		String topic = "";
		String ip = null;
		String port = null;
		autoStart = false;
		updated = false;
		
		if(args.length == 3)
		{
			autoStart = true;
			updated = true;
			topic = args[0];
			ip = args[1];
			port = args[2];
			
		}
		
		/*if(args.length == 2 && args[0].equals("auto"))
		{	autoStart = true;
			updated = true;
			topic = args[1];
		}
		if(args.length == 1 && args[0].equals("auto"))
		{	autoStart = true;
		}*/
		 new StartWorkerWithGui(autoStart,updated,topic,ip,port);
	}

	private void initComponents() {
		scrollPane1 = new JScrollPane();
		textArea = new JTextArea();
		textArea.setEditable(false);
		btnConnect = new JButton();
		label1 = new JLabel();
		label2 = new JLabel();
		cmbPort = new JComboBox();
		
		label3 = new JLabel();
		labelnumber = new JLabel();
		lblLogo = new JLabel();
		cmbIp = new JComboBox();
		
		//======== this ========
		setTitle("D.MASON WORKER "+version);
		Container contentPane = getContentPane();
	
		//======== scrollPane1 ========
		{
			scrollPane1.setViewportView(textArea);
		}
	
		//---- btnConnect ----
		btnConnect.setText("Connect");
		btnConnect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				btnConnectOnClick(e);
			}
		});
	
		//---- label1 ----
		label1.setText("Server IP:");
	
		//---- label2 ----
		label2.setText("Server port:");
	
		//---- label3 ----
		label3.setText("");
	
		//---- labelnumber ----
		labelnumber.setText("");
	
		//---- label4 ----
		lblLogo.setText("Distributed Mason 'failed to load image'");
		lblLogo.setIcon(new ImageIcon(ClassLoader.getSystemClassLoader().getResource("dmason/resource/image/carmineworker.png")));
	
		//---- comboBox ----
		cmbIp.setEditable(true);
		cmbPort.setEditable(true);
	
		Scanner in=new Scanner(ClassLoader.getSystemClassLoader().getResourceAsStream("dmason/resource/file/urlworker"));
		while(in.hasNext())
		{
			String line=in.nextLine();
			String[] args=line.split(":");
			cmbIp.addItem(args[0]);
			cmbPort.addItem(args[1]);
		}
		
		cmbIp.setSelectedIndex(0);
		cmbPort.setSelectedIndex(0);
	
	
		GroupLayout contentPaneLayout = new GroupLayout(contentPane);
		contentPane.setLayout(contentPaneLayout);
		contentPaneLayout.setHorizontalGroup(
			contentPaneLayout.createParallelGroup()
				.addGroup(contentPaneLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(contentPaneLayout.createParallelGroup()
						.addComponent(lblLogo, GroupLayout.PREFERRED_SIZE, 400, GroupLayout.PREFERRED_SIZE)
						.addGroup(contentPaneLayout.createParallelGroup()
							.addGroup(contentPaneLayout.createSequentialGroup()
								.addGroup(contentPaneLayout.createParallelGroup()
									.addComponent(label2)
									.addComponent(label1))
								.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
								.addGroup(contentPaneLayout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
									.addComponent(cmbPort)
									.addComponent(cmbIp, 0, 119, Short.MAX_VALUE))
								.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
								.addGroup(contentPaneLayout.createParallelGroup()
									.addGroup(contentPaneLayout.createSequentialGroup()
										.addGap(78, 78, 78)
										.addComponent(btnConnect))
									.addGroup(contentPaneLayout.createSequentialGroup()
										.addGap(46, 46, 46)
										.addComponent(label3)
										.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(labelnumber, GroupLayout.PREFERRED_SIZE, 69, GroupLayout.PREFERRED_SIZE))))
							.addGroup(GroupLayout.Alignment.TRAILING, contentPaneLayout.createSequentialGroup()
								.addGap(9, 9, 9)
								.addComponent(scrollPane1, GroupLayout.PREFERRED_SIZE, 366, GroupLayout.PREFERRED_SIZE)
								.addGap(462, 462, 462))))
					.addGap(20, 20, 20))
		);
		contentPaneLayout.setVerticalGroup(
			contentPaneLayout.createParallelGroup()
				.addGroup(contentPaneLayout.createSequentialGroup()
					.addContainerGap(21, Short.MAX_VALUE)
					.addComponent(lblLogo, GroupLayout.PREFERRED_SIZE, 102, GroupLayout.PREFERRED_SIZE)
					.addGap(18, 18, 18)
					.addGroup(contentPaneLayout.createParallelGroup()
						.addComponent(label1)
						.addGroup(contentPaneLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
							.addComponent(btnConnect)
							.addComponent(cmbIp, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(contentPaneLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
						.addGroup(contentPaneLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
							.addComponent(label2)
							.addComponent(cmbPort, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
						.addGroup(contentPaneLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
							.addComponent(label3)
							.addComponent(labelnumber)))
					.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					.addComponent(scrollPane1, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
					.addGap(16, 16, 16))
		);
		setSize(410, 495);
		setLocationRelativeTo(getOwner());
	
	}

	/*private void initSchedule() {
		
		timer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				
				if(!connection.isConnected())
					System.out.println("not connected");
				else
					System.out.println("Connected");
			}
		}, SCHEDULE_DELAY, SCHEDULE_INTERVAL);
		
	}*/

	private void btnConnectOnClick(ActionEvent e) {
		
		connect();
		
	}
	
	private void connect()
	{
		
		try {
			if(autoStart)
				connection.setupConnection(new Address(ip, port));
			else
				connection.setupConnection(new Address((String)cmbIp.getSelectedItem(), (String)cmbPort.getSelectedItem()));
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if(!updated)
			new PeerDaemonStarter(connection, this,version,digest);
		else // the worker was updated and then reconnected to the same topic
			new PeerDaemonStarter(connection, this,myTopic,version,digest);
		
		cmbIp.setEditable(false);
		cmbPort.setEditable(false);
	}
	@Override
	public void writeMessage(String message) {
		textArea.append(message);
	}

	// used for show change about connection reconnection
	// Observable is: ConnectionNFieldsWithActiveMQAPI
	@Override
	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub
		
		if(connection.isConnected())
			textArea.append("Connection restabilished\n");
		
		else
			textArea.append("Connection refused\n");
	}
	
	public void exit()
	{
		System.out.println("Quitting");
		System.exit(EXIT_ON_CLOSE);
	}
}

package it.isislab.dmason.experimentals.systemmanagement.master;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class Client {

	public static void main(String[] args) {


		String serverIP = "127.0.0.1";
		int serverPort = 3248;
		String fileOutput = "/home/miccar/Scrivania/out.jar";
		byte[] aByte = new byte[1];
		int bytesRead;

		Socket clientSocket = null;
		InputStream is = null;

		try {
			clientSocket = new Socket( serverIP , serverPort );
			is = clientSocket.getInputStream();
		} catch (IOException ex) {
			// Do exception handling
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		if (is != null) {

			FileOutputStream fos = null;
			BufferedOutputStream bos = null;
			try {
				System.out.println("Creating file");
				
				File v=new File(fileOutput);
			    if(v.exists()){
			    	v.delete();
			    	v=new File(fileOutput);
			    } 
				v.setWritable(true);
				v.setExecutable(true);
				fos = new FileOutputStream( v );
				bos = new BufferedOutputStream(fos);
				bytesRead = is.read(aByte, 0, aByte.length);

				System.out.println("Writing on file");
				do {
					baos.write(aByte);
					bytesRead = is.read(aByte);
				} while (bytesRead != -1);

				bos.write(baos.toByteArray());
				bos.flush();
				bos.close();
				System.out.println("End writing");
				clientSocket.close();
			} catch (IOException ex) {
				// Do exception handling
			}
		}
	}


}

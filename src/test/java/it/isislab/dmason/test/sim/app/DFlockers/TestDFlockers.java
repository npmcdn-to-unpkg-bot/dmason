/**
 * Copyright 2012 Universita' degli Studi di Salerno


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
package it.isislab.dmason.test.sim.app.DFlockers;
/*
 * THIS CLASS HAS BEEN USED FOR TESTING PURPOSES IN THE BEGINNINGS,
 */
import static org.junit.Assert.assertEquals;
import it.isislab.dmason.exception.DMasonException;
import it.isislab.dmason.sim.field.DistributedField2D;
import it.isislab.dmason.tools.batch.data.GeneralParam;
import it.isislab.dmason.util.connection.ConnectionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.junit.Test;

/**
 * 
 * @author Michele Carillo
 * @author Ada Mancuso
 * @author Dario Mazzeo
 * @author Francesco Milone
 * @author Francesco Raia
 * @author Flavio Serrapica
 * @author Carmine Spagnuolo
 *
 */
public class TestDFlockers {
	
	class ComparatoreFloker implements Comparator<DFlocker>{

		public ComparatoreFloker() {
			// TODO Auto-generated constructor stub
		}
		@Override
		public int compare(DFlocker o1, DFlocker o2) {
			// TODO Auto-generated method stub
			
			return o1.id.compareTo(o2.id);
		}
		
	}

	private static int numSteps = 1000; //only graphicsOn=false
	private static int rows = 2; //number of rows
	private static int columns = 2; //number of columns
	private static int MAX_DISTANCE=1; //max distance
	private static int NUM_AGENTS=6000; //number of agents
	private static int WIDTH=300; //field width
	private static int HEIGHT=300; //field height
	private static String ip="127.0.0.1"; //ip of activemq
	private static String port="61616"; //port of activemq

	//don't modify this...
	//private static int MODE = (rows==1 || columns==1)? DistributedField2D.HORIZONTAL_DISTRIBUTION_MODE : DistributedField2D.SQUARE_DISTRIBUTION_MODE; 
	//rivate static int MODE = (rows==1 || columns==1)? DistributedField2D.HORIZONTAL_BALANCED_DISTRIBUTION_MODE : DistributedField2D.SQUARE_BALANCED_DISTRIBUTION_MODE;
	private static int MODE = DistributedField2D.UNIFORM_PARTITIONING_MODE;


	ArrayList<DFlocker> initial_agents = new ArrayList<DFlocker>();
	ArrayList<DFlocker> end_agents = new ArrayList<DFlocker>();
	
	@Test
	public void testFlockersWithDContinuonus2D() throws DMasonException, InterruptedException {


		class worker extends Thread
		{

			private DFlockers ds;
			public worker(DFlockers ds) {
				this.ds=ds;
				ds.start();
				
				
			}
			@Override
			public void run() {
				int i=0;
				while(i!=numSteps)
				{
					//	System.out.println(i);
					ds.schedule.step(ds);
					
					i++;
					if(i==1){
						synchronized (initial_agents) {
							for(Object d:ds.flockers.allObjects)
							{
								DFlocker df=(DFlocker) d;

								if(ds.flockers.verifyPosition(df.getPos())){								
									initial_agents.add(df);
								}
							}

						}
					}
					else if(i==numSteps-1){
						synchronized (end_agents) {
							for(Object d:ds.flockers.allObjects)
							{
								DFlocker df=(DFlocker) d;
								if(ds.flockers.verifyPosition(df.getPos())){								
									end_agents.add(df);
								}

							}
						}
					}
				}
				
			}
		}

		ArrayList<worker> myWorker = new ArrayList<worker>();
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < columns; j++) {

				GeneralParam genParam = new GeneralParam(WIDTH, HEIGHT, MAX_DISTANCE, rows,columns,NUM_AGENTS, MODE,ConnectionType.fakeUnitTestJMS); 
				genParam.setI(i);
				genParam.setJ(j);
				genParam.setIp(ip);
				genParam.setPort(port);
			
					DFlockers sim = new DFlockers(genParam); 
					worker a = new worker(sim);
					myWorker.add(a);
				
			}
		}

		for (worker w : myWorker) {
			w.start();
		}
		for (worker w : myWorker) {
			w.join();
		}
		
		//verifico la stessa dimensione
		assertEquals(initial_agents.size(), end_agents.size());
		
		//verifico se gli array siano uguali
		//System.out.println(initial_agents); 
		
		ComparatoreFloker c=new ComparatoreFloker();
		
		Collections.sort(initial_agents,c);
		Collections.sort(end_agents,c);
		
		for(int i=0;i<initial_agents.size();i++){
			assertEquals(initial_agents.get(i).id, end_agents.get(i).id);
		}
		
	}
}
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

package it.isislab.dmason.sim.engine;


import it.isislab.dmason.experimentals.sim.field.support.field2D.loadbalanced.UpdatePositionInterface;
import it.isislab.dmason.experimentals.sim.field.support.loadbalancing.LoadBalancingInterface;
import it.isislab.dmason.experimentals.sim.field.support.loadbalancing.MyCellInterface;
import it.isislab.dmason.experimentals.util.visualization.globalviewer.ViewerMonitor;
import it.isislab.dmason.nonuniform.QuadTree;
import it.isislab.dmason.nonuniform.TreeObject;
import it.isislab.dmason.sim.field.CellType;
import it.isislab.dmason.sim.field.DistributedField2D;
import it.isislab.dmason.sim.field.DistributedField2DLB;
import it.isislab.dmason.sim.field.DistributedFieldNetwork;
import it.isislab.dmason.sim.field.continuous.DContinuousNonUniform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Double2D;


/**
 * The Distributed Schedule for Distributed Mason with multiple fields
 * It's necessary for the synchronization of multiply environment 
 * for each step.
 * @param <E> the type of coordinates
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
public class DistributedMultiSchedule<E> extends Schedule
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public ArrayList<DistributedField2D> fields2D;
	public ArrayList<DistributedFieldNetwork> fieldsNetwork;
	Steppable zombie = null;

	private HashMap<String, String> peers;
	private boolean split;
	private boolean merge;
	private int numAgents;
	public int externalAgents;
	private int numExt;
	private DistributedState state;
	private HashMap<String, ArrayList<MyCellInterface>> h;

	//thresholds for the split and the merge of the cell 
	private double thresholdSplit;
	private double thresholdMerge;


	private final ReentrantLock lock = new ReentrantLock();
	private final Condition block = lock.newCondition();

	public HashMap<String,Object> deferredUpdates=new HashMap<String, Object>();

	/**
	 * Count how many viewers are active on this schedule. Using this
	 * subclass allows to increment/decrement the counter atomically.
	 */
	public class CounterViewer
	{
		private int count = 0;
		public synchronized void increment() { count++;	}
		public synchronized void decrement(){ count--; }
		public synchronized int getCount(){	return count; }
	}

	/**
	 * Number of the viewers active on this schedules.
	 */
	public CounterViewer numViewers = new CounterViewer();

	public boolean isEnableZoomView = false;

	public ViewerMonitor monitor = new ViewerMonitor();

	/**
	 * Counts the number of threads that have done synchronizing.
	 */
	private int n = 0;

	/**
	 * Every region in this worker, after synchronization, will store a
	 * boolean in this ArrayList stating if the synchronization itself
	 * was successful or not.
	 */
	private ArrayList<Boolean> synchResults = new ArrayList<Boolean>(); 
	private ArrayList<Boolean> synchNetworkResults = new ArrayList<Boolean>(); 
	//NON UNIFORM
	private QuadTree tree_partitioning;
	private HashMap<RemotePositionedAgent<E>, DistributedField2D<E>> map_agents_on_fields=new HashMap<RemotePositionedAgent<E>, DistributedField2D<E>>();
	private HashMap<RemotePositionedAgent<E>,E> map_agents_on_position=new HashMap<RemotePositionedAgent<E>,E>();

	public DistributedMultiSchedule() {

		fields2D = new ArrayList<DistributedField2D>();
		fieldsNetwork = new ArrayList<DistributedFieldNetwork>();
		peers = new HashMap<String, String>();
		split = false;
		merge = false;
		numAgents = 0;
		externalAgents = 0;
		numExt = 0;

		thresholdSplit=3;
		thresholdMerge=1.5;
		// profiling code

		time = 0;
		// end profiling code
	}
	public DistributedMultiSchedule(int tot_agents,int number_of_cells, double width, double height,double aoi) {

		fields2D = new ArrayList<DistributedField2D>();
		fieldsNetwork = new ArrayList<DistributedFieldNetwork>();
		peers = new HashMap<String, String>();
		split = false;
		merge = false;
		numAgents = 0;
		externalAgents = 0;
		numExt = 0;
		thresholdSplit=3;
		thresholdMerge=1.5;
		// profiling code
		time = 0;
		// end profiling code
		tree_partitioning=new QuadTree(tot_agents/number_of_cells, 0, 0, width,height,aoi);

	}
	/**
	 * The same method of MASON scheduleOnce(Steppable event) but for non uniform partitioning
	 * @param event
	 * @param x
	 * @param y
	 * @return
	 */
	public boolean scheduleOnceNonUniform(RemotePositionedAgent<E> event,double x, double y,DistributedField2D<E> onField, E pos) {
		return  tree_partitioning.insert(event, x, y) && (map_agents_on_fields.put(event, onField)==null) && (map_agents_on_position.put(event, pos)==null);
	}
	/**
	 * Steps the schedule for each field, gathering and ordering all the items to step on the next time step (skipping
	 * blank time steps), and then stepping all of them in the decided order.
	 * Returns FALSE if nothing was stepped -- the schedule is exhausted or time has run out.
	 */
	@Override
	public synchronized boolean step(final SimState simstate)
	{
		state = (DistributedState)simstate;

		//load peers list
		if(getSteps()==0){
			int numP = (int) Math.sqrt(state.NUMPEERS);
			int z = 0;
			for (int i = 0; i < numP; i++) {
				for (int j = 0; j < numP; j++) {
					peers.put(""+z,i+"-"+j);
					z=z+3;
				}
			}
		}
		//NON UNIFORM DISTRIBUTION MODE
		if(getSteps()==0 && tree_partitioning!=null){

			tree_partitioning.partition(state.P, tree_partitioning, true);

			List<QuadTree> parts=tree_partitioning.getPartitioning(tree_partitioning);

			QuadTree myCell=parts.get(state.TYPE.pos_j);

			//PREPARAZIONE CAMPI
			for(DistributedField2D<E> f : fields2D)
			{
				f.clear();
				f.createRegions(myCell);

			}

			//ADD AGENTS TO CORRECT FIELD AND TO THE SCHEDULE OF THIS CELL
			for(TreeObject agent: myCell.getObjects())
			{
				this.scheduleOnce((Steppable) agent.obj);

				state.addToField((RemotePositionedAgent<E>) agent.obj, (Double2D)map_agents_on_position.get(agent.obj));
			}

			//CRAEZIONE DELLA COMUNICAZIONE
			state.getDistributedStateConnectionJMS().initNonUnfiromCommunication(myCell);

		}
	
		// If not already present, adds a "zombie" agent to the schedule
		// in order to prevent stopping the simulation.
		if (zombie == null)
		{ 
			zombie = new Steppable()
			{
				static final long serialVersionUID = 6330208166095250478L;
				@Override
				public void step(SimState state) { /* do nothing*/ }
			};
			this.scheduleRepeating(zombie);
		}

		synchronized (monitor)
		{
			if (monitor.isZoom)
				monitor.ZOOM = true;
			else
				monitor.ZOOM = false;
		}

		// Execute the simulation step
		super.step(state);
		deferredUpdates.clear();


		if(tree_partitioning==null) verifyBalance();


		// Create a thread for each field assigned to this worker, in order
		// to do synchronization
		for(DistributedField2D<E> f : fields2D)
		{
			MyThread t = new MyThread(f, this);
			t.start();
		}

		// Waits for every synchronization thread.
		// Note: synchronization threads will update the synchResults array
		//       as well as the n variable.
		lock.lock();
		while(n < fields2D.size()){
			try
			{
				block.await(); // Will be signaled by a thread
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		n = 0;
		lock.unlock();

		class MyNetworkThread<E> extends Thread
		{ 
			DistributedFieldNetwork<E> field;
			DistributedMultiSchedule<E> schedule;

			public MyNetworkThread(DistributedFieldNetwork<E> f, DistributedMultiSchedule<E> s)
			{
				field = f;
				schedule = s;
			} 

			@Override
			public void run()
			{ 
				// Synchronize the field, then report to the 
				// DistributedMultiSchedule if the operation was successful or not
				schedule.statusNetworkSyn(field.synchro());
			} 
		} 

		for(DistributedFieldNetwork<E> f : fieldsNetwork)
		{
			MyNetworkThread t = new MyNetworkThread(f, this);
			t.start();
		}

		lock.lock();
		while(n < fieldsNetwork.size())
		{
			try {
				block.await();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		n=0;
		lock.unlock();

		for (Boolean b : synchNetworkResults)
		{
			if (b == false)
			{
				return false;
			}
		}	

		// Check if fields did synchronize successfully
		for (Boolean b : synchResults)
		{
			if (b == false)
			{
				return false;
			}
		}	


		// If there is an active zoom synchronous monitor, wait for it 
		if(monitor.ZOOM && monitor.isSynchro)
		{
			Long currentStep = this.getSteps() - 1;
			try
			{
				monitor.awaitForAckStep(currentStep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Done
		return true;
	}

	/**
	 * This subclass is in charge of synchronizing a single region.
	 * @param <E> the type of coordinates
	 */
	class MyThread<E> extends Thread
	{ 
		DistributedField2D<E> field;
		DistributedMultiSchedule<E> schedule;

		public MyThread(DistributedField2D<E> f, DistributedMultiSchedule<E> s)
		{
			field = f;
			schedule = s;
		} 

		@Override
		public void run()
		{ 
			// Synchronize the field, then report to the 
			// DistributedMultiSchedule if the operation was successful or not
			schedule.statusSyn(field.synchro());
		} 
	} 

	/**
	 * Stores result of a field synchronization's result. This method is meant
	 * to be be called by an inner thread <code>MyThread</code> in charge of
	 * executing field synchronization.
	 * @param b <code>true</code> if the synchronization was successful, <code>false</code> otherwise.
	 */
	public void statusSyn(boolean b)
	{
		lock.lock();
		n++;	             // Increase number of threads that did synchronize
		synchResults.add(b); // Update the array of synchronization results
		block.signal();      // Signal DistributedMultiSchedule
		lock.unlock();
	}

	public void statusNetworkSyn(boolean b)
	{
		lock.lock();
		n++;	             // Increase number of threads that did synchronize
		synchNetworkResults.add(b); // Update the array of synchronization results
		block.signal();      // Signal DistributedMultiSchedule
		lock.unlock();
	}

	// Getters and setters
	public ArrayList<DistributedField2D> getFields() { return fields2D; }
	public void setFields(ArrayList<DistributedField2D> fields) { this.fields2D = fields; }
	public void addField(DistributedField2D<E> f) { fields2D.add(f); }	
	public void addNetworkField(DistributedFieldNetwork<E> f) { fieldsNetwork.add(f); }

	public void manageBalance(HashMap<Integer,UpdatePositionInterface> hashUpdatesPosition, 
			DistributedField2DLB field, CellType cellType,LoadBalancingInterface balance) {
		if(getSteps()>state.NUMPEERS)
		{
			HashMap<CellType, MyCellInterface> h =field.getToSendForBalance();

			if(state.TYPE.toString().equals(peers.get((getSteps()%(3*state.NUMPEERS))+"")) && !field.isSplitted()
					&& split)
			{
				field.prepareForBalance(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreBalance(true);			
				hashUpdatesPosition.get(MyCellInterface.UP).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.DOWN).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreBalance(true);
				hashUpdatesPosition.get(MyCellInterface.LEFT).setPreBalance(true);


			}
			else
				if(state.TYPE.toString().equals(peers.get(((getSteps()%(3*state.NUMPEERS))-1)+"")) && !field.isSplitted()
						&& field.isPrepareForBalance())
				{
					field.setIsSplitted(true);		
					field.prepareForBalance(false);
					MyCellInterface m0 = h.get(MyCellInterface.CORNER_DIAG_UP_LEFT);
					m0.setPosition(balance.calculatePositionForBalance(m0.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setMyCell(m0);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreBalance(true);

					MyCellInterface m1 = h.get(MyCellInterface.UP);
					m1.setPosition(balance.calculatePositionForBalance(m1.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.UP).setMyCell(m1);
					hashUpdatesPosition.get(MyCellInterface.UP).setPreBalance(true);

					MyCellInterface m2 = h.get(MyCellInterface.CORNER_DIAG_UP_RIGHT);
					m2.setPosition(balance.calculatePositionForBalance(m2.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setMyCell(m2);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreBalance(true);

					MyCellInterface m3 = h.get(MyCellInterface.RIGHT);
					m3.setPosition(balance.calculatePositionForBalance(m3.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.RIGHT).setMyCell(m3);
					hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreBalance(true);

					MyCellInterface m4 = h.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT);
					m4.setPosition(balance.calculatePositionForBalance(m4.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setMyCell(m4);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreBalance(true);

					MyCellInterface m5 = h.get(MyCellInterface.DOWN);
					m5.setPosition(balance.calculatePositionForBalance(m5.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.DOWN).setMyCell(m5);
					hashUpdatesPosition.get(MyCellInterface.DOWN).setPreBalance(true);

					MyCellInterface m6 = h.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT);
					m6.setPosition(balance.calculatePositionForBalance(m6.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setMyCell(m6);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreBalance(true);

					MyCellInterface m7 = h.get(MyCellInterface.LEFT);
					m7.setPosition(balance.calculatePositionForBalance(m7.getPosition()));
					hashUpdatesPosition.get(MyCellInterface.LEFT).setMyCell(m7);
					hashUpdatesPosition.get(MyCellInterface.LEFT).setPreBalance(true);
				}
				else
				{
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreBalance(false);			
					hashUpdatesPosition.get(MyCellInterface.UP).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.DOWN).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreBalance(false);
					hashUpdatesPosition.get(MyCellInterface.LEFT).setPreBalance(false);
				}

		}
	}

	public void manageMerge(HashMap<Integer,UpdatePositionInterface> hashUpdatesPosition, 
			DistributedField2DLB field, CellType cellType) {
		if(getSteps()>state.NUMPEERS)
		{
			if(state.TYPE.toString().equals(peers.get((getSteps()%(3*state.NUMPEERS))+"")) && !field.isUnited() 
					&& merge)
			{
				field.prepareForUnion(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreUnion(true);			
				hashUpdatesPosition.get(MyCellInterface.UP).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.DOWN).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreUnion(true);
				hashUpdatesPosition.get(MyCellInterface.LEFT).setPreUnion(true);


				numExt = 0;

			}
			else
				if(state.TYPE.toString().equals(peers.get(((getSteps()%(3*state.NUMPEERS))-1)+"")) && !field.isUnited()
						&& !field.isSplitted())
				{
					field.prepareForUnion(true);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreUnion(false);			
					hashUpdatesPosition.get(MyCellInterface.UP).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.DOWN).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.LEFT).setPreUnion(false);
				}
				else
				{
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setPreUnion(false);			
					hashUpdatesPosition.get(MyCellInterface.UP).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.DOWN).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setPreUnion(false);
					hashUpdatesPosition.get(MyCellInterface.LEFT).setPreUnion(false);
				}

			HashMap<Integer, MyCellInterface> cellToSend = field.getToSendForUnion();
			for(Integer s : cellToSend.keySet())
			{
				if(cellToSend.get(s)!=null)
				{
					MyCellInterface mc = cellToSend.get(s);

					hashUpdatesPosition.get(s).setUnion(true);
					hashUpdatesPosition.get(s).setMyCell(mc);
				}
			}
			for (Integer s : cellToSend.keySet()) {
				cellToSend.put(s, null);
			}
		}
	}

	private void verifyBalance(){

		double average = state.NUMAGENTS/(state.rows*state.columns);
		double splitting=thresholdSplit*average;
		double merging=thresholdMerge*average;

		if(numAgents>splitting)
		{
			split = true;
			merge = false;
		}
		else 
			if(((numAgents+externalAgents)<merging) && 
					(state.TYPE.toString().equals(peers.get((getSteps()%(3*state.rows*state.columns))+""))))
			{
				merge = true;
				split = false;
				numExt = numAgents+externalAgents;
			}
		externalAgents = 0;
	}

	public double getThresholdSplit() {
		return thresholdSplit;
	}

	public void setThresholdSplit(double thresholdSplit) {
		this.thresholdSplit = thresholdSplit;
	}

	public double getThresholdMerge() {
		return thresholdMerge;
	}

	public void setThresholdMerge(double thresholdMerge) {
		this.thresholdMerge = thresholdMerge;
	}
	public int getNumFields(){
		return fields2D.size();
	}

}

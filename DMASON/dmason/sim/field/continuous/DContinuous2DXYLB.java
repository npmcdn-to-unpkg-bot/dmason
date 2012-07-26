package dmason.sim.field.continuous;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import dmason.sim.engine.DistributedMultiSchedule;
import dmason.sim.engine.DistributedState;
import dmason.sim.engine.RemoteAgent;import dmason.sim.field.grid.RegionInteger;
import dmason.sim.field.CellType;
import dmason.sim.field.DistributedRegion;
import dmason.sim.field.Entry;
import dmason.sim.field.MessageListener;
import dmason.sim.field.Region;
import dmason.sim.field.RegionMap;
import dmason.sim.field.UpdateMap;
import dmason.sim.field.UpdatePositionDoubleField;
import dmason.sim.field.UpdatePositionInterface;
import dmason.sim.loadbalancing.LoadBalancingDoubleField;
import dmason.sim.loadbalancing.LoadBalancingInterface;
import dmason.sim.loadbalancing.MyCellDoubleField;
import dmason.sim.loadbalancing.MyCellInterface;
import dmason.util.connection.Connection;
import dmason.util.connection.ConnectionNFieldsWithActiveMQAPI;
import dmason.util.connection.ConnectionWithJMS;
import sim.engine.SimState;
import sim.util.Double2D;


/**
 *  <h3>This Field extends Continuous2D, to be used in a distributed environment. All the necessary informations 
 *  for the distribution of simulation are wrapped in this class.</h3>
 * <p> This version is for a distribution in a <i>square mode</i>.
 *  It represents the field managed by a single peer.
 *  This is an example for a square mode distribution with 9 peers (only to distinguish the regions):
 *  (for code down)
 *  <p>
 *
 *	<ul>
 *	<li>MYFIELD : Region to be simulated by peer.</li>
 *
 *	<li>LEFT_MINE, RIGHT_MINE, UP_MINE, DOWN_MINE,CORNER_MINE_LEFT_UP,CORNER_MINE_LEFT_DOWN,
 *		CORNER_MINE_RIGHT_UP,CORNER_MINE_RIGHT_DOWN :Boundaries Regions those must be simulated and sent to neighbors.</li>
 *	
 *	<li>LEFT_OUT, RIGHT_OUT, UP_OUT, DOWN_OUT, CORNER_OUT_LEFT_UP_DIAG, CORNER_OUT_LEFT_DOWN_DIAG,
 *		CORNER_OUT_RIGHT_UP_DIAG, CORNER_OUT_RIGHT_DOWN_DIAG : Boundaries Regions those must not be simulated and sent to neighbors to be simulated.</li>
 *   <li>
 *	All peers subscribes to the topic of boundary region which want the information and run a asynchronous thread
 *	to receive the updates, then publish a topic for every their border (or neighbor), that can be :
 *	<ul>
 *	<li> MYTOPIC L (LEFT BORDER)</li>
 *	<li> MYTOPIC R (RIGHT BORDER)</li>
 *	<li> MYTOPIC U (UPPER BORDER)</li>
 *	<li> MYTOPIC D (LOWER BORDER)</li>
 *
 *	<li> MYTOPIC CUDL (Corner Up Diagonal Left)</li>
 *	<li> MYTOPIC CUDR (Corner Up Diagonal Right)</li>
 *	<li> MYTOPIC CDDL (Corner Down Diagonal Left)</li>
 *	<li> MYTOPIC CDDR (Corner Down Diagonal Right)</li>
 *</ul>
 *</li>
 *	</ul></p>
 *	
 * <PRE>
 * ---------------------------------------------------------------------------------------
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |         00             |  |  |          01          |  |  |            02           |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |   CORNER DIAG           |
 * |                        |  |  |                      |  |  |  /                      |
 * |                        |  |  |                      |  |  | /                       |
 * |________________________|__|__|______UP_OUT__________|__|__|/________________________|
 * |________________________|__|__|______UP_MINE_________|__|__|_________________________|
 * |________________________|__|__|______________________|__|__|_________________________|
 * |                        |  |  |                     /|  |  |                         |
 * |                        L  L  |                    / |  R  R                         |
 * |                        E  E  |                   /  |  I  I                         |
 * |         10             F  F  |         11   CORNER  |  G  G         12              |
 * |                        T  T  |               MINE   |  H  H                         |
 * |                        |  |  |                      |  T  T                         |
 * |                        O  M  |       MYFIELD        |  |  |                         |
 * |                        U  I  |                      |  M  O                         |
 * |                        T  N  |                      |  I  U                         |
 * |________________________|__|__|______________________|__|__|_________________________|
 * |________________________|__|__|___DOWN_MINE__________|__|__|_________________________|
 * |________________________|__|__|___DOWN_OUT___________|__|__|_________________________|
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |       20               |  |  |          21          |  |  |           22            |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * |                        |  |  |                      |  |  |                         |
 * ---------------------------------------------------------------------------------------
 * </PRE>
 */

public class DContinuous2DXYLB extends DContinuous2D
{	

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ArrayList<MessageListener> listeners = new ArrayList<MessageListener>();
	private String NAME;
	private ConnectionWithJMS con = new ConnectionNFieldsWithActiveMQAPI();

	//quando ricevo cellette e ho splittato a mia volta imposto i topic diversamente delle cellette
	private boolean isSplitted;
	private boolean splitDone;;
	private boolean prepareForBalance;
	private boolean prepareForUnion;
	private boolean preUnion;
	private boolean isUnited;
	private int positionForUnion = -1;
	private boolean unionDone;

	//Serve per dividere le celle per il load Balancing
	private LoadBalancingInterface balance;
	private HashMap<Integer, UpdatePositionDoubleField<DistributedRegion<Double,Double2D>>> hashUpdatesPosition;
	private HashMap<Integer, MyCellInterface> toSendForBalance;
	private HashMap<Integer, MyCellInterface> toSendForUnion;
	private RegionDoubleLB outAgents;

	// --> only for testing
	public PrintWriter printer;
	public ArrayList<RemoteAgent<Double2D>> buffer_print=new ArrayList<RemoteAgent<Double2D>>();
	private int numAgents;
	// <--



	/**
	 * Constructor of class with paramaters:
	 * 
	 * @param discretization the discretization of the field
	 * @param width field's width  
	 * @param height field's height
	 * @param sm The SimState of simulation
	 * @param max_distance maximum shift distance of the agents
	 * @param i i position in the field of the cell
	 * @param j j position in the field of the cell
	 * @param num_peers number of the peers
	 */

	public DContinuous2DXYLB(double discretization, double width, double height
			,SimState sm,int max_distance,int i,int j,int num_peers, String name) {


		super(discretization, width, height);
		this.NAME = name;
		this.sm=sm;	
		MAX_DISTANCE=max_distance;
		numPeers=num_peers;	
		cellType = new CellType(i, j);
		toSendForBalance = new HashMap<Integer, MyCellInterface>();
		toSendForUnion = new HashMap<Integer, MyCellInterface>();

		//upper left corner's coordinates
		own_x=(width/((int)Math.sqrt(numPeers)))* cellType.pos_j; //inversione
		own_y=(height/((int)Math.sqrt(numPeers)))* cellType.pos_i;

		// own width and height
		my_width=(int) (width/Math.sqrt(numPeers));
		my_height=(int) (height/Math.sqrt(numPeers));

		//Divide le celle inizialmente
		balance = new LoadBalancingDoubleField();

		//contiene le celle divise inizialmente
		ArrayList<MyCellInterface> listOriginalCell = balance.createRegions(this, my_width, my_height, MAX_DISTANCE, own_x,own_y,numPeers);

		//struttura in cui vengono inserite le MyCell<Double>
		listGrid = new HashMap<Integer,HashMap<CellType, MyCellInterface>>();

		for (int k = 0; k < 8; k++) {
			toSendForUnion.put(k, null);
		}

		//riempie la struttura contenitore di MyCell<Double>
		for(int k = 0; k < 9; k++){
			listGrid.put(k, new HashMap<CellType, MyCellInterface>());
		}

		int [] lP = {0,1,2,7,8,3,6,5,4};
		for(int k = 0; k < lP.length; k++){
			listGrid.get(lP[k]).put(cellType, listOriginalCell.get(k));
		}

		//Contenitore di UpdatePositionForDoubleField per gli aggiornamenti e le publish
		hashUpdatesPosition = new HashMap<Integer, UpdatePositionDoubleField<DistributedRegion<Double,Double2D>>>();

		this.isSplitted = false;
		this.splitDone = false;
		this.prepareForBalance = false;
		this.prepareForUnion = false;
		this.preUnion = false;
		this.unionDone = false;
		this.isUnited = true;

		outAgents = new RegionDoubleLB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

		/*//RIPRODUCIBILIT�
		// --> only for testing
		String curDir = System.getProperty("user.dir");
		try 
		{
			printer=new PrintWriter(new FileOutputStream(curDir+"/test_"+cellType+"_"+num_peers+"_one.txt"));
		} catch (FileNotFoundException e) { e.printStackTrace();}
		// <--
*/
		updates_cacheLB=new ArrayList<ArrayList<Region<Double,Double2D>>>();
		setConnection(((DistributedState)sm).getConnection());	
		numAgents=0;
	}

	/**
	 * Set a available location to a Remote Agent:
	 * it generates the location depending on the field of expertise
	 * @return The location assigned to Remote Agent
	 */
	public Double2D setAvailableRandomLocation(RemoteAgent<Double2D> rm)
	{    
		double shiftx=((DistributedState)sm).random.nextDouble();
		double shifty=((DistributedState)sm).random.nextDouble();
		double x=(own_x+MAX_DISTANCE)+((my_width+own_x-MAX_DISTANCE)-(own_x+MAX_DISTANCE))*shiftx;
		double y=(own_y+MAX_DISTANCE)+((my_height+own_y-MAX_DISTANCE)-(own_y+MAX_DISTANCE))*shifty;

		rm.setPos(new Double2D(x,y));

		return (new Double2D(x, y));
	}

	/**  
	 * Provide the shift logic of the agents among the peers
	 * @param location The new location of the remote agent
	 * @param rm The remote agent to be stepped
	 * @param sm SimState of simulation
	 * @return 1 if it's in the field, -1 if there's an error (setObjectLocation returns null)
	 */
	public boolean setDistributedObjectLocation(final Double2D location,RemoteAgent<Double2D> rm,SimState sm)
	{

		/**
    	//--> only for testing RIPRODUCIBILITA'
    	buffer_print.add(rm);
		*/
    	numAgents++;
    	boolean fl = false;
    	
    	if(prepareForBalance)
    	{
    		this.remove(rm);
    		
    		boolean b = false;
    		
	    	for(Integer pos : listGrid.keySet())
	    	{	
	    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
	    		
	    		for(CellType ct : hm.keySet())
	    		{
	    			if((hm.get(ct).getPosition() == MyCellInterface.CENTER))
	    			{	
	    				MyCellDoubleField mc = (MyCellDoubleField) hm.get(ct);
	    				if(mc.getMyField().isMine(location.x,location.y))
	    					return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));
	    				
	    				Class o=mc.getMyRMap().getClass();
	    				
	    				Field[] fields = o.getDeclaredFields();
	    				
	    				for (int z = 0; z < fields.length; z++)
	    				{
	    					fields[z].setAccessible(true);
	    					
	    					try
	    					{
	    						String name=fields[z].getName();
	    				    	Method method = o.getMethod("get"+name, null);
	    				    	Object returnValue = method.invoke(mc.getMyRMap(), null);
	    				    	
	    				    	if(returnValue!=null)
	    				    	{
	    				    		Region<Double,Double2D> region=((Region<Double,Double2D>)returnValue);
	    				    		
	    				    		if(region.isMine(location.x, location.y))
	    				    		{
	    				    			if(name.contains("mine"))
	    				    			{
	    				    				region.add(new Entry<Double2D>(rm, location));
	    				    				mc.getMyField().add(new Entry<Double2D>(rm, location));
	    				    			}
	    				    			else
	    				    				if(name.contains("out"))
	    				    				{
	    				    					region.add(new Entry<Double2D>(rm, location));
	    				    					outAgents.add(new Entry<Double2D>(rm, location));
	    				    				}
	    				    		}
	    				    	}
	    					}
	    					catch (Exception e) {
								// TODO: handle exception
							}
	    				}
	    			}
	    			else
		    			if((ct.toString().equals(cellType.toString())) 
		    					&& ((hm.get(ct).getPosition() != MyCellInterface.CENTER)))
		    			{
				    		MyCellDoubleField mc = (MyCellDoubleField) hm.get(ct);
				    		b = balance.addForBalance(location, rm, mc);
			    			fl = fl || b;
		    			}	    			
		    			else
		    			{
		    				MyCellDoubleField mc = (MyCellDoubleField) hm.get(ct);
		    				
					    	if(mc.getMyField().isMine(location.x,location.y))
					    		return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));

					    	if(setAgents(rm, location, mc))
					    		fl = fl || true;
		    			}
	    		}
	    	}
    	} 
    	else
	    	if(prepareForUnion && !isSplitted)
	    	{
	    		this.remove(rm);
	    	
	    		boolean u = false;
	    		
		    	for(Integer pos : listGrid.keySet())
		    	{	
		    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
		    		
		    		for(CellType ct : hm.keySet())
		    		{	/*
		    			MyCellDoubleField mc =  (MyCellDoubleField) hm.get(ct);
		    			u = balance.addForBalance(location, rm, mc);
		    			fl = fl || u;*/
		    			MyCellDoubleField mc =  (MyCellDoubleField) hm.get(ct);
		    			
		    			if(mc.getMyField().isMine(location.x,location.y))
	    					return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));
		    			
		    			Class o=mc.getMyRMap().getClass();
	    				
	    				Field[] fields = o.getDeclaredFields();
	    				
	    				for (int z = 0; z < fields.length; z++)
	    				{
	    					fields[z].setAccessible(true);
	    					
	    					try
	    					{
	    						String name=fields[z].getName();
	    				    	Method method = o.getMethod("get"+name, null);
	    				    	Object returnValue = method.invoke(mc.getMyRMap(), null);
	    				    	
	    				    	if(returnValue!=null)
	    				    	{
	    				    		Region<Double,Double2D> region=((Region<Double,Double2D>)returnValue);
	    				    		
	    				    		if(region.isMine(location.x, location.y))
	    				    		{
	    				    			if(name.contains("mine"))
	    				    			{
	    				    				fl = u || region.add(new Entry<Double2D>(rm, location));
	    				    				mc.getMyField().add(new Entry<Double2D>(rm, location));
	    				    			}
	    				    			else
	    				    				if(name.contains("out"))
	    				    				{
	    				    					fl = u || region.add(new Entry<Double2D>(rm, location));
	    				    					outAgents.add(new Entry<Double2D>(rm, location));
	    				    				}
	    				    		}
	    				    	}
	    					}
	    					catch (Exception e) {
								// TODO: handle exception
							}
	    				}
		    		}
		    	}
	    	}    	
	    	else
	    		if(preUnion)
	    		{
	        		this.remove(rm);
	        		
	        		boolean f = false;
			    	
	        		for(Integer pos : listGrid.keySet())
		        	{	
		        		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
		        		for(CellType ct : hm.keySet())
		        		{	
		        			MyCellDoubleField mc =(MyCellDoubleField) hm.get(ct);
		        			if(mc.isUnion())
		        			{
		        				f = balance.addForBalance(location, rm, mc);
		    			    	fl = fl || f;
		        			}
		        			else
		        			{
			        			if(mc.getMyField().isMine(location.x,location.y)) 
						    		return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));
			        			if(setAgents(rm, location, mc))
			        				fl = fl || true;
		        			}
		        		}
		        	}
	    		}
		    	else
		    	{		    		
			    	for(Integer pos : listGrid.keySet())
			    	{	
			    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
			    		
			    		for(CellType ct : hm.keySet())
			    		{	
			    			MyCellDoubleField mc = (MyCellDoubleField)hm.get(ct);

			    			if(mc.isMine(location.x, location.y) && getState().schedule.getSteps()>1)
			    			{
			    				if(mc.getMyField().isMine(location.x,location.y)) 
			    					return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));
			    				if(setAgents(rm, location, mc)){
			    					fl = fl || true;
			    				}
			    			}
			    			else{
			    				if(mc.getMyField().isMine(location.x,location.y))  
			    					return mc.getMyField().addAgents(new Entry<Double2D>(rm, location));
			    				if(setAgents(rm, location, mc))
			    					fl = fl || true;
			    			}
			    		}
			    	}
		    	}    	
    	if(fl) 
    		return true;
    	else
    	{
	    	System.out.println(cellType+")OH MY GOD!"+"  location: "+location+" ID= "+rm.getId()); // it should never happen (don't tell it to anyone shhhhhhhh! ;P) 
	    	return false;
    	}
	}

	/**
	 * 	This method provides the synchronization in the distributed environment.
	 * 	It's called after every step of schedule.
	 */
	public synchronized boolean synchro() 
	{	
		/**
		if(sm.schedule.getSteps()==80){
			String curDir = System.getProperty("user.dir");
			PrintWriter p = null;
			try 
			{
				p=new PrintWriter(new FileOutputStream(curDir+"/testAllAgentsAtStep80.txt",true));
			} catch (FileNotFoundException e) { e.printStackTrace();}

			for(RemoteAgent<Double2D> r : buffer_print)
			{
				p.println(r.id);
				p.flush();
			}//D
		}
		//--> only for testing RIPRODUCIBILIT�
		if((sm.schedule.getSteps()-1)<=1200)
			for(RemoteAgent<Double2D> r : buffer_print)
			{
				printer.println(cellType+","+(sm.schedule.getSteps()-1)+","+r.id+","+r.pos.getX()+","+r.pos.getY());
				printer.flush();
			}//<--
		buffer_print=new ArrayList<RemoteAgent<Double2D>>();
		*/

		for(ArrayList<Region<Double, Double2D>> arr : updates_cacheLB)
		{
			for(Region<Double,Double2D> region : arr)
			{
				for(Entry<Double2D> remote_agent : region)
				{
					this.remove(remote_agent.r);
				}
			}
		}
		
		if(prepareForUnion && !isSplitted)
		{
			for (int j2 = 0; j2 <= 16; j2++) {
				listGrid.get(MyCellInterface.CENTER).get(cellType).getPositionGood().put(j2, false);
				listGrid.get(MyCellInterface.CENTER).get(cellType).getPositionPublish().put((j2%8), false);
			}
		}
		
		if(preUnion){
			CellType c = null;
			int p=-1;
	    	for(Integer pos : listGrid.keySet())
	    	{	
	    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
	    		for(CellType ct : hm.keySet())
	    		{	
	    			if(!hm.get(ct).getParentCellType().toString().equals(cellType.toString()) && 
	    					hm.get(ct).isUnion()){
	    				c = ct;
	    				p = pos;
	    				positionForUnion = pos;
	    				if(!isSplitted)	
	    					clearReturnedOut((MyCellDoubleField)hm.get(ct));
	    			}
	    		}
	    	}
	    	if(p!=-1)
	    		toSendForUnion.put(p, listGrid.get(p).remove(c));
		}

		resetPublishList(sm.schedule.getSteps()-1);
		
		if(preUnion)
		{
			if(!isSplitted)
				for(Integer position : toSendForUnion.keySet())
				{
					if(toSendForUnion.get(position)!= null){
						
						if(position%2!=0) 	// topic sui lati
						{
							listGrid.get((position-1+8)%8).get(cellType).getPositionPublish().put(position, true);
							listGrid.get((position+1+8)%8).get(cellType).getPositionPublish().put(position, true);
						}
					}
				}
			
		}
		
		//--> publishing the regions to correspondent topics for the neighbors
		preparePublishUnion();
		preparePublishCenter();
		preparePublishMicroCell();
		positionForUnion = -1;
		unionDone = false;
		
		if(preUnion)
		{
			if(!isSplitted)
				for(Integer position : toSendForUnion.keySet())
				{
					if(toSendForUnion.get(position)!= null){
						
						if(position%2==0) 	// topic sugli angoli
						{
							listGrid.get(position).get(cellType).getPositionPublish().put(position, true);
						}
						else // topic sui lati
						{	
							listGrid.get((position-1+8)%8).get(cellType).getPositionPublish().put(position, true);
							listGrid.get(position).get(cellType).getPositionPublish().put(position, true);
							listGrid.get((position+1+8)%8).get(cellType).getPositionPublish().put(position, true);
						}
					}
				}
			
			preUnion = false;
		}
		
		if(prepareForBalance)
		{
			for (int k = 0; k < 8; k++) {
				
				toSendForBalance.put(k, listGrid.get(k).remove(cellType));
			}
		}
		
		for(Integer pos : listGrid.keySet())
		{
			HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
			for(CellType ct : hm.keySet())
			{
				removeOut((MyCellDoubleField) hm.get(ct));
			}
		}
		
		//every agent in the myfield region is scheduled
		for(Integer pos : listGrid.keySet())
    	{	
    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
	    		
    		for(CellType ct : hm.keySet())
    		{	
    			if(ct.equals(cellType)){
    				MyCellDoubleField md = (MyCellDoubleField) hm.get(ct);
    				
    				for(Entry<Double2D> e: md.getMyField())
    				{
    					RemoteAgent<Double2D> rm=e.r;
    					Double2D loc=e.l;
    					rm.setPos(loc);
    					this.remove(rm);
    					sm.schedule.scheduleOnce(rm);
    					setObjectLocation(rm,loc);
    				}
    			}
    		}
    	}
		
		//every agent in the myfield region is scheduled
		for(Integer pos : listGrid.keySet())
    	{	
    		HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
	    		
    		for(CellType ct : hm.keySet())
    		{	
    			if(!ct.equals(cellType)){
    				MyCellDoubleField md = (MyCellDoubleField) hm.get(ct);
    				
    				for(Entry<Double2D> e: md.getMyField())
    				{
    					RemoteAgent<Double2D> rm=e.r;
    					Double2D loc=e.l;
    					rm.setPos(loc);
    					this.remove(rm);
    					sm.schedule.scheduleOnce(rm);
    					setObjectLocation(rm,loc);
    				}
    			}
    		}
    	}

		updates_cacheLB=new ArrayList<ArrayList<Region<Double, Double2D>>>();
		
		//((DistributedMultiSchedule)(sm.schedule)).manageMerge(hashUpdatesPosition,this,cellType);
		//((DistributedMultiSchedule)(sm.schedule)).manageBalance(hashUpdatesPosition,this,cellType,balance);
		
		//PUBLISH SUI TOPIC
		try {
			
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.LEFT),cellType.toString()+"L", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.RIGHT),cellType.toString()+"R", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.UP),cellType.toString()+"U", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.DOWN),cellType.toString()+"D", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT),cellType.toString()+"CUDL", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT),cellType.toString()+"CUDR", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT),cellType.toString()+"CDDL", NAME);
			connection.publishToTopic(hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT),cellType.toString()+"CDDR", NAME);	
			
		} catch (Exception e1) { e1.printStackTrace();}
		//<--

		//take from UpdateMap the updates for current last terminated step and use 
		//verifyUpdates() to elaborate informations
		PriorityQueue<Object> q;
		try 
		{
			q = updates.getUpdates(sm.schedule.getSteps()-1, 8);			

			while(!q.isEmpty())
			{
				UpdatePositionDoubleField<DistributedRegion<Double, Double2D>> region=(UpdatePositionDoubleField<DistributedRegion<Double,Double2D>>)q.poll();
				
				((DistributedMultiSchedule)(sm.schedule)).externalAgents+=region.getNumAgentExternalCell();
				
				if(region.isPreBalance())
				{
					if(region.getMyCell()!=null)
					{
						MyCellDoubleField mc = region.getMyCell();

						resetArrivedCellPositions(mc);
						
						listGrid.get(mc.getPosition()).put(mc.getParentCellType(), mc);
						
						for(Entry<Double2D> e: mc.getMyField())
						{							
							RemoteAgent<Double2D> rm=e.r;
							Double2D loc=e.l;
							rm.setPos(loc);
						    this.remove(rm);
							sm.schedule.scheduleOnce(rm);
							setObjectLocation(rm,loc);
						}
						
						if(!isSplitted){
							clearArrivedOut(mc);
							updateExternalOutFromAdiacentCell(mc);
						}

						updateFields(mc);
						updateInternalMine(mc);
						memorizeRegionOut(mc);
						
						if(!isSplitted)
						{
							if(mc.getPosition()%2==0) 	// position sugli angoli
							{
								listGrid.get(mc.getPosition()).get(cellType).getPositionGood().put(mc.getPosition(), false);
							}
							else // position sui lati
							{
								int h=10;
							
								{	//centrale
									listGrid.get(mc.getPosition()).get(cellType).getPositionGood().put(mc.getPosition(), false);
									
									for (int k = 0; k < 7; k+=2) 
									{	
										if(!listGrid.get(mc.getPosition()).get(cellType).getPositionPublish().get((k+1+8)%8))
										{
											listGrid.get(mc.getPosition()).get(cellType).getPositionGood().put(h+k, false);
										}
										if(!listGrid.get(mc.getPosition()).get(cellType).getPositionPublish().get((k-1+8)%8))
										{
											listGrid.get(mc.getPosition()).get(cellType).getPositionGood().put(h+k, false);
										}
									}
								}
								{	//laterali
									listGrid.get((mc.getPosition()-1+8)%8).get(cellType).getPositionGood().
										put(((mc.getPosition()+1+8)%8),false);
									listGrid.get((mc.getPosition()+1+8)%8).get(cellType).getPositionGood().
									put(((mc.getPosition()-1+8)%8),false);
								}
							}
						}
					}
					else
					{
						resetLocalPositionPublish(region.getPosition());
					}
				}
				
				//the owner send the cell requested by a splitted neighbours
				if(region.isPreUnion())
				{
					preUnion = true;
					int k = balance.calculatePositionForBalance(region.getPosition());
					HashMap<CellType, MyCellInterface> hm = listGrid.get(k);
					
					for(CellType ct : hm.keySet()){
						
						if(hm.get(ct).getParentCellType().toString().equals(region.getCellType().toString()))
						{
							hm.get(ct).setUnion(true);
							resetUnionLocalPositionForShipping(hm.get(ct).getPosition());
						}
					}
				}
				
				//cell received for union
				if(region.isUnion())
				{
					MyCellDoubleField mc = region.getMyCell();
					region.setMyCell(null);
					int rightPosition = balance.calculatePositionForBalance(mc.getPosition());
					mc.setPosition(rightPosition);
					mc.setParentCellType(cellType);
					mc.setUnion(false);
					listGrid.get(mc.getPosition()).put(cellType, mc);
					region.setUnion(false);
				}
				
				verifyUpdates(region);	
			}			
			
			if(prepareForBalance && !isSplitted)
			{
				for (int j2 = 0; j2 <= 16; j2++) {
					listGrid.get(MyCellInterface.CENTER).get(cellType).getPositionGood().put(j2, true);
				}

				for(Integer pos : listGrid.keySet())
				{
					HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

					for(CellType ct : hm.keySet()){

						if(!ct.equals(cellType))
						{

							for (int j2 = 0; j2 <= 16; j2++) {

								hm.get(ct).getPositionGood().put(j2, true);
							}
						}
					}
				}
			}
			
			makeUnion();
					
		}catch (InterruptedException e1) {e1.printStackTrace(); }
		
		if(isSplitted && splitDone){
			for (int j2 = 0; j2 <= 8; j2++) {
				listGrid.get(MyCellInterface.CENTER).get(cellType).getPositionPublish().put(j2, true);
			}
			
			for(Integer pos : listGrid.keySet())
			{
				if(pos != MyCellInterface.CENTER)
					listGrid.get(pos).remove(cellType);
			}
			
			splitDone = false;
		}
		ArrayList<Region<Double,Double2D>> tmp = new ArrayList<Region<Double,Double2D>>();
		tmp.add(outAgents.clone());
		updates_cacheLB.add(tmp);
		for(ArrayList<Region<Double,Double2D>> regions : updates_cacheLB)
			for(Region<Double,Double2D> r : regions)
				for(Entry<Double2D> e_m: r)
				{
					RemoteAgent<Double2D> rm=e_m.r;
					((DistributedState<Double2D>)sm).addToField(rm,e_m.l);
				}

		outAgents = new RegionDoubleLB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		
		this.reset();

		return true;
	}
	/**
	 * This method, written with Java Reflect, follows two logical ways for all the regions:
	 * - if a region is an out one, the agent's location is updated and it's insert a new Entry 
	 * 		in the updates_cacheLB (cause the agent is moving out and it's important to maintain the information
	 * 		for the next step)
	 * - if a region is a mine one, the agent's location is updated and the agent is scheduled.
	 */
	private void removeOut(MyCellInterface md)
	{
		Class o=md.getMyRMap().getClass();
				
		Field[] fields = o.getDeclaredFields();
	    for (int z = 0; z < fields.length; z++)
	    {
	    	fields[z].setAccessible(true);
	    	try
	    	{
	    		String name=fields[z].getName();
	    		Method method = o.getMethod("get"+name, null);
	    		Object returnValue = method.invoke(md.getMyRMap(), null);
	    		if(returnValue!=null)
	    		{
	    			Region<Integer,Double2D> region=((Region<Integer,Double2D>)returnValue);
		   			if(name.contains("out"))
		   			{
		   				for(Entry<Double2D> e: region)
		   			 	{
		   					RemoteAgent<Double2D> rm=e.r;
		   					rm.setPos(e.l);
		   					this.remove(rm);
		   			 	} 
		   			}
			   	}
		 	}
	    	catch (IllegalArgumentException e){e.printStackTrace();} 
	    	catch (IllegalAccessException e) {e.printStackTrace();} 
	    	catch (SecurityException e) {e.printStackTrace();} 
	    	catch (NoSuchMethodException e) {e.printStackTrace();} 
	    	catch (InvocationTargetException e) {e.printStackTrace();}
			}
		
	}

	//Resetta le strutture contenitore delle publish
	private void resetPublishList(long step){

		hashUpdatesPosition.put(UpdatePositionInterface.CORNER_DIAG_UP_LEFT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.CORNER_DIAG_UP_LEFT,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.UP, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.UP,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.CORNER_DIAG_UP_RIGHT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.CORNER_DIAG_UP_RIGHT,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.RIGHT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.RIGHT,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.CORNER_DIAG_DOWN_RIGHT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.CORNER_DIAG_DOWN_RIGHT,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.DOWN, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.DOWN,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.CORNER_DIAG_DOWN_LEFT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.CORNER_DIAG_DOWN_LEFT,cellType,null));
		hashUpdatesPosition.put(UpdatePositionInterface.LEFT, 
				new UpdatePositionDoubleField<DistributedRegion<Double, Double2D>>(step,UpdatePositionInterface.LEFT,cellType,null));
	}

	private void makeUnion() {

		if(prepareForUnion && !isSplitted)
		{
			for(Integer pos : listGrid.keySet())
			{
				if(pos != MyCellInterface.CENTER)
				{
					HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

					for(CellType c : hm.keySet()){

						if(c.equals(cellType))
						{
							MyCellDoubleField mc = (MyCellDoubleField)hm.get(c);
							int antiPosition = balance.calculatePositionForBalance(mc.getPosition());
							if(mc.getPosition()%2==0)//angoli
							{
								for (int j2 = 0; j2 < 8; j2++) {

									if(j2 == ((antiPosition-1+8)%8))	
										mc.getPositionPublish().put(j2, false);
									else
										if(j2 == antiPosition)
											mc.getPositionPublish().put(j2, false);
										else
											if(j2 == ((antiPosition+1+8)%8))
												mc.getPositionPublish().put(j2, false);
											else
												mc.getPositionPublish().put(j2, true);	
								}
							}
							else
							{
								for (int j2 = 0; j2 < 8; j2++) 
								{
									if(j2 == ((mc.getPosition()-1+8)%8))	
										mc.getPositionPublish().put(j2, true);
									else
										if(j2 == mc.getPosition())
											mc.getPositionPublish().put(j2, true);
										else
											if(j2 == ((mc.getPosition()+1+8)%8))
												mc.getPositionPublish().put(j2, true);
											else
												mc.getPositionPublish().put(j2, false);
								}
							}
						}
					}
				}
			}

			for(Integer pos : listGrid.keySet())
			{
				if(pos != MyCellInterface.CENTER)
				{
					HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

					for(CellType ct : hm.keySet())
					{
						if(!ct.equals(cellType) && hm.get(ct)!=null)
						{
							MyCellDoubleField mc = (MyCellDoubleField)hm.get(ct);
							int topicPosition = mc.getPosition();

							if(topicPosition%2==0) 	// topic sugli angoli
							{
								listGrid.get(topicPosition).get(cellType).getPositionPublish().put(topicPosition, false);
							}
							else // topic sui lati
							{
								listGrid.get((topicPosition-1+8)%8).get(cellType).getPositionPublish().put(((topicPosition+1+8)%8), false);
								listGrid.get((topicPosition-1+8)%8).get(cellType).getPositionPublish().put(topicPosition, false);
								listGrid.get(topicPosition).get(cellType).getPositionPublish().put(topicPosition, false);
								listGrid.get((topicPosition+1+8)%8).get(cellType).getPositionPublish().put(((topicPosition-1+8)%8), false);
								listGrid.get((topicPosition+1+8)%8).get(cellType).getPositionPublish().put(topicPosition, false);
							}
						}
					}
				}
			}

			for(Integer pos : listGrid.keySet())
			{
				if(pos != MyCellInterface.CENTER)
				{
					HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

					for(CellType c : hm.keySet()){

						if(c.equals(cellType))
						{
							MyCellDoubleField mc = (MyCellDoubleField)hm.get(c);
							int antiPosition = balance.calculatePositionForBalance(mc.getPosition());
							int position = mc.getPosition();

							if(position%2==0) //angoli
							{
								for (int l = 0; l <= 16; l++) {

									mc.getPositionGood().put(l, true);
								}

								mc.getPositionGood().put(((antiPosition+2+8)%8)+9, false);
								mc.getPositionGood().put(((antiPosition+1+8)%8), false);
								mc.getPositionGood().put((antiPosition+10), false);
								mc.getPositionGood().put(antiPosition, false);
								mc.getPositionGood().put((antiPosition+9), false);
								mc.getPositionGood().put(((antiPosition-1+8)%8), false);
								mc.getPositionGood().put(((antiPosition-2+8)%8)+10, false);


								if(!mc.getPositionPublish().get(((position-2+8)%8)))
								{
									mc.getPositionGood().put(((position-2+8)%8), false);
								}
								if(!mc.getPositionPublish().get(position))
								{
									mc.getPositionGood().put(position, false);
								}
								if(!mc.getPositionPublish().get(((position+2+8)%8)))
								{
									mc.getPositionGood().put(((position+2+8)%8), false);
								}
							}
							else //lati
							{
								for (int l = 0; l <= 16; l++) {

									mc.getPositionGood().put(l, false);
								}

								if(mc.getPositionPublish().get(position))
								{
									mc.getPositionGood().put(((position+1+8)%8), true);
									mc.getPositionGood().put(((position+1+8)%8)+9, true);
									mc.getPositionGood().put(position, true);
									mc.getPositionGood().put(((position-1+8)%8), true);
									mc.getPositionGood().put(((position-1+8)%8)+10, true);
								}
								else
								{
									mc.getPositionGood().put(((position+1+8)%8), true);
									mc.getPositionGood().put(((position+1+8)%8)+9, false);
									mc.getPositionGood().put(position, false);
									mc.getPositionGood().put(((position-1+8)%8), true);
									mc.getPositionGood().put(((position-1+8)%8)+10, false);

								}
							}
						}
					}
				}
			}

			for(Integer pos : listGrid.keySet())
			{	
				if(pos != MyCellInterface.CENTER)
				{
					HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

					for(CellType ct : hm.keySet())
					{
						if(ct.equals(cellType)){
							MyCellDoubleField md = (MyCellDoubleField)hm.get(ct);

							for(Entry<Double2D> e: md.getMyField())
							{
								RemoteAgent<Double2D> rm=e.r;
								Double2D loc=e.l;
								rm.setPos(loc);
								this.remove(rm);
								sm.schedule.scheduleOnce(rm);
								setObjectLocation(rm,loc);
								setPortrayalForObject(rm);
							}

							updateFields(md);
							updateInternalMine(md);
							updateInternalOut(md);
							memorizeRegionOut(md);
						}
					}
				}
			}

			updateInternalOut(listGrid.get(MyCellInterface.CENTER).get(cellType));

			for(Integer pos : listGrid.keySet())
			{	
				HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);
				for(CellType ct : hm.keySet())
				{
					if(!ct.equals(cellType))
					{
						updateInternalOut(hm.get(ct));
					}
				}
			}

			prepareForUnion = false;
			isUnited = true;
		}

		if(prepareForUnion && isSplitted)
		{ 
			for(Integer pos : listGrid.keySet())
			{
				HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

				for(CellType c : hm.keySet()){

					if(!c.equals(cellType))
					{
						MyCellDoubleField mc = (MyCellDoubleField)hm.get(c);

						int position = mc.getPosition();
						int antiPosition = balance.calculatePositionForBalance(position);

						int h = 10; 

						if(position%2==0) //angoli
						{
							//INIZIALIZZIAMO LE POSITION GOOD
							for (int k = 0; k <= 16; k++) 
							{
								if(k == antiPosition)
									mc.getPositionGood().put(antiPosition, false);
								else
									mc.getPositionGood().put(k, true);
							}

							for (int k = 0; k < 8; k++) 
							{
								if(k==(antiPosition))
									mc.getPositionPublish().put(k, false);
								else
									mc.getPositionPublish().put(k, true);
							}

							HashMap<CellType, MyCellInterface> hm1 = listGrid.get(((mc.getPosition()-1+8)%8));
							HashMap<CellType, MyCellInterface> hm2 = listGrid.get(((mc.getPosition()+1+8)%8));

							for(CellType ct : hm1.keySet())
							{
								if(!ct.equals(cellType) && hm1.get(ct)!=null)
								{
									mc.getPositionPublish().put(((mc.getPosition()-2+8)%8), false);
									mc.getPositionPublish().put(((mc.getPosition()-3+8)%8), false);
								}
							}
							for(CellType ct : hm2.keySet())
							{
								if(!ct.equals(cellType) && hm2.get(ct)!=null)
								{
									mc.getPositionPublish().put(((mc.getPosition()+2+8)%8), false);
									mc.getPositionPublish().put(((mc.getPosition()+3+8)%8), false);
								}
							}
						}
						else //laterali
						{								
							//INIZIALIZZIAMO LE POSITION GOOD
							for (int k = 0; k <= 16; k++) 
							{
								mc.getPositionGood().put(k, true);
							}

							mc.getPositionGood().put(((antiPosition+1+8)%8), false);
							mc.getPositionGood().put(((antiPosition+1+8)%8)+9, false);
							mc.getPositionGood().put(antiPosition, false);
							mc.getPositionGood().put(((antiPosition-1+8)%8), false);
							mc.getPositionGood().put(((antiPosition-1+8)%8)+10, false);
						}
					}

				}
			}

			isSplitted = false;
			unionDone = true;
		}
	}

	private void resetUnionLocalPositionForShipping(int position) {

		if(!isSplitted)
		{	
			if(position%2==0) 	// topic sugli angoli
			{
				listGrid.get(position).get(cellType).getPositionPublish().put(position, false);
			}
			else // topic sui lati
			{	
				listGrid.get((position-1+8)%8).get(cellType).getPositionPublish().put(position, false);
				listGrid.get(position).get(cellType).getPositionPublish().put(position, false);
				listGrid.get((position+1+8)%8).get(cellType).getPositionPublish().put(position, false);
			}
		}

		if(position%2!=0)
		{
			HashMap<CellType, MyCellInterface> hm1 = listGrid.get((position-1+8)%8);
			for(CellType ct : hm1.keySet())
			{
				if(!ct.equals(cellType))
				{
					MyCellDoubleField mc1 =(MyCellDoubleField) hm1.get(ct);
					mc1.getPositionPublish().put(((mc1.getPosition()+2+8)%8), true);
					mc1.getPositionPublish().put(((mc1.getPosition()+3+8)%8), true);
				}
			}

			HashMap<CellType, MyCellInterface> hm2 = listGrid.get((position+1+8)%8);
			for(CellType ct : hm2.keySet())
			{
				if(!ct.equals(cellType))
				{
					MyCellDoubleField mc2 = (MyCellDoubleField) hm2.get(ct);
					mc2.getPositionPublish().put(((mc2.getPosition()-2+8)%8), true);
					mc2.getPositionPublish().put(((mc2.getPosition()-3+8)%8), true);
				}
			}
		}

		if(!isSplitted)
		{
			if(position%2==0) 	// position sugli angoli
			{
				listGrid.get(position).get(cellType).getPositionGood().put(position, true);
			}
			else // position sui lati
			{			
				{	//centrale

					for (int l = 0; l <= 16; l++) {

						listGrid.get(position).get(cellType).getPositionGood().put(l, false);
					}

					listGrid.get(position).get(cellType).getPositionGood().put(((position+1+8)%8), true);
					listGrid.get(position).get(cellType).getPositionGood().put(((position+1+8)%8)+9, true);
					listGrid.get(position).get(cellType).getPositionGood().put(position, true);
					listGrid.get(position).get(cellType).getPositionGood().put(((position-1+8)%8), true);
					listGrid.get(position).get(cellType).getPositionGood().put(((position-1+8)%8)+10, true);
				}
				{	//laterali
					listGrid.get((position-1+8)%8).get(cellType).getPositionGood().
					put(((position+1+8)%8),true);
					listGrid.get((position+1+8)%8).get(cellType).getPositionGood().
					put(((position-1+8)%8),true);
				}
			}
		}
	}

	private void clearReturnedOut(MyCellDoubleField mc) {

		int position =mc.getPosition();

		if(position == MyCellInterface.UP)
		{
			mc.getMyRMap().corner_out_down_left_diag_center.clear();
			mc.getMyRMap().corner_mine_down_left.clear();
			mc.getMyRMap().corner_mine_down_right.clear();
			mc.getMyRMap().corner_out_down_right_diag_center.clear();
		}
		else
			if(position == MyCellInterface.RIGHT)
			{
				mc.getMyRMap().corner_out_up_left_diag_center.clear();
				mc.getMyRMap().corner_mine_up_left.clear();
				mc.getMyRMap().corner_mine_down_left.clear();
				mc.getMyRMap().corner_out_down_left_diag_center.clear();
			}
			else
				if(position == MyCellInterface.DOWN)
				{
					mc.getMyRMap().corner_out_up_left_diag_center.clear();
					mc.getMyRMap().corner_mine_up_left.clear();
					mc.getMyRMap().corner_mine_up_right.clear();
					mc.getMyRMap().corner_out_up_right_diag_center.clear();
				}
				else
					if(position == MyCellInterface.LEFT)
					{
						mc.getMyRMap().corner_out_up_right_diag_center.clear();
						mc.getMyRMap().corner_mine_up_right.clear();
						mc.getMyRMap().corner_mine_down_right.clear();
						mc.getMyRMap().corner_out_down_right_diag_center.clear();
					}
	}

	private void resetLocalPositionPublish(int position) {

		int topicPosition = balance.calculatePositionForBalance(position);

		if(!isSplitted)
		{	
			if(topicPosition%2==0) 	// topic sugli angoli
			{
				listGrid.get(topicPosition).get(cellType).getPositionPublish().put(topicPosition, false);
			}
			else // topic sui lati
			{
				listGrid.get((topicPosition-1+8)%8).get(cellType).getPositionPublish().put(topicPosition, false);
				listGrid.get(topicPosition).get(cellType).getPositionPublish().put(topicPosition, false);
				listGrid.get((topicPosition+1+8)%8).get(cellType).getPositionPublish().put(topicPosition, false);
			}
		}

		if(topicPosition%2!=0)
		{
			HashMap<CellType, MyCellInterface> hm1 = listGrid.get((topicPosition-1+8)%8);
			for(CellType ct : hm1.keySet())
			{
				if(!ct.equals(cellType))
				{
					MyCellDoubleField mc = (MyCellDoubleField) hm1.get(ct);
					mc.getPositionPublish().put(((topicPosition+1+8)%8), false);
					mc.getPositionPublish().put(((topicPosition+2+8)%8), false);
				}
			}

			HashMap<CellType, MyCellInterface> hm2 = listGrid.get((topicPosition+1+8)%8);
			for(CellType ct : hm2.keySet())
			{
				if(!ct.equals(cellType))
				{
					MyCellDoubleField mc = (MyCellDoubleField) hm2.get(ct);
					mc.getPositionPublish().put(((topicPosition-1+8)%8), false);
					mc.getPositionPublish().put(((topicPosition-2+8)%8), false);
				}
			}
		}
	}

	private void resetArrivedCellPositions(MyCellDoubleField mc) {

		int position = mc.getPosition();
		int antiPosition = balance.calculatePositionForBalance(position);

		int h = 10; 

		if(!isSplitted){
			if(position%2==0) //angoli
			{
				//INIZIALIZZIAMO LE POSITION GOOD
				for (int k = 0; k <= 16; k++) 
				{
					if(k == antiPosition)
						mc.getPositionGood().put(antiPosition, false);
					else
						mc.getPositionGood().put(k, true);
				}

				for (int k = 0; k < 8; k++) 
				{
					if(k==(antiPosition))
						mc.getPositionPublish().put(k, false);
					else
						mc.getPositionPublish().put(k, true);
				}

				HashMap<CellType, MyCellInterface> hm1 = listGrid.get(((mc.getPosition()-1+8)%8));
				HashMap<CellType, MyCellInterface> hm2 = listGrid.get(((mc.getPosition()+1+8)%8));

				for(CellType ct : hm1.keySet())
				{
					if(!ct.equals(cellType) && hm1.get(ct)!=null)
					{
						mc.getPositionPublish().put(((mc.getPosition()-2+8)%8), false);
						mc.getPositionPublish().put(((mc.getPosition()-3+8)%8), false);
					}
				}
				for(CellType ct : hm2.keySet())
				{
					if(!ct.equals(cellType) && hm2.get(ct)!=null)
					{
						mc.getPositionPublish().put(((mc.getPosition()+2+8)%8), false);
						mc.getPositionPublish().put(((mc.getPosition()+3+8)%8), false);
					}
				}
			}
			else //laterali
			{								
				//INIZIALIZZIAMO LE POSITION GOOD
				for (int k = 0; k <= 16; k++) 
				{
					mc.getPositionGood().put(k, true);
				}

				for (int k = 0; k < 8; k++) 
				{	
					if(k==((position-2+8)%8))
						mc.getPositionPublish().put(k, true);
					else
						if(k==((position-1+8)%8))
							mc.getPositionPublish().put(k, true);
						else
							if(k == position)
								mc.getPositionPublish().put(k, true);
							else
								if(k == ((position+1+8)%8))
									mc.getPositionPublish().put(k, true);
								else
									if(k == ((position+2+8)%8))
										mc.getPositionPublish().put(k, true);
									else
										mc.getPositionPublish().put(k, false);
				}


				mc.getPositionGood().put(((antiPosition+1+8)%8), false);
				mc.getPositionGood().put(((antiPosition+1+8)%8)+9, false);
				mc.getPositionGood().put(antiPosition, false);
				mc.getPositionGood().put(((antiPosition-1+8)%8), false);
				mc.getPositionGood().put(((antiPosition-1+8)%8)+10, false);


				HashMap<CellType, MyCellInterface> hm1 = listGrid.get(((mc.getPosition()-1+8)%8));
				HashMap<CellType, MyCellInterface> hm2 = listGrid.get(((mc.getPosition()+1+8)%8));

				for(CellType ct : hm1.keySet())
				{
					if(!ct.equals(cellType) && hm1.get(ct)!=null)
					{
						MyCellDoubleField mc1 = (MyCellDoubleField)hm1.get(ct);
						mc1.getPositionPublish().put(((mc1.getPosition()+2+8)%8), false);
						mc1.getPositionPublish().put(((mc1.getPosition()+3+8)%8), false);
					}
				}
				for(CellType ct : hm2.keySet())
				{
					if(!ct.equals(cellType) && hm2.get(ct)!=null)
					{
						MyCellDoubleField mc2 = (MyCellDoubleField)hm2.get(ct);
						mc2.getPositionPublish().put(((mc2.getPosition()-2+8)%8), false);
						mc2.getPositionPublish().put(((mc2.getPosition()-3+8)%8), false);
					}
				}
			}
		}
		else
		{

			if(mc.getPosition()%2==0)
			{
				for (int k = 0; k <= 16; k++) 
				{
					mc.getPositionGood().put(k, true);
				}

				for (int k = 0; k < 8; k++) 
				{
					mc.getPositionPublish().put(k, true);
				}

				HashMap<CellType, MyCellInterface> hm1 = listGrid.get(((mc.getPosition()-1+8)%8));
				HashMap<CellType, MyCellInterface> hm2 = listGrid.get(((mc.getPosition()+1+8)%8));

				for(CellType ct : hm1.keySet())
				{
					if(!ct.equals(cellType) && hm1.get(ct)!=null)
					{
						mc.getPositionPublish().put(((mc.getPosition()-2+8)%8), false);
						mc.getPositionPublish().put(((mc.getPosition()-3+8)%8), false);
					}
				}
				for(CellType ct : hm2.keySet())
				{
					if(!ct.equals(cellType) && hm2.get(ct)!=null)
					{
						mc.getPositionPublish().put(((mc.getPosition()+2+8)%8), false);
						mc.getPositionPublish().put(((mc.getPosition()+3+8)%8), false);
					}
				}
			}
			else
			{
				for (int k = 0; k <= 16; k++) 
				{
					mc.getPositionGood().put(k, true);
				}

				for (int k = 0; k < 8; k++) 
				{
					mc.getPositionPublish().put(k, true);
				}
				HashMap<CellType, MyCellInterface> hm1 = listGrid.get(((mc.getPosition()-1+8)%8));
				HashMap<CellType, MyCellInterface> hm2 = listGrid.get(((mc.getPosition()+1+8)%8));

				for(CellType ct : hm1.keySet())
				{
					if(!ct.equals(cellType) && hm1.get(ct)!=null)
					{
						MyCellDoubleField mc1 =(MyCellDoubleField) hm1.get(ct);
						mc1.getPositionPublish().put(((mc1.getPosition()+2+8)%8), false);
						mc1.getPositionPublish().put(((mc1.getPosition()+3+8)%8), false);
					}
				}
				for(CellType ct : hm2.keySet())
				{
					if(!ct.equals(cellType) && hm2.get(ct)!=null)
					{
						MyCellDoubleField mc2 = (MyCellDoubleField) hm2.get(ct);
						mc2.getPositionPublish().put(((mc2.getPosition()-2+8)%8), false);
						mc2.getPositionPublish().put(((mc2.getPosition()-3+8)%8), false);
					}
				}
			}
		}

	}

	private void updateExternalOutFromAdiacentCell(MyCellInterface mc) {

		int position = mc.getPosition();

		if(position == MyCellInterface.CORNER_DIAG_UP_LEFT)
		{
			for(Entry<Double2D> e: (RegionDoubleLB) ((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_LEFT).get(cellType)).getMyRMap().getcorner_out_up_left_diag_center())
			{			    	
				RemoteAgent<Double2D> rm=e.r;
				Double2D loc=e.l;
				rm.setPos(loc);
				this.remove(rm);
				sm.schedule.scheduleOnce(rm);
				setObjectLocation(rm,loc);
				setPortrayalForObject(rm);
			}
		}
		else
			if(position == MyCellInterface.UP)
			{
				for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_LEFT).get(cellType)).getMyRMap().getcorner_out_up_right_diag_center())
				{			    	
					RemoteAgent<Double2D> rm=e.r;
					Double2D loc=e.l;
					rm.setPos(loc);
					this.remove(rm);
					sm.schedule.scheduleOnce(rm);
					setObjectLocation(rm,loc);
					setPortrayalForObject(rm);
				}
				for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.UP).get(cellType)).getMyRMap().getup_out())
				{			    	
					RemoteAgent<Double2D> rm=e.r;
					Double2D loc=e.l;
					rm.setPos(loc);
					this.remove(rm);
					sm.schedule.scheduleOnce(rm);
					setObjectLocation(rm,loc);
					setPortrayalForObject(rm);
				}
				for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).get(cellType)).getMyRMap().getcorner_out_up_left_diag_center())
				{			    	
					RemoteAgent<Double2D> rm=e.r;
					Double2D loc=e.l;
					rm.setPos(loc);
					this.remove(rm);
					sm.schedule.scheduleOnce(rm);
					setObjectLocation(rm,loc);
					setPortrayalForObject(rm);
				}
			}
			else
				if(position == MyCellInterface.CORNER_DIAG_UP_RIGHT)
				{
					for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).get(cellType)).getMyRMap().getcorner_out_up_right_diag_center())
					{			    	
						RemoteAgent<Double2D> rm=e.r;
						Double2D loc=e.l;
						rm.setPos(loc);
						this.remove(rm);
						sm.schedule.scheduleOnce(rm);
						setObjectLocation(rm,loc);
						setPortrayalForObject(rm);
					}
				}
				else
					if(position == MyCellInterface.RIGHT)
					{
						for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).get(cellType)).getMyRMap().getcorner_out_down_right_diag_center())
						{			    	
							RemoteAgent<Double2D> rm=e.r;
							Double2D loc=e.l;
							rm.setPos(loc);
							this.remove(rm);
							sm.schedule.scheduleOnce(rm);
							setObjectLocation(rm,loc);
							setPortrayalForObject(rm);
						}
						for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.RIGHT).get(cellType)).getMyRMap().getright_out())
						{			    	
							RemoteAgent<Double2D> rm=e.r;
							Double2D loc=e.l;
							rm.setPos(loc);
							this.remove(rm);
							sm.schedule.scheduleOnce(rm);
							setObjectLocation(rm,loc);
							setPortrayalForObject(rm);
						}
						for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).get(cellType)).getMyRMap().getcorner_out_up_right_diag_center())
						{			    	
							RemoteAgent<Double2D> rm=e.r;
							Double2D loc=e.l;
							rm.setPos(loc);
							this.remove(rm);
							sm.schedule.scheduleOnce(rm);
							setObjectLocation(rm,loc);
							setPortrayalForObject(rm);
						}
					}
					else
						if(position == MyCellInterface.CORNER_DIAG_DOWN_RIGHT)
						{
							for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).get(cellType)).getMyRMap().getcorner_out_down_right_diag_center())
							{			    	
								RemoteAgent<Double2D> rm=e.r;
								Double2D loc=e.l;
								rm.setPos(loc);
								this.remove(rm);
								sm.schedule.scheduleOnce(rm);
								setObjectLocation(rm,loc);
								setPortrayalForObject(rm);
							}
						}
						else
							if(position == MyCellInterface.DOWN)
							{
								for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).get(cellType)).getMyRMap().getcorner_out_down_left_diag_center())
								{			    	
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
								for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.DOWN).get(cellType)).getMyRMap().getdown_out())
								{			    	
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
								for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).get(cellType)).getMyRMap().getcorner_out_down_right_diag_center())
								{			    	
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
							}
							else
								if(position == MyCellInterface.CORNER_DIAG_DOWN_LEFT)
								{
									for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).get(cellType)).getMyRMap().getcorner_out_down_left_diag_center())
									{			    	
										RemoteAgent<Double2D> rm=e.r;
										Double2D loc=e.l;
										rm.setPos(loc);
										this.remove(rm);
										sm.schedule.scheduleOnce(rm);
										setObjectLocation(rm,loc);
										setPortrayalForObject(rm);
									}
								}
								else
									if(position == MyCellInterface.LEFT)
									{
										for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).get(cellType)).getMyRMap().getcorner_out_up_left_diag_center())
										{			    	
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
										for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.LEFT).get(cellType)).getMyRMap().getleft_out())
										{			    	
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
										for(Entry<Double2D> e: (RegionDoubleLB)((MyCellDoubleField)listGrid.get(MyCellInterface.CORNER_DIAG_UP_LEFT).get(cellType)).getMyRMap().getcorner_out_down_left_diag_center())
										{			    	
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
									}		
	}

	private void clearArrivedOut(MyCellDoubleField mc) {

		int position = mc.getPosition();
		if(position == MyCellInterface.CORNER_DIAG_UP_LEFT)
		{
			mc.getMyRMap().corner_out_down_right_diag_center.clear();
		}
		else
			if(position == MyCellInterface.UP)
			{
				mc.getMyRMap().corner_out_down_left_diag_center.clear();
				mc.getMyRMap().corner_out_down_left_diag_down.clear();
				mc.getMyRMap().down_out.clear();
				mc.getMyRMap().corner_out_down_right_diag_down.clear();
				mc.getMyRMap().corner_out_down_right_diag_center.clear();
			}
			else
				if(position == MyCellInterface.CORNER_DIAG_UP_RIGHT)
				{
					mc.getMyRMap().corner_out_down_left_diag_center.clear();
				}
				else
					if(position == MyCellInterface.RIGHT)
					{
						mc.getMyRMap().corner_out_up_left_diag_center.clear();
						mc.getMyRMap().corner_out_up_left_diag_left.clear();
						mc.getMyRMap().left_out.clear();
						mc.getMyRMap().corner_out_down_left_diag_left.clear();
						mc.getMyRMap().corner_out_down_left_diag_center.clear();
					}
					else
						if(position == MyCellInterface.CORNER_DIAG_DOWN_RIGHT)
						{
							mc.getMyRMap().corner_out_up_left_diag_center.clear();
						}
						else
							if(position == MyCellInterface.DOWN)
							{
								mc.getMyRMap().corner_out_up_left_diag_center.clear();
								mc.getMyRMap().corner_out_up_left_diag_up.clear();
								mc.getMyRMap().up_out.clear();
								mc.getMyRMap().corner_out_up_right_diag_up.clear();
								mc.getMyRMap().corner_out_up_right_diag_center.clear();
							}
							else
								if(position == MyCellInterface.CORNER_DIAG_DOWN_LEFT)
								{
									mc.getMyRMap().corner_out_up_right_diag_center.clear();
								}
								else
									if(position == MyCellInterface.LEFT)
									{
										mc.getMyRMap().corner_out_up_right_diag_center.clear();
										mc.getMyRMap().corner_out_up_right_diag_right.clear();
										mc.getMyRMap().right_out.clear();
										mc.getMyRMap().corner_out_down_right_diag_right.clear();
										mc.getMyRMap().corner_out_down_right_diag_center.clear();
									}
	}

	private void preparePublishUnion() {

		for(Integer pos : toSendForUnion.keySet())
		{	
			if(toSendForUnion.get(pos)!=null)
			{
				MyCellDoubleField md = (MyCellDoubleField)toSendForUnion.get(pos);

				if(md.getPosition() == MyCellInterface.CORNER_DIAG_UP_LEFT)
				{
					/*
	    			if(isSplitted)
	    			{
	    				DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
	    						new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
	    								md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
	    				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_right_diag_center);    					
	    			}*/

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_right_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.RIGHT))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_right);
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_right);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_right);
						}
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);    	 

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
						}
					}
					if(md.getPositionPublish().get(MyCellInterface.DOWN))
					{

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_down);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_down);
						}

						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_down);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
						}
					}
					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_center);
					}
					else{
						DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_left_center);
					}
				}

				if(md.getPosition() == MyCellInterface.UP)
				{

					if(isSplitted)
					{
						/*
						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_diag_left);
						 */
						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_left_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_diag_down);

						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);

						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_diag_down);
						 */
						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_right_diag_center);	
						/*
						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_diag_right);
						 */
					}
				}

				if(md.getPosition() == MyCellInterface.CORNER_DIAG_UP_RIGHT)
				{
					/*
	    			if(isSplitted)
	    			{
	    				DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
	    						new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
	    								md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
	    				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_left_diag_center);
	    			}*/

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_left_center);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_right_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.DOWN))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_down);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_down);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_down);
						}
					}	
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);
						}
					}
					if(md.getPositionPublish().get(MyCellInterface.LEFT))
					{
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_left);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_left);
						}
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left);
						DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_left);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
						}
					}
				}

				if(md.getPosition() == MyCellInterface.RIGHT)
				{

					if(isSplitted)
					{

						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_left_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_diag_up);

						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_diag_left);

						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);

						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_diag_left);
						 */
						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_left_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_down_left_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_diag_down);
						 */
					}
				}

				if(md.getPosition() == MyCellInterface.CORNER_DIAG_DOWN_RIGHT)
				{
					/*
	    			if(isSplitted)
	    			{		
	    				DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
	    						new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
	    								md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
	    				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_left_diag_center);
	    			}*/

					if(md.getPositionPublish().get(MyCellInterface.UP))
					{

						if(isSplitted)
						{	
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_up);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_up);
						}
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_up);
						DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_up);
					}
					else
					{

						if(isSplitted)
						{	

							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
						}

						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);
						DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_right_center);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_left_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.LEFT))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_left);

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_left);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_left);
						}
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
						}
					}
				}

				if(md.getPosition() == MyCellInterface.DOWN)
				{
					if(isSplitted)
					{
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_diag_left);
						 */
						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_left_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_left_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_diag_up);

						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);

						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_diag_up);
						 */
						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_right_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_diag_right);
						 */
					}
				}

				if(md.getPosition() == MyCellInterface.CORNER_DIAG_DOWN_LEFT)
				{
					/*
	    			if(isSplitted)
	    			{
	    				DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
	    						new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
	    								md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
	    				hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_right_diag_center);
	    			}*/

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_left_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.UP))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_up);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_up);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_up);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_up);
						}
					}
					else{
						DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
						}
					}
					if(md.getPositionPublish().get(MyCellInterface.RIGHT))
					{

						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_right);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_right);
						}
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_right);
						DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_right);
					}
					else
					{
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
						}
						else
						{
							RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
							empty.clear();
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											empty, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
						}
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);
						DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
					}
					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_center);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_right_center);
					}
				}

				if(md.getPosition() == MyCellInterface.LEFT)
				{
					if(isSplitted)
					{
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_diag_up);
						 */
						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_right_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_up_right_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_diag_right);

						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);

						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_diag_right);
						 */
						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_right_diag_center);
						/*
						DistributedRegion<Double, Double2D> dr_corner_down_right_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_diag_down);
						 */
					}
				}
			}
		}
	}

	private void preparePublishMicroCell() {

		for(Integer pos : listGrid.keySet())
		{	
			HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

			for(CellType ct : hm.keySet())
			{	
				if(!ct.equals(cellType))
				{
					MyCellDoubleField md = (MyCellDoubleField) hm.get(ct);

					if(md.getPosition() == MyCellInterface.CORNER_DIAG_UP_LEFT)
					{
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_right_diag_center);    					
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_right);
							}
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);    	    				
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_down);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_down);
							}

							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_down);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
						}
					}

					if(md.getPosition() == MyCellInterface.UP)
					{
						hashUpdatesPosition.get(MyCellInterface.UP).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							/*
							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_diag_left);*/
							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_down_left_diag_center);
							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_diag_down);

							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);

							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_diag_down);
							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_right_diag_center);
							/*
							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_diag_right);*/
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_diag_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_diag_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_diag_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_diag_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
							}
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
						}
					}

					if(md.getPosition() == MyCellInterface.CORNER_DIAG_UP_RIGHT)
					{
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_left_diag_center);
						}

						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_down_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_down);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_down);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_down);
							}
						}	
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_down);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_down);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_left);
							}
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left);
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_left);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);

							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
							}
						}
					}

					if(md.getPosition() == MyCellInterface.RIGHT)
					{
						hashUpdatesPosition.get(MyCellInterface.RIGHT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{

							DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_left_diag_center);
							/*DistributedRegion<Double, Double2D> dr_corner_up_left_diag_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_diag_up);*/
							DistributedRegion<Double, Double2D> dr_corner_up_left_diag_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_diag_left);

							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);

							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_diag_left);
							DistributedRegion<Double, Double2D> dr_corner_down_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_left_diag_center);
							/*DistributedRegion<Double, Double2D> dr_corner_down_left_diag_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_diag_down);*/

						}

						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_left_diag_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_diag_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
							}
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_left_diag_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_diag_down);
							}
							else{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
							}
						}
					}

					if(md.getPosition() == MyCellInterface.CORNER_DIAG_DOWN_RIGHT)
					{
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{		
							DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_left_diag_center);
						}

						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							if(isSplitted || unionDone)
							{	

								DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_up);
							}
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_up);
						}
						else
						{
							if(isSplitted || unionDone)
							{	

								DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
							}

							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
						}

						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_left);

							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_left);
							}
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_left_left);
							}
						}
					}

					if(md.getPosition() == MyCellInterface.DOWN)
					{
						hashUpdatesPosition.get(MyCellInterface.DOWN).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							/*DistributedRegion<Double, Double2D> dr_corner_up_left_diag_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_diag_left);*/
							DistributedRegion<Double, Double2D> dr_corner_up_left_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_left_diag_center);
							DistributedRegion<Double, Double2D> dr_corner_up_left_diag_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_diag_up);

							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);

							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_diag_up);
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_up_right_diag_center);
							/*
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_diag_right);*/
						}

						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							if(isSplitted || unionDone){
								DistributedRegion<Double, Double2D> dr_corner_up_right_diag_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_diag_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);
							}
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_left_diag_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_diag_left);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
							}
						}
					}

					if(md.getPosition() == MyCellInterface.CORNER_DIAG_DOWN_LEFT)
					{
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_right_diag_center);
						}

						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_up_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_up);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_up);
							}
						}
						else{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_up);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_up);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.RIGHT))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_up_right_right);
							}
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_right);
						}
						else
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_right);
							}
							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_right);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_center);
						}
						else
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_right_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_left_center);
						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
						}
					}

					if(md.getPosition() == MyCellInterface.LEFT)
					{
						hashUpdatesPosition.get(MyCellInterface.LEFT).setNumAgentExternalCell(countAgent(md));
						if(isSplitted)
						{
							/*
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_up = 
	    							new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
	    									md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
	    					hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_diag_up);*/
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_right_diag_center);
							DistributedRegion<Double, Double2D> dr_corner_up_right_diag_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_diag_right);

							DistributedRegion<Double, Double2D> dr_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
											md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right);

							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_right = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_right_diag_right);
							DistributedRegion<Double, Double2D> dr_corner_down_right_diag_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_right_diag_center);
							/*DistributedRegion<Double, Double2D> dr_corner_down_right_diag_down = 
	    							new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
	    									md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
	    					hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_diag_down);*/
						}

						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_up_left_center);
						}

						if(md.getPositionPublish().get(MyCellInterface.UP))
						{
							DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
							DistributedRegion<Double, Double2D> dr_up = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
											md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_up_right_diag_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_diag_up);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
							}
						}
						if(md.getPositionPublish().get(MyCellInterface.DOWN))
						{
							if(isSplitted || unionDone)
							{
								DistributedRegion<Double, Double2D> dr_corner_down_right_diag_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_diag_down);
							}
							else
							{
								RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
								empty.clear();
								DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
										new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
												empty, (sm.schedule.getSteps()-1),cellType);
								hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);	
							}
							DistributedRegion<Double, Double2D> dr_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
											md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
							DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_down, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
						}
						if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_center = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_left_center);

						}
						if(md.getPositionPublish().get(MyCellInterface.LEFT))
						{
							DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
							DistributedRegion<Double, Double2D> dr_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
											md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
							DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
						}
					}
				}
			}
		}
	}

	private void preparePublishCenter(){

		for(Integer pos : listGrid.keySet())
		{	
			HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

			MyCellDoubleField md = (MyCellDoubleField)listGrid.get(pos).get(cellType);

			if(md != null)
			{
				//PUBLISH POSITION CORNER UP LEFT
				if(md.getPosition()==MyCellInterface.CORNER_DIAG_UP_LEFT){

					//LEFT MINE UP
					if(md.getPositionPublish().get(MyCellInterface.LEFT))
					{
						DistributedRegion<Double, Double2D> dr_left_corner_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left_corner_down_diag);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
						DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_left);
					}
					else
					{
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_left);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_down_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left);
						DistributedRegion<Double, Double2D> dr_corner_up_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_left);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
					{						
						DistributedRegion<Double, Double2D> dr_corner_left_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_corner_left_up);
					}

					if(md.getPositionPublish().get(MyCellInterface.UP))
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_left_up);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
						DistributedRegion<Double, Double2D> dr_corner_right_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_right_up_diag);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_left_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_left_up);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_up);
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_up);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										empty,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_up_right_up);
					}
				}

				//PUBLISH POSITION UP
				if(md.getPosition()==MyCellInterface.UP){

					if(md.getPositionPublish().get(MyCellInterface.UP))
					{
						DistributedRegion<Double, Double2D> dr_corner_left_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_left_up_diag);

						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);

						DistributedRegion<Double, Double2D> dr_corner_right_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_right_up_diag);
					}
					else
					{
						if(positionForUnion != MyCellInterface.UP){
							DistributedRegion<Double, Double2D> dr_corner_left_up_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center,	(sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_left_up_diag);

							DistributedRegion<Double, Double2D> dr_corner_right_up_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_right_up_diag);
						}
					}
				}

				//PUBLISH POSITION CORNER UP RIGHT
				if(md.getPosition()==MyCellInterface.CORNER_DIAG_UP_RIGHT){

					if(md.getPositionPublish().get(MyCellInterface.UP))
					{
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left_corner_up_diag);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);
						DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_up);
					}
					else
					{
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_up);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left_corner_up_diag_up);
						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_up);
						DistributedRegion<Double, Double2D> dr_corner_up_right_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_up);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
					{	
						DistributedRegion<Double, Double2D> dr_corner_up_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_corner_up_right);
					}

					if(md.getPositionPublish().get(MyCellInterface.RIGHT)){
						DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_up_right_right);

						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);

						DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_right_down_diag);

					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_up_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_corner_up_right_right);
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right);
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_right);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_right_corner_down_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right_corner_down_diag_right);
					}
				}

				//PUBLISH POSITION RIGHT
				if(md.getPosition()==MyCellInterface.RIGHT){

					if(md.getPositionPublish().get(MyCellInterface.RIGHT)){
						DistributedRegion<Double, Double2D> dr_right_corner_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right_corner_up_diag);

						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);

						DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_right_down_diag);

					}
					else
					{
						if(positionForUnion != MyCellInterface.RIGHT){
							DistributedRegion<Double, Double2D> dr_right_corner_up_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
											md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right_corner_up_diag);

							DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_right_down_diag);
						}
					}
				}

				//PUBLISH POSITION CORNER DOWN RIGHT
				if(md.getPosition()==MyCellInterface.CORNER_DIAG_DOWN_RIGHT){

					if(md.getPositionPublish().get(MyCellInterface.RIGHT)){
						DistributedRegion<Double, Double2D> dr_right_corner_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right_corner_up_diag);

						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);

						DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);

					}
					else
					{
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_right_diag_right);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_right_corner_up_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_right_corner_up_diag_right);
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_right);
						DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_right);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
					{	
						DistributedRegion<Double, Double2D> dr_corner_down_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_corner_down_right);
					}

					if(md.getPositionPublish().get(MyCellInterface.DOWN))
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_right_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_left_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_left_down_diag);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_right_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_down);
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_left_diag_down);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_left_corner_down_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_left_corner_down_diag_down);
					}
				}

				//PUBLISH POSITION DOWN
				if(md.getPosition()==MyCellInterface.DOWN){

					if(md.getPositionPublish().get(MyCellInterface.DOWN))
					{
						DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_right_down_diag);

						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);

						DistributedRegion<Double, Double2D> dr_corner_left_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_left_down_diag);
					}
					else
					{
						if(positionForUnion != MyCellInterface.DOWN){
							DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
											md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_right_down_diag);		    			
							DistributedRegion<Double, Double2D> dr_corner_left_down_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_left_down_diag);
						}
					}
				}

				//PUBLISH POSITION CORNER DOWN LEFT
				if(md.getPosition()==MyCellInterface.CORNER_DIAG_DOWN_LEFT){

					if(md.getPositionPublish().get(MyCellInterface.DOWN))
					{
						DistributedRegion<Double, Double2D> dr_corner_right_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_right_down_diag);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
					}
					else
					{
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_down_right_diag_down);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_right_corner_down_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_right_corner_down_diag_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_down);
					}

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
					{	
						DistributedRegion<Double, Double2D> dr_corner_down_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_corner_down_left);
					}

					if(md.getPositionPublish().get(MyCellInterface.LEFT)){

						DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_down_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left_corner_up_diag);
					}
					else
					{
						DistributedRegion<Double, Double2D> dr_corner_down_left_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_left);
						RegionDoubleLB empty = ((RegionDoubleLB)md.getMyRMap().corner_out_up_left_diag_left);
						empty.clear();
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										empty, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_left_corner_up_diag_left);
					}
				}

				//PUBLISH POSITION LEFT
				if(md.getPosition()==MyCellInterface.LEFT){

					if(md.getPositionPublish().get(MyCellInterface.LEFT)){
						DistributedRegion<Double, Double2D> dr_corner_left_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_left_down_diag);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left_corner_up_diag);
					}
					else
					{
						if(positionForUnion != MyCellInterface.LEFT){
							DistributedRegion<Double, Double2D> dr_corner_left_down_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
											md.getMyRMap().corner_out_down_left_diag_center,	(sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_left_down_diag);
							DistributedRegion<Double, Double2D> dr_left_corner_up_diag = 
									new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
											md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
							hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left_corner_up_diag);
						}
					}
				}

				if(md.getPosition() == MyCellInterface.CENTER){

					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_LEFT)){
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_LEFT).add(dr_left_corner_up_diag_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.UP)){
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_left_corner_up_diag_up);

						DistributedRegion<Double, Double2D> dr_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().up_mine,
										md.getMyRMap().up_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_up);

						DistributedRegion<Double, Double2D> dr_right_corner_up_diag_up = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_up, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.UP).add(dr_right_corner_up_diag_up);

					}
					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_UP_RIGHT)){
						DistributedRegion<Double, Double2D> dr_right_corner_up_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_UP_RIGHT).add(dr_right_corner_up_diag_center);
					}

					if(md.getPositionPublish().get(MyCellInterface.RIGHT)){
						DistributedRegion<Double, Double2D> dr_right_corner_up_diag_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_right,
										md.getMyRMap().corner_out_up_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right_corner_up_diag_right);
						DistributedRegion<Double, Double2D> dr_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().right_mine,
										md.getMyRMap().right_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_right);
						DistributedRegion<Double, Double2D> dr_corner_down_right_right = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_right, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.RIGHT).add(dr_corner_down_right_right);
					}
					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT)){
						DistributedRegion<Double, Double2D> dr_right_corner_down_diag = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT).add(dr_right_corner_down_diag);
					}
					if(md.getPositionPublish().get(MyCellInterface.DOWN)){
						DistributedRegion<Double, Double2D> dr_corner_right_down_diag_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_right,
										md.getMyRMap().corner_out_down_right_diag_down, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_right_down_diag_down);
						DistributedRegion<Double, Double2D> dr_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().down_mine,
										md.getMyRMap().down_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_down);
						DistributedRegion<Double, Double2D> dr_corner_down_left_down = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_down,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.DOWN).add(dr_corner_down_left_down);
					}
					if(md.getPositionPublish().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT)){
						DistributedRegion<Double, Double2D> dr_left_corner_down_diag_center = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_center, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.CORNER_DIAG_DOWN_LEFT).add(dr_left_corner_down_diag_center);
					}
					if(md.getPositionPublish().get(MyCellInterface.LEFT)){
						DistributedRegion<Double, Double2D> dr_corner_left_down_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_down_left,
										md.getMyRMap().corner_out_down_left_diag_left,	(sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_corner_left_down_diag_left);
						DistributedRegion<Double, Double2D> dr_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().left_mine,
										md.getMyRMap().left_out, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left);
						DistributedRegion<Double, Double2D> dr_left_corner_up_diag_left = 
								new DistributedRegion<Double, Double2D>(md.getMyRMap().corner_mine_up_left,
										md.getMyRMap().corner_out_up_left_diag_left, (sm.schedule.getSteps()-1),cellType);
						hashUpdatesPosition.get(MyCellInterface.LEFT).add(dr_left_corner_up_diag_left);
					}
				}
			}
		}
	}

	private void updateInternalMine(MyCellDoubleField md) {

		Class o=md.getMyRMap().getClass();

		Field[] fields = o.getDeclaredFields();
		for (int z = 0; z < fields.length; z++)
		{
			fields[z].setAccessible(true);
			try
			{
				String name=fields[z].getName();
				Method method = o.getMethod("get"+name, null);
				Object returnValue = method.invoke(md.getMyRMap(), null);
				if(returnValue!=null)
				{
					Region<Double, Double2D> region=((Region<Double, Double2D>)returnValue);

					if(name.contains("mine"))
					{
						for(Entry<Double2D> e: region)
						{

							if(name.contains("left_mine") && !md.getPositionGood().get(MyCellInterface.LEFT))
							{	

								RemoteAgent<Double2D> rm=e.r;
								Double2D loc=e.l;
								rm.setPos(loc);
								this.remove(rm);
								sm.schedule.scheduleOnce(rm);
								setObjectLocation(rm,loc);
								setPortrayalForObject(rm);
							}
							else
								if(name.contains("right_mine") && !md.getPositionGood().get(MyCellInterface.RIGHT))
								{
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
								else
									if(name.contains("up_mine") && !md.getPositionGood().get(MyCellInterface.UP))
									{
										RemoteAgent<Double2D> rm=e.r;
										Double2D loc=e.l;
										rm.setPos(loc);
										this.remove(rm);
										sm.schedule.scheduleOnce(rm);
										setObjectLocation(rm,loc);
										setPortrayalForObject(rm);
									}
									else
										if(name.contains("down_mine") && !md.getPositionGood().get(MyCellInterface.DOWN))
										{
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
										else
											if(name.contains("corner_mine_down_left") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
											{
												RemoteAgent<Double2D> rm=e.r;
												Double2D loc=e.l;
												rm.setPos(loc);
												this.remove(rm);
												sm.schedule.scheduleOnce(rm);
												setObjectLocation(rm,loc);
												setPortrayalForObject(rm);
											}
											else
												if(name.contains("corner_mine_down_right") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
												{
													RemoteAgent<Double2D> rm=e.r;
													Double2D loc=e.l;

													rm.setPos(loc);
													this.remove(rm);
													sm.schedule.scheduleOnce(rm);
													setObjectLocation(rm,loc);
													setPortrayalForObject(rm);
												}
												else
													if(name.contains("corner_mine_up_left") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
													{
														RemoteAgent<Double2D> rm=e.r;
														Double2D loc=e.l;
														rm.setPos(loc);
														this.remove(rm);
														sm.schedule.scheduleOnce(rm);
														setObjectLocation(rm,loc);
														setPortrayalForObject(rm);
													}
													else
														if(name.contains("corner_mine_up_right") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
														{
															RemoteAgent<Double2D> rm=e.r;
															Double2D loc=e.l;
															rm.setPos(loc);
															this.remove(rm);
															sm.schedule.scheduleOnce(rm);
															setObjectLocation(rm,loc);
															setPortrayalForObject(rm);
														}
						}
					}
				}
			}
			catch (IllegalArgumentException e){e.printStackTrace();} 
			catch (IllegalAccessException e) {e.printStackTrace();} 
			catch (SecurityException e) {e.printStackTrace();} 
			catch (NoSuchMethodException e) {e.printStackTrace();} 
			catch (InvocationTargetException e) {e.printStackTrace();}
		}
	}

	private void updateInternalOut(MyCellInterface md) {

		Class o=md.getMyRMap().getClass();

		Field[] fields = o.getDeclaredFields();
		for (int z = 0; z < fields.length; z++)
		{
			fields[z].setAccessible(true);
			try
			{
				String name=fields[z].getName();
				Method method = o.getMethod("get"+name, null);
				Object returnValue = method.invoke(md.getMyRMap(), null);
				if(returnValue!=null)
				{
					Region<Double, Double2D> region=((Region<Double, Double2D>)returnValue);

					if(name.contains("out"))
					{
						for(Entry<Double2D> e: region)
						{

							if(name.contains("left_out") && !md.getPositionGood().get(MyCellInterface.LEFT))
							{	

								RemoteAgent<Double2D> rm=e.r;
								Double2D loc=e.l;
								rm.setPos(loc);
								this.remove(rm);
								sm.schedule.scheduleOnce(rm);
								setObjectLocation(rm,loc);
								setPortrayalForObject(rm);
							}
							else
								if(name.contains("right_out") && !md.getPositionGood().get(MyCellInterface.RIGHT))
								{
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
								else
									if(name.contains("up_out") && !md.getPositionGood().get(MyCellInterface.UP))
									{
										RemoteAgent<Double2D> rm=e.r;
										Double2D loc=e.l;
										rm.setPos(loc);
										this.remove(rm);
										sm.schedule.scheduleOnce(rm);
										setObjectLocation(rm,loc);
										setPortrayalForObject(rm);
									}
									else
										if(name.contains("down_out") && !md.getPositionGood().get(MyCellInterface.DOWN))
										{
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
										else
											if(name.contains("corner_out_down_left_diag_center") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
											{
												RemoteAgent<Double2D> rm=e.r;
												Double2D loc=e.l;
												rm.setPos(loc);
												this.remove(rm);
												sm.schedule.scheduleOnce(rm);
												setObjectLocation(rm,loc);
												setPortrayalForObject(rm);
											}
											else
												if(name.contains("corner_out_down_left_diag_down") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_DOWN))
												{
													RemoteAgent<Double2D> rm=e.r;
													Double2D loc=e.l;
													rm.setPos(loc);
													this.remove(rm);
													sm.schedule.scheduleOnce(rm);
													setObjectLocation(rm,loc);
													setPortrayalForObject(rm);
												}
												else
													if(name.contains("corner_out_down_left_diag_left") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_LEFT))
													{
														RemoteAgent<Double2D> rm=e.r;
														Double2D loc=e.l;
														rm.setPos(loc);
														this.remove(rm);
														sm.schedule.scheduleOnce(rm);
														setObjectLocation(rm,loc);
														setPortrayalForObject(rm);
													}
													else
														if(name.contains("corner_out_down_right_diag_center") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
														{
															RemoteAgent<Double2D> rm=e.r;
															Double2D loc=e.l;

															rm.setPos(loc);
															this.remove(rm);
															sm.schedule.scheduleOnce(rm);
															setObjectLocation(rm,loc);
															setPortrayalForObject(rm);
														}
														else 
															if(name.contains("corner_out_down_right_diag_down") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_DOWN))
															{
																RemoteAgent<Double2D> rm=e.r;
																Double2D loc=e.l;

																rm.setPos(loc);
																this.remove(rm);
																sm.schedule.scheduleOnce(rm);
																setObjectLocation(rm,loc);
																setPortrayalForObject(rm);
															}
															else
																if(name.contains("corner_out_down_right_diag_right") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_RIGHT))
																{
																	RemoteAgent<Double2D> rm=e.r;
																	Double2D loc=e.l;

																	rm.setPos(loc);
																	this.remove(rm);
																	sm.schedule.scheduleOnce(rm);
																	setObjectLocation(rm,loc);
																	setPortrayalForObject(rm);
																}
																else
																	if(name.contains("corner_out_up_left_diag_center") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
																	{
																		RemoteAgent<Double2D> rm=e.r;
																		Double2D loc=e.l;
																		rm.setPos(loc);
																		this.remove(rm);
																		sm.schedule.scheduleOnce(rm);
																		setObjectLocation(rm,loc);
																		setPortrayalForObject(rm);
																	}
																	else
																		if(name.contains("corner_out_up_left_diag_up") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_UP))
																		{
																			RemoteAgent<Double2D> rm=e.r;
																			Double2D loc=e.l;
																			if(rm.getId().equals("2-2-2245")){
																				System.out
																				.println("INSERITO!!!!!!!!!!!!!!!");
																			}
																			rm.setPos(loc);
																			this.remove(rm);
																			sm.schedule.scheduleOnce(rm);
																			setObjectLocation(rm,loc);
																			setPortrayalForObject(rm);
																		}
																		else
																			if(name.contains("corner_out_up_left_diag_left") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_LEFT))
																			{
																				RemoteAgent<Double2D> rm=e.r;
																				Double2D loc=e.l;
																				rm.setPos(loc);
																				this.remove(rm);
																				sm.schedule.scheduleOnce(rm);
																				setObjectLocation(rm,loc);
																				setPortrayalForObject(rm);
																			}
																			else
																				if(name.contains("corner_out_up_right_diag_center") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
																				{
																					RemoteAgent<Double2D> rm=e.r;
																					Double2D loc=e.l;
																					rm.setPos(loc);
																					this.remove(rm);
																					sm.schedule.scheduleOnce(rm);
																					setObjectLocation(rm,loc);
																					setPortrayalForObject(rm);
																				}
																				else
																					if(name.contains("corner_out_up_right_diag_up") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_UP))
																					{
																						RemoteAgent<Double2D> rm=e.r;
																						Double2D loc=e.l;
																						rm.setPos(loc);
																						this.remove(rm);
																						sm.schedule.scheduleOnce(rm);
																						setObjectLocation(rm,loc);
																						setPortrayalForObject(rm);
																					}
																					else
																						if(name.contains("corner_out_up_right_diag_right") && !md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_RIGHT))
																						{
																							RemoteAgent<Double2D> rm=e.r;
																							Double2D loc=e.l;
																							rm.setPos(loc);
																							this.remove(rm);
																							sm.schedule.scheduleOnce(rm);
																							setObjectLocation(rm,loc);
																							setPortrayalForObject(rm);
																						}
						}
					}
				}
			}
			catch (IllegalArgumentException e){e.printStackTrace();} 
			catch (IllegalAccessException e) {e.printStackTrace();} 
			catch (SecurityException e) {e.printStackTrace();} 
			catch (NoSuchMethodException e) {e.printStackTrace();} 
			catch (InvocationTargetException e) {e.printStackTrace();}
		}
	}

	/**
	 * This method takes updates from box and schedules every agent in the regions out.
	 * Every agent in the regions mine is compared with every agent in the updates_cacheLB:
	 * if they are not equals, that in box mine is added.
	 * 
	 * @param box A Distributed Region that contains the updates
	 */
	private void verifyUpdates(UpdatePositionDoubleField<DistributedRegion<Double, Double2D>> super_box)
	{
		ArrayList<Region<Double, Double2D>> updates_out = new ArrayList<Region<Double, Double2D>>();

		for(DistributedRegion<Double, Double2D> sb : super_box){

			Region<Double, Double2D> r_mine=sb.out;
			Region<Double, Double2D> r_out=sb.mine;

			for(Entry<Double2D> e_m: r_mine)
			{
				RemoteAgent<Double2D> rm=e_m.r;
				sm.schedule.scheduleOnce(rm);

				((DistributedState<Double2D>)sm).addToField(rm,e_m.l);
			}

			updates_out.add(r_out);
		}

		updates_cacheLB.add(updates_out);
	}

	private void memorizeRegionOut(MyCellDoubleField md)
	{
		ArrayList<Region<Double, Double2D>> updates_out = new ArrayList<Region<Double, Double2D>>();

		Class o=md.getMyRMap().getClass();

		Field[] fields = o.getDeclaredFields();
		for (int z = 0; z < fields.length; z++)
		{
			fields[z].setAccessible(true);
			try
			{
				String name=fields[z].getName();
				Method method = o.getMethod("get"+name, null);
				Object returnValue = method.invoke(md.getMyRMap(), null);
				if(returnValue!=null)
				{
					Region<Double, Double2D> region=((Region<Double, Double2D>)returnValue);

					if(name.contains("out"))
					{		
						if(name.contains("left_out") && md.getPositionGood().get(MyCellInterface.LEFT))
						{
							updates_out.add(region.clone());
						}
						else
							if(name.contains("right_out") && md.getPositionGood().get(MyCellInterface.RIGHT))
							{
								updates_out.add(region.clone());
							}
							else
								if(name.contains("up_out") && md.getPositionGood().get(MyCellInterface.UP))
								{
									updates_out.add(region.clone());
								}
								else
									if(name.contains("down_out") && md.getPositionGood().get(MyCellInterface.DOWN))
									{
										updates_out.add(region.clone());
									}
									else
										if(name.contains("corner_out_down_left_diag_left") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_LEFT))
										{							
											updates_out.add(region.clone());
										}
										else
											if(name.contains("corner_out_down_left_diag_down") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_DOWN))
											{
												updates_out.add(region.clone());
											}
											else
												if(name.contains("corner_out_down_left_diag_center") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
												{
													updates_out.add(region.clone());
												}
												else
													if(name.contains("corner_out_down_right_diag_right") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_RIGHT))
													{
														updates_out.add(region.clone());
													}
													else
														if(name.contains("corner_out_down_right_diag_down") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_DOWN))
														{
															updates_out.add(region.clone());
														}
														else
															if(name.contains("corner_out_down_right_diag_center") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
															{
																updates_out.add(region.clone());
															}
															else
																if(name.contains("corner_out_up_left_diag_left") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_LEFT))
																{
																	updates_out.add(region.clone());
																}
																else
																	if(name.contains("corner_out_up_left_diag_up") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_UP))
																	{
																		updates_out.add(region.clone());
																	}
																	else
																		if(name.contains("corner_out_up_left_diag_center") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
																		{
																			updates_out.add(region.clone());
																		}
																		else
																			if(name.contains("corner_out_up_right_diag_right") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_RIGHT))
																			{
																				updates_out.add(region.clone());
																			}
																			else
																				if(name.contains("corner_out_up_right_diag_up") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_UP))
																				{
																					updates_out.add(region.clone());
																				}
																				else
																					if(name.contains("corner_out_up_right_diag_center") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
																					{
																						updates_out.add(region.clone());
																					}
					}
				}
			}
			catch (IllegalArgumentException e){e.printStackTrace();} 
			catch (IllegalAccessException e) {e.printStackTrace();} 
			catch (SecurityException e) {e.printStackTrace();} 
			catch (NoSuchMethodException e) {e.printStackTrace();} 
			catch (InvocationTargetException e) {e.printStackTrace();}
		}
		updates_cacheLB.add(updates_out);
	}

	/**
	 * This method, written with Java Reflect, follows two logical ways for all the regions:
	 * - if a region is an out one, the agent's location is updated and it's insert a new Entry 
	 * 		in the updates_cacheLB (cause the agent is moving out and it's important to maintain the information
	 * 		for the next step)
	 * - if a region is a mine one, the agent's location is updated and the agent is scheduled.
	 */
	private void updateFields(MyCellInterface md)
	{
		Class o=md.getMyRMap().getClass();

		Field[] fields = o.getDeclaredFields();
		for (int z = 0; z < fields.length; z++)
		{
			fields[z].setAccessible(true);
			try
			{
				String name=fields[z].getName();
				Method method = o.getMethod("get"+name, null);
				Object returnValue = method.invoke(md.getMyRMap(), null);
				if(returnValue!=null)
				{
					Region<Double, Double2D> region=((Region<Double, Double2D>)returnValue);
					if(name.contains("out"))
					{
						for(Entry<Double2D> e: region)
						{
							RemoteAgent<Double2D> rm=e.r;
							rm.setPos(e.l);
							this.remove(rm);
						} 
					}
					else
						if(name.contains("mine"))
						{
							for(Entry<Double2D> e: region)
							{			    	
								if(name.contains("left_mine") && md.getPositionGood().get(MyCellInterface.LEFT))
								{	
									RemoteAgent<Double2D> rm=e.r;
									Double2D loc=e.l;
									rm.setPos(loc);
									this.remove(rm);
									sm.schedule.scheduleOnce(rm);
									setObjectLocation(rm,loc);
									setPortrayalForObject(rm);
								}
								else
									if(name.contains("right_mine") && md.getPositionGood().get(MyCellInterface.RIGHT))
									{
										RemoteAgent<Double2D> rm=e.r;
										Double2D loc=e.l;
										rm.setPos(loc);
										this.remove(rm);
										sm.schedule.scheduleOnce(rm);
										setObjectLocation(rm,loc);
										setPortrayalForObject(rm);
									}
									else
										if(name.contains("up_mine") && md.getPositionGood().get(MyCellInterface.UP))
										{
											RemoteAgent<Double2D> rm=e.r;
											Double2D loc=e.l;
											rm.setPos(loc);
											this.remove(rm);
											sm.schedule.scheduleOnce(rm);
											setObjectLocation(rm,loc);
											setPortrayalForObject(rm);
										}
										else
											if(name.contains("down_mine") && md.getPositionGood().get(MyCellInterface.DOWN))
											{
												RemoteAgent<Double2D> rm=e.r;
												Double2D loc=e.l;
												rm.setPos(loc);
												this.remove(rm);
												sm.schedule.scheduleOnce(rm);
												setObjectLocation(rm,loc);
												setPortrayalForObject(rm);
											}
											else
												if(name.contains("corner_mine_down_left") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
												{
													RemoteAgent<Double2D> rm=e.r;
													Double2D loc=e.l;
													rm.setPos(loc);
													this.remove(rm);
													sm.schedule.scheduleOnce(rm);
													setObjectLocation(rm,loc);
													setPortrayalForObject(rm);
												}
												else
													if(name.contains("corner_mine_down_right") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
													{
														RemoteAgent<Double2D> rm=e.r;									
														Double2D loc=e.l;
														rm.setPos(loc);
														this.remove(rm);
														sm.schedule.scheduleOnce(rm);
														setObjectLocation(rm,loc);
														setPortrayalForObject(rm);
													}
													else
														if(name.contains("corner_mine_up_left") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
														{
															RemoteAgent<Double2D> rm=e.r;
															Double2D loc=e.l;
															rm.setPos(loc);
															this.remove(rm);
															sm.schedule.scheduleOnce(rm);
															setObjectLocation(rm,loc);
															setPortrayalForObject(rm);
														}
														else
															if(name.contains("corner_mine_up_right") && md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
															{
																RemoteAgent<Double2D> rm=e.r;
																Double2D loc=e.l;
																rm.setPos(loc);
																this.remove(rm);
																sm.schedule.scheduleOnce(rm);
																setObjectLocation(rm,loc);
																setPortrayalForObject(rm);
															}
							}
						}
				}
			}
			catch (IllegalArgumentException e){e.printStackTrace();} 
			catch (IllegalAccessException e) {e.printStackTrace();} 
			catch (SecurityException e) {e.printStackTrace();} 
			catch (NoSuchMethodException e) {e.printStackTrace();} 
			catch (InvocationTargetException e) {e.printStackTrace();}
		}

	}

	/**
	 * This method, written with Java Reflect, provides to add the Remote Agent
	 * in the right Region.
	 * @param rm The Remote Agent to add
	 * @param location The new location of the Remote Agent
	 * @return true if the agent is added in right way
	 */
	private boolean setAgents(RemoteAgent<Double2D> rm,Double2D location, MyCellInterface md)
	{

		RegionMap<Double, Double2D> rmap =(RegionMap<Double, Double2D>) md.getMyRMap();
		
		MyCellDoubleField ms = (MyCellDoubleField) md;
		
		if(md.getPositionGood().get(MyCellInterface.LEFT))
		{
			Region<Double,Double2D> region = rmap.left_out;
			if(region.isMine(location.x,location.y))
			{   	 
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));
			}
		}

		if(md.getPositionGood().get(MyCellInterface.RIGHT))
		{
			Region<Double,Double2D> region = rmap.right_out;
			if(region.isMine(location.x,location.y))
			{   	 
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.UP))
		{
			Region<Double,Double2D> region = rmap.up_out;
			if(region.isMine(location.x,location.y))
			{   	 
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.DOWN))
		{
			Region<Double,Double2D> region = rmap.down_out;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_LEFT))
		{		
			Region<Double,Double2D> region = rmap.corner_out_down_left_diag_left;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT_DOWN))
		{
			Region<Double,Double2D> region = rmap.corner_out_down_left_diag_down;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_LEFT))
		{
			Region<Double,Double2D> region = rmap.corner_out_down_left_diag_center;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_RIGHT))
		{
			Region<Double,Double2D> region = rmap.corner_out_down_right_diag_right;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT_DOWN))
		{
			Region<Double,Double2D> region = rmap.corner_out_down_right_diag_down;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_DOWN_RIGHT))
		{
			Region<Double,Double2D> region = rmap.corner_out_down_right_diag_center;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_LEFT))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_left_diag_left;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT_UP))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_left_diag_up;
			if(region.isMine(location.x,location.y))
			{   	
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_LEFT))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_left_diag_center;
			if(region.isMine(location.x,location.y))
			{   
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_RIGHT))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_right_diag_right;
			if(region.isMine(location.x,location.y))
			{   	
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT_UP))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_right_diag_up;
			if(region.isMine(location.x,location.y))
			{   	
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}

		if(md.getPositionGood().get(MyCellInterface.CORNER_DIAG_UP_RIGHT))
		{
			Region<Double,Double2D> region = rmap.corner_out_up_right_diag_center;
			if(region.isMine(location.x,location.y))
			{   	
				outAgents.add(new Entry<Double2D>(rm, location));
				return  region.addAgents(new Entry<Double2D>(rm, location));	
			}
		}


		if(rmap.corner_mine_up_left.isMine(location.x,location.y))
		{   	 
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.corner_mine_up_left.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.up_mine.isMine(location.x,location.y))
		{   	 
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.up_mine.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.corner_mine_up_right.isMine(location.x,location.y))
		{   	
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.corner_mine_up_right.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.right_mine.isMine(location.x,location.y))
		{   	 
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.right_mine.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.corner_mine_down_right.isMine(location.x,location.y))
		{   	
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.corner_mine_down_right.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.down_mine.isMine(location.x,location.y))
		{   	
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.down_mine.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.corner_mine_down_left.isMine(location.x,location.y))
		{   	
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.corner_mine_down_left.addAgents(new Entry<Double2D>(rm, location));	
		}

		if(rmap.left_mine.isMine(location.x,location.y))
		{   	
			ms.getMyField().addAgents(new Entry<Double2D>(rm, location));
			return  rmap.left_mine.addAgents(new Entry<Double2D>(rm, location));	
		}

		return false;
	}
	
	private int countAgent(MyCellInterface mc){
		
		int counter = 0;
		
		counter = counter + ((RegionDoubleLB)mc.getMyField()).size();
		
		Class o=mc.getMyRMap().getClass();
		
		Field[] fields = o.getDeclaredFields();
	    for (int z = 0; z < fields.length; z++)
	    {
	    	fields[z].setAccessible(true);
	    	try
	    	{
	    		String name=fields[z].getName();
	    		Method method = o.getMethod("get"+name, null);
	    		Object returnValue = method.invoke(mc.getMyRMap(), null);
	    		if(returnValue!=null)
	    		{
	    			Region<Integer,Double2D> region=((Region<Integer,Double2D>)returnValue);
		   			if(name.contains("mine"))
		   			{
		   				counter = counter + region.size();
		   			}
	    		}
	    	}
	    	catch (IllegalArgumentException e){e.printStackTrace();} 
	    	catch (IllegalAccessException e) {e.printStackTrace();} 
	    	catch (SecurityException e) {e.printStackTrace();} 
	    	catch (NoSuchMethodException e) {e.printStackTrace();} 
	    	catch (InvocationTargetException e) {e.printStackTrace();}
	    }
		
		return counter;
	}

	/**
	 * Clear all Regions.
	 * @return true if the clearing is successful, false if exception is generated
	 */
	private boolean reset()
	{
		for(Integer pos : listGrid.keySet())
		{	
			HashMap<CellType, MyCellInterface> hm = listGrid.get(pos);

			for(CellType ct : hm.keySet())
			{	
				MyCellDoubleField md = (MyCellDoubleField) hm.get(ct);


				md.getMyField().clear();	

				Class o=md.getMyRMap().getClass();

				Field[] fields = o.getDeclaredFields();
				for (int z = 0; z < fields.length; z++)
				{
					fields[z].setAccessible(true);
					try
					{
						String name=fields[z].getName();
						Method method = o.getMethod("get"+name, null);
						Object returnValue = method.invoke(md.getMyRMap(), null);
						if(returnValue!=null)
						{
							Region<Double, Double2D> region=((Region<Double, Double2D>)returnValue);
							region.clear();    
						}
					}
					catch (IllegalArgumentException e){e.printStackTrace(); return false;} 
					catch (IllegalAccessException e) {e.printStackTrace();return false;} 
					catch (SecurityException e) {e.printStackTrace();return false;} 
					catch (NoSuchMethodException e) {e.printStackTrace();return false;} 
					catch (InvocationTargetException e) {e.printStackTrace();return false;}
				}
			}
		}
		return true;
	}

	//used in DFLOCKERS
	public boolean setPortrayalForObject(Object o){
		if(p!=null)	{
			((DistributedState<Double2D>)sm).setPortrayalForObject(o);
			return true;
		}

		return false;
	}

	/**
	 * Implemented method from the abstract class.
	 */
	public DistributedState getState() { return (DistributedState)sm; }

	//getters and setters
	public double getOwn_x() { return own_x; }

	public void setOwn_x(double own_x) { this.own_x = own_x; }

	public double getOwn_y() {	return own_y; }

	public void setOwn_y(double own_y) { this.own_y = own_y; }

	@Override
	public ArrayList<MessageListener> getLocalListener() {
		return listeners;
	}

	@Override
	public void setTable(HashMap table) {
		connection.setTable(table);
	}

	@Override
	public String getID() {
		// TODO Auto-generated method stub
		return NAME;
	}

	@Override
	public void setConnection(Connection con) {
		// TODO Auto-generated method stub
		connection=(ConnectionNFieldsWithActiveMQAPI)con;
	}

	@Override
	public UpdateMap getUpdates() {
		// TODO Auto-generated method stub
		return updates;
	}

	@Override
	public void setIsSplitted(boolean isSplitted) {
		// TODO Auto-generated method stub
		if(isSplitted)
			isUnited = false;
		this.isSplitted = isSplitted;
		this.splitDone = isSplitted;
	}

	public void prepareForBalance(boolean setForBalance){

		this.prepareForBalance = setForBalance;
	}

	public boolean isSetForBalance() {
		return prepareForBalance;
	}

	@Override
	public HashMap<Integer, MyCellInterface> getToSendForBalance() {
		// TODO Auto-generated method stub
		return toSendForBalance;
	}

	@Override
	public HashMap<Integer, MyCellInterface> getToSendForUnion() {

		return toSendForUnion;
	}

	@Override
	public void prepareForUnion(boolean prepareForUnion) {
		this.prepareForUnion = prepareForUnion; 

	}

	@Override
	public boolean isSplitted() {
		// TODO Auto-generated method stub
		return isSplitted;
	}

	@Override
	public boolean isUnited() {
		// TODO Auto-generated method stub
		return isUnited;
	}

	@Override
	public boolean isPrepareForBalance() {
		// TODO Auto-generated method stub
		return prepareForBalance;
	}

	@Override
	public boolean setDistributedObjectLocationForPeer(Double2D location,
			RemoteAgent<Double2D> rm, SimState sm) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getNumAgents() {
		return numAgents;
	}

	@Override
	public void resetNumAgents() {
		numAgents=0;
		
	}
}
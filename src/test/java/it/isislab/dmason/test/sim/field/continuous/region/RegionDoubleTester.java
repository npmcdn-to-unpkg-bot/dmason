package it.isislab.dmason.test.sim.field.continuous.region;

import static org.junit.Assert.*;
import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import sim.util.Double2D;
import it.isislab.dmason.exception.DMasonException;
import it.isislab.dmason.sim.engine.RemotePositionedAgent;
import it.isislab.dmason.sim.field.continuous.region.RegionDouble;
import it.isislab.dmason.sim.field.support.field2D.EntryAgent;
import it.isislab.dmason.test.sim.app.DFlockers.DFlocker;

// TODO: Auto-generated Javadoc
/**
 * Test the Class RegionDouble.
 * 
 * @author Michele Carillo
 * @author Flavio Serrapica
 * @author Carmine Spagnuolo
 * @author Mario Capuozzo
 */
public class RegionDoubleTester {

	/** The rd. */
	RegionDouble rd;
	double width,height;

	/** The loop test. */
	int loopTest;

	/**
	 * Sets the up.
	 *
	 * @throws Exception
	 *             the exception
	 */
	@Before
	public void setUp() throws Exception {
		width=100;
		height=100;
		rd = new RegionDouble(0.0, 0.0, 100.0, 100.0, width,height);
		loopTest = 10000;
	}

	// isMine
	// verify if an entry is located in my region

	/**
	 * Test is mine0_0.
	 */
	@Test
	public void testIsMine0_0() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertTrue(rd.isMine(0.0, 0.0));
	}

	/**
	 * Test is mine0_1.
	 */
	@Test
	public void testIsMine0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertTrue(rd.isMine(0.0, 1.0));
	}

	/**
	 * Test is mine1_0.
	 */
	@Test
	public void testIsMine1_0() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertTrue(rd.isMine(1.0, 0.0));
	}

	/**
	 * Test is mine range0_1_0_1.
	 */
	@Test
	public void testIsMineRange0_1_0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		double step = 1.0 / loopTest;
		double i = 0.0;
		while (i < 1) {
			double j = 0.0;
			while (j < 1) {
				assertTrue(rd.isMine(i, j));
				j += step;
			}
			i += step;
		}

	}

	/**
	 * Test is mine negative range1_0_0_1.
	 */
	@Test
	public void testIsMineNegativeRange1_0_0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		double step = 1.0 / loopTest;
		double i = -1.0;
		while (i < 0) {
			double j = 0.0;
			while (j < 1) {
				assertFalse(rd.isMine(i, j));
				j += step;
			}
			i += step;
		}

	}


	/**
	 * Test add agents null.
	 */
	@Test
	public void testAddAgentsNull() {
		
		EntryAgent<Double2D> e = null;
		assertFalse(rd.addAgents(e));

	}

	/**
	 * Test add agents.
	 */
	@Test
	public void testAddAgents() {
		RemotePositionedAgent<Double2D> c = null;
		Double2D f = null;
		EntryAgent<Double2D> e = new EntryAgent<Double2D>(c, f);
		assertFalse(rd.addAgents(e));
	}

	/**
	 * Test add agents verify.
	 */
	@Test
	public void testAddAgentsVerify(){
		RemotePositionedAgent<Double2D> c = new DFlocker();
		Double2D f = new Double2D(0, 0);
		EntryAgent<Double2D> e = new EntryAgent<Double2D>(c, f);
		rd.addAgents(e);
		assertEquals(e, rd.get(c.getId()));
	}

	// clone
	/**
	 * Test clone.
	 */
	@Test
	public void testClone() {

		RegionDouble clone = (RegionDouble) rd.clone();

		assertEquals(rd, clone);
	}

	/**
	 * Test clone with entry.
	 */
	@Test
	public void testCloneWithEntry() {
		rd.clear();
		RemotePositionedAgent<Double2D> c = new DFlocker();
		Double2D f = new Double2D();
		EntryAgent<Double2D> e = new EntryAgent<Double2D>(c, f);
		rd.addAgents(e);
		RegionDouble clone = null;
		try {
			clone = (RegionDouble) rd.clone(); // per poter funzionare bisogna implementare il metodo equals 
											   // alla classe Region ed Entry -> implica che ogni RemotePositionedAgent deve implementare il metodo equals

		} catch (NullPointerException err) {
			fail("clone fail");
		}

		//assertEquals("incorrect copy of entry", e, clone);
		assertEquals("incorrect copy of entry", rd, clone);
	}
	
	/**
	 * Test clone with entry.
	 */
	@Test
	public void testCloneWithMultipleEntries() {
		Double2D f = new Double2D(10.0,22.0);
		RemotePositionedAgent<Double2D> c = new DFlocker();
		c.setId("fake");
		EntryAgent<Double2D> e = new EntryAgent<Double2D>(c, f);
		rd.addAgents(e);
		f= new Double2D(10.0,11.0);
		e = new EntryAgent<Double2D>(c, f);
		rd.addAgents(e);
		RegionDouble clone = null;
		try {
			clone = (RegionDouble) rd.clone(); // per poter funzionare bisogna implementare il metodo equals 
											   // alle classi Region ed Entry -> implica che ogni RemotePositionedAgent deve implementare il metodo equals

		} catch (NullPointerException err) {
			fail("clone fail");
		}

		//assertEquals("incorrect copy of entry", e, clone);
		assertEquals("incorrect copy of entry", rd, clone);
	}
}

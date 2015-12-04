/*package it.isislab.dmason.test.sim.field.grid.region;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import it.isislab.dmason.sim.engine.RemotePositionedAgent;
import it.isislab.dmason.sim.field.grid.region.RegionIntegerLB;
import it.isislab.dmason.sim.field.support.field2D.EntryAgent;
import it.isislab.dmason.test.sim.app.DParticles.DParticle;
import org.junit.Before;
import org.junit.Test;
import sim.util.Int2D;
*//**
* The Class RegionIntegerLBTester. Tests the RegionIntegerLB.
 * @author Michele Carillo
 * @author Flavio Serrapica
 * @author Carmine Spagnuolo
 * @author Mario Capuozzo
 *//*
public class RegionIntegerLBTester {
	*//** The rd. *//*
	RegionIntegerLB rd;

	*//** The loop test. *//*
	int loopTest;

	*//**
	 * Sets the up.
	 *
	 * @throws Exception
	 *             the exception
	 *//*
	@Before
	public void setUp() throws Exception {
		rd = new RegionIntegerLB(0, 0,1,1, 1, 1);
		loopTest = 10000;
	}

	// isMine
	// verify if an entry is located in my region

	*//**
	 * Test is mine0_0.
	 *//*
	@Test
	public void testIsMine0_0() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertTrue(rd.isMine(0, 0));
	}

	*//**
	 * Test is mine0_1.
	 *//*
	@Test
	public void testIsMine0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertFalse(rd.isMine(0, 1));
	}

	*//**
	 * Test is mine1_0.
	 *//*
	@Test
	public void testIsMine1_0() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		assertFalse(rd.isMine(1, 0));
	}

	
	*//**
	 * Test is mine range0_1_0_1.
	 *//*
	@Test
	public void testIsMineRange0_1_0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		int step = 1 / loopTest;
		int i = 0;
		while (i < 1) {
			int j = 0;
			while (j < 1) {
				assertTrue(rd.isMine(i, j));
				j += step;
			}
			i += step;
		}

	}

	*//**
	 * Test is mine negative range1_0_0_1.
	 *//*
	@Test
	public void testIsMineNegativeRange1_0_0_1() {
		// (x>=0) && (y >= 0) && (x <1 ) && (y<1 );
		int step = 1 / loopTest;
		int i = -1;
		while (i < 0) {
			int j = 0;
			while (j < 1) {
				assertFalse(rd.isMine(i, j));
				j += step;
			}
			i += step;
		}

	}
	

	// createRegion
	*//**
	 * Test create region xx under0.
	 *//*
	@Test
	public void testCreateRegionXXUnder0() {

		assertNull(rd.createRegion(-1, 0, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region yy under0.
	 *//*
	@Test
	public void testCreateRegionYYUnder0() {

		assertNull(rd.createRegion(0, -1, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region xx over0.
	 *//*
	@Test
	public void testCreateRegionXXOver0() {

		assertNotNull(rd.createRegion(1, 0, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region yy over0.
	 *//*
	@Test
	public void testCreateRegionYYOver0() {

		assertNotNull(rd.createRegion(0, 1, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region xxw over w.
	 *//*
	@Test
	public void testCreateRegionXxwOverW() {

		assertNull(rd.createRegion(11, 0, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region xxw equal w.
	 *//*
	@Test
	public void testCreateRegionXxwEqualW() {

		assertNull(rd.createRegion(10, 0, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region yyh over h.
	 *//*
	@Test
	public void testCreateRegionYyhOverH() {

		assertNull(rd.createRegion(0, 11, 0, 0, 0, 0, 10, 10));

	}

	*//**
	 * Test create region yyh equal h.
	 *//*
	@Test
	public void testCreateRegionYyhEqualH() {

		assertNull(rd.createRegion(0, 10, 0, 0, 0, 0, 10, 10));

	}

	

	// addAgents

	*//**
	 * Test add agents null.
	 *//*
	@Test
	public void testAddAgentsNull() {
		
		 * BUG FIND mi fa inserire un entry null
		 
		EntryAgent<Int2D> e = null;
		assertFalse(rd.addAgents(e));
	}

	*//**
	 * Test add agents.
	 *//*
	@Test
	public void testAddAgents() {
		RemotePositionedAgent<Int2D> c = null;
		Int2D f = null;
		
		 * BUG FIND mi fa inserire un entry con valori null
		 
		EntryAgent<Int2D> e = new EntryAgent<Int2D>(c, f);
		
		assertTrue(rd.addAgents(e));
	}

	*//**
	 * Test add agents verify.
	 *//*
	@Test
	public void testAddAgentsVerify() {
		RemotePositionedAgent<Int2D> c = null;
		Int2D f = null;
		EntryAgent<Int2D> e = new EntryAgent<Int2D>(c, f);
		rd.addAgents(e);
		assertEquals(e, rd.get(e.r.getId()));
	}

	// clone
	*//**
	 * Test clone.
	 *//*
	@Test
	public void testClone() {

		RegionIntegerLB clone = (RegionIntegerLB) rd.clone();

		assertEquals(rd, clone);
	}

	*//**
	 * Test clone with entry.
	 *//*
	@Test
	public void testCloneWithEntry() {
		rd.clear();
		RemotePositionedAgent<Int2D> c = new DParticle();
		Int2D f = new Int2D();
		EntryAgent<Int2D> e = new EntryAgent<Int2D>(c, f);
		rd.addAgents(e);
		RegionIntegerLB clone = null;
		try {
			clone = (RegionIntegerLB) rd.clone();

		} catch (NullPointerException err) {
			fail("clone fail");
		}
		assertEquals("incorrect copy of entry", rd, clone);
	}
}
*/
package org.freeplane.plugin.graph.layout.graphstream;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Aggregates the initial-spacing unit tests.
 * <p>
 * {@link InitialSpacingAcceptanceShould} is deliberately absent: it is an acceptance driver that
 * spawns a python3 subprocess for several hours, not a unit test. It runs only from the
 * {@code graphAcceptanceDrivers} task, which is the single place its headless interlock is meant
 * to be satisfied.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({InitialSpacingMetricsShould.class, InitialSpacingSeedShould.class, InitialSpacingLifecycleShould.class})
public class InitialSpacingShould { }

package org.freeplane.core.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

public class ConfigurationUtilsTest {

	@Test
	public void encodeListValueShouldUseSingleSeparatorForNonStrictLists() {
		assertEquals("ID_A" + File.pathSeparator + "ID_B",
		        ConfigurationUtils.encodeListValue(Arrays.asList("ID_A", "ID_B"), false));
	}

	@Test
	public void decodeListValueShouldRoundTripNonStrictEncodedLists() {
		assertEquals(Arrays.asList("ID_A", "ID_B"),
		        ConfigurationUtils.decodeListValue(
		                ConfigurationUtils.encodeListValue(Arrays.asList("ID_A", "ID_B"), false), false));
	}

	@Test
	public void decodeListValueShouldTreatRepeatedNonStrictSeparatorsAsOneSeparator() {
		assertEquals(Arrays.asList("ID_A", "ID_B"),
		        ConfigurationUtils.decodeListValue("ID_A" + File.pathSeparator + File.pathSeparator + "ID_B", false));
	}
}

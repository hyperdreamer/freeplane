/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2026 Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.freeplane.core.util.LogUtils;
import org.junit.Test;

public class ComboPropertyTest {
    @Test
    public void nullValueSelectsFirstChoiceWithoutLoggingAnError() {
        final String propertyName = "combo.without.registered.default";
        final ComboProperty property = new ComboProperty(propertyName,
                Arrays.asList("first", "second"), Arrays.asList("First", "Second"));
        property.setValue("second");
        final AtomicBoolean severeErrorLogged = new AtomicBoolean();
        final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (Level.SEVERE.equals(record.getLevel()) && record.getMessage().contains(propertyName)) {
                    severeErrorLogged.set(true);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        final Logger logger = LogUtils.getLogger();
        logger.addHandler(handler);
        try {
            property.setValue(null);
        }
        finally {
            logger.removeHandler(handler);
        }

        assertThat(property.getValue()).isEqualTo("first");
        assertThat(severeErrorLogged).isFalse();
    }
}

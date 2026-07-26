package org.freeplane.view.swing.map;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.features.ui.IMapViewChangeListener;
import org.junit.Test;

public class MapViewChangeObserverCompoundTest {

	@Test
	public void notYetShowingMapViewStillDeliversAfterViewDisplayedToListenerAddedBeforeItIsShown() {
		MapViewChangeObserverCompound subject = new MapViewChangeObserverCompound();
		MapView previousView = mock(MapView.class);
		MapView createdView = mock(MapView.class);
		IMapViewChangeListener listener = mock(IMapViewChangeListener.class);
		AtomicBoolean showing = new AtomicBoolean(false);
		AtomicReference<HierarchyListener> hierarchyListener = new AtomicReference<>();

		when(createdView.isShowing()).thenAnswer(invocation -> showing.get());
		when(createdView.isLayoutCompleted()).thenReturn(true);
		org.mockito.Mockito.doAnswer(invocation -> {
			hierarchyListener.set(invocation.getArgument(0));
			return null;
		}).when(createdView).addHierarchyListener(any(HierarchyListener.class));

		subject.mapViewCreated(previousView, createdView);
		assertNotNull(hierarchyListener.get());

		subject.addListener(listener);
		showing.set(true);
		hierarchyListener.get().hierarchyChanged(mock(HierarchyEvent.class));

		verify(listener).afterViewDisplayed(previousView, createdView);
	}
}

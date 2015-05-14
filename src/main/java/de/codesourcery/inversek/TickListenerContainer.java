package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.List;


public class TickListenerContainer implements ITickListener
{
	private List<ITickListener> listeners = new ArrayList<>();
	private final List<ITickListener> toRemove = new ArrayList<>();
	
	public void add(ITickListener l) 
	{
		if (l == null) {
			throw new IllegalArgumentException("l must not be NULL");
		}
		synchronized (listeners) {
			listeners.add( l );
		}
	}
	
	public boolean isEmpty() 
	{
		synchronized (listeners) {
			return listeners.isEmpty();
		}
	}
	
	public void remove(ITickListener l ) {
		synchronized(listeners) {
			listeners.remove( l );
		}
	}
	
	public boolean tick(float deltaSeconds) {
		
		final List<ITickListener> copy;
		synchronized(listeners) {
			copy = new ArrayList<>(listeners);
		}

		for ( ITickListener l : copy ) 
		{
			if ( ! l.tick( deltaSeconds ) ) {
				toRemove.add( l );
			}
		}
		
		synchronized(listeners) 
		{
			if ( ! toRemove.isEmpty() ) 
			{
				this.listeners.removeAll(toRemove);
				toRemove.clear();
			}
		}
		return true;
	}
}

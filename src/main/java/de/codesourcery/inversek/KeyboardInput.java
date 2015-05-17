package de.codesourcery.inversek;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class KeyboardInput extends KeyAdapter {

	private final Set<Integer> pressed = new HashSet<>();
	
	public void attach(Component c) {
		c.addKeyListener( this );
	}
	
	@Override
	public void keyPressed(KeyEvent e) 
	{
		synchronized( pressed ) {
			pressed.add( e.getKeyCode() );
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		synchronized( pressed ) {
			pressed.remove( e.getKeyCode() );
		}
	}
	
	public boolean isIncAnglePressed() 
	{
		synchronized( pressed ) {
			return getAndClear( KeyEvent.VK_PLUS);
		}
	}
	
	public boolean isDecAnglePressed() 
	{
		synchronized( pressed ) {
			return getAndClear( KeyEvent.VK_MINUS );
		}
	}	
	
	public boolean isOpenGripper() 
	{
		synchronized( pressed ) {
			return getAndClear( KeyEvent.VK_O );
		}
	}
	
	public boolean isEmergencyStop() 
	{
		return getAndClear( KeyEvent.VK_SPACE );
	}
	
	private boolean getAndClear(int keyCode) 
	{
		synchronized( pressed ) {
			final boolean result = pressed.contains( keyCode );
			pressed.remove( keyCode );
			return result;
		}
	}
	
	public boolean isCloseGripper() {
		return getAndClear( KeyEvent.VK_C );
	}	
}

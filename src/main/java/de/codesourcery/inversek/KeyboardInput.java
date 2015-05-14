package de.codesourcery.inversek;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public final class KeyboardInput extends KeyAdapter 
{
	private final Set<Integer> pressed = new HashSet<>();
	
	public void attach(Component peer) {
		peer.addKeyListener( this );
	}
	
	public void keyReleased(java.awt.event.KeyEvent e) 
	{
		pressed.remove( e.getKeyCode() );
	}
	
	public boolean isPressed(int key) {
		return pressed.contains( key );
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		pressed.add( e.getKeyCode() );
	}
}

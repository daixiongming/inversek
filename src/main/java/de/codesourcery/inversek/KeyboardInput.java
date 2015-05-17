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
		pressed.add( e.getKeyCode() );
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		pressed.remove( e.getKeyCode() );
	}
	
	public boolean isIncAnglePressed() {
		return pressed.contains( KeyEvent.VK_PLUS);
	}
	
	public boolean isDecAnglePressed() {
		return pressed.contains( KeyEvent.VK_MINUS );
	}	
}

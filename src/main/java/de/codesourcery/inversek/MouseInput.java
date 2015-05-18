package de.codesourcery.inversek;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MouseInput
{
	public static enum Button { LEFT,RIGHT,MIDDLE }

	private final Object LOCK = new Object();

	public static final class State
	{
		public int mouseX;
		public int mouseY;
		public Button button;
		public boolean mouseMoved;

		public State() {
		}

		public void set(int mouseX, int mouseY, Button button, boolean mouseMoved) {
			this.mouseX = mouseX;
			this.mouseY = mouseY;
			this.button = button;
			this.mouseMoved = mouseMoved;
		}
	}

	private int currentX;
	private int currentY;
	private Button button;
	private boolean mouseMoved = true;

	private final MouseAdapter adapter = new MouseAdapter()
	{
		@Override
		public void mouseMoved(MouseEvent e)
		{
			setCurrentPosition( e.getPoint() );
		}

		@Override
		public void mouseClicked(java.awt.event.MouseEvent e)
		{
			final Button b;
			switch( e.getButton() )
			{
				case MouseEvent.BUTTON1: b = Button.LEFT; break;
				case MouseEvent.BUTTON2: b = Button.MIDDLE; break;
				case MouseEvent.BUTTON3: b = Button.RIGHT; break;
				default:
					return;
			}
			buttonClicked(b,e.getPoint());
		}
	};

	public void attach(Component c)
	{
		c.addMouseListener( adapter );
		c.addMouseMotionListener( adapter );
	}

	public void setCurrentPosition(Point p)
	{
		synchronized(LOCK)
		{
			this.mouseMoved |= this.currentX != p.x || this.currentY != p.y;
			this.currentX = p.x;
			this.currentY = p.y;
		}
	}

	public void buttonClicked(Button button,Point p)
	{
		if ( button == null ) {
			throw new IllegalArgumentException("button must not be NULL");
		}
		synchronized(LOCK)
		{
			this.button = button;
			setCurrentPosition(p);
		}
	}

	public void getState(State state)
	{
		synchronized(LOCK)
		{
			state.set( currentX , currentY, button, mouseMoved);
		}
	}

	public void clearInput()
	{
		synchronized(LOCK) {
			this.button = null;
			this.mouseMoved = false;
		}
	}
}
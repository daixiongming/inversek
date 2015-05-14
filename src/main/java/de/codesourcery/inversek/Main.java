package de.codesourcery.inversek;

import java.awt.Dimension;

import javax.swing.JFrame;

import com.badlogic.gdx.math.Vector2;

public class Main 
{
	public static final boolean DEBUG = false;
	
	public static void main(String[] args) 
	{
		new Main().run();
	}

	private final RobotArm robotArm;
	
	private final MyPanel panel;
	private final TickListenerContainer listenerContainer = new TickListenerContainer();

	public Main() 
	{
		robotArm = new RobotArm();
		panel = new MyPanel( robotArm.getModel() );
		
		listenerContainer.add( robotArm );
	}

	public void run() {

		panel.setPreferredSize( new Dimension(640,480 ) );

		final JFrame frame = new JFrame("test");
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		frame.getContentPane().add( panel );
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		// main loop

		long previous = System.currentTimeMillis();
		while ( true ) 
		{
			long now = System.currentTimeMillis();
			float deltaSeconds = (now - previous)/1000f;
			previous = now;

			if ( panel.desiredPositionChanged ) 
			{
				final Vector2 target = panel.viewToModel( panel.desiredPosition );
				if ( robotArm.moveArm( target ) ) {
					System.out.println("Desired target => view: "+panel.desiredPosition+" (world: "+target+")");					
				} else {
					System.err.println("Arm refused to move to "+target);
				}
				panel.desiredPositionChanged = false;				
			}

			listenerContainer.tick( deltaSeconds );
			
			panel.tick( deltaSeconds );

			try {
				Thread.sleep( 15 );
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
package de.codesourcery.inversek;

import java.awt.Dimension;

import javax.swing.JFrame;

import com.badlogic.gdx.math.Vector2;

public class Main 
{
	public static final boolean DEBUG = false;
	public static final int DESIRED_FPS = 70;
	
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

		final JFrame frame = new JFrame("Right-click to set motion target, left-click on bone/joint to show details");
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		frame.getContentPane().add( panel );
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		// main loop

		long previous = System.currentTimeMillis();
		float sumSeconds=0;
		while ( true ) 
		{
			long now = System.currentTimeMillis();
			float deltaSeconds = (now - previous)/1000f;
			sumSeconds += deltaSeconds;
			previous = now;

			if ( panel.desiredPositionChanged ) 
			{
				robotArm.moveArm( panel.viewToModel( panel.desiredPosition ) );
				panel.desiredPositionChanged = false;				
			}

			listenerContainer.tick( deltaSeconds );
			
			if ( sumSeconds >= 1.0f/DESIRED_FPS ) 
			{
				panel.tick( sumSeconds );
				sumSeconds = 0;
			}
		}
	}
}
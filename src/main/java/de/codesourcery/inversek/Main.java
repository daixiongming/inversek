package de.codesourcery.inversek;

import java.awt.Dimension;
import java.awt.Point;

import javax.swing.JFrame;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;

import de.codesourcery.inversek.ISolver.ICompletionCallback;
import de.codesourcery.inversek.ISolver.Outcome;

public class Main implements IMathSupport
{
	public static final boolean DEBUG = false;
	public static final int DESIRED_FPS = 70;
	
	public static void main(String[] args) 
	{
		new Main().run();
	}

	private final WorldModel worldModel;
	private final RobotArm robotArm;
	
	private final KeyboardInput keyboardInput = new KeyboardInput();
	private final MyPanel panel;
	private final TickListenerContainer listenerContainer = new TickListenerContainer();

	public Main() 
	{
		Box2D.init();
		
		worldModel = new WorldModel();
		
		robotArm = new RobotArm( worldModel );
		
		panel = new MyPanel( robotArm , worldModel );
		keyboardInput.attach( panel );
		
		listenerContainer.add( robotArm );
		listenerContainer.add( worldModel );
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
		
		System.out.println("Chains: "+robotArm.getModel().getChains().size());
		while ( true ) 
		{
			final long now = System.currentTimeMillis();
			final float deltaSeconds = (now - previous)/1000f;
			sumSeconds += deltaSeconds;
			previous = now;

			if ( panel.addBallAt != null ) 
			{
				Vector2 modelCoords = panel.viewToModel( panel.addBallAt );
				if ( modelCoords.y > 0 ) {
					worldModel.addBall( modelCoords.x , modelCoords.y );
				}
				panel.addBallAt = null;
			}
			
			if ( panel.desiredPositionChanged ) 
			{
				final Point p = panel.desiredPosition;
				if ( p != null ) {
					final Vector2 worldCoords = panel.viewToModel( p );
					
					final ICompletionCallback cb = new ICompletionCallback() 
					{
						private boolean called = false;
						
						@Override
						public void complete(ISolver solver, Outcome outcome) 
						{
							if ( called ) {
								throw new IllegalStateException("Called more than once?");
							}
							called = true;
							if ( outcome == Outcome.SUCCESS ) {
								panel.setDebugRender( solver.getChain() );
							} else if ( outcome == Outcome.FAILURE ) {
								panel.setDebugRender( null );
							} else {
								throw new IllegalStateException("Unexpected outcome: "+outcome);
							}
						}
					};
					if ( robotArm.moveArm( worldCoords , cb ) ) 
					{
						System.err.println("Arm moving to "+p+" (world: "+worldCoords+")");						
					} else {
						System.err.println("Arm has not finished moving yet");
					}
				}
				panel.desiredPositionChanged = false;				
			} 
			else 
			{
				Node<?> selection = panel.selectedNode;
				if ( selection != null && selection instanceof Joint) 
				{
					final float factor = keyboardInput.isIncAnglePressed() ? 1 : keyboardInput.isDecAnglePressed() ? -1 : 0;
					if ( factor != 0 ) {
						final Joint j = (Joint) selection;
						float angleInDeg = j.getBox2dOrientationDegrees();
						float newAngleInDeg = angleInDeg + factor * 2;
						if ( robotArm.moveJoint( j , newAngleInDeg ) ) {
							System.out.println("Moving "+j+" from "+angleInDeg+" to "+newAngleInDeg);
						} else {
							System.err.println("Failed to move joint");
						}
					}
				}
			}
			
			listenerContainer.tick( deltaSeconds );
			
			robotArm.getModel().getChains().forEach( chain -> chain.syncWithBox2d() );
			
			if ( sumSeconds >= 1.0f/DESIRED_FPS ) 
			{
				panel.tick( sumSeconds );
				sumSeconds = 0;
			}
		}
	}
}